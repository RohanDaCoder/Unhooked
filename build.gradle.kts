// Unhooked APK pipeline (replaces build.sh).
//
//   ./gradlew buildApk -Papk=<instagram.apk|.apkm|.xapk|.apks> [-Pclone] [-Pinstall] [-Pdebug]
//   ./gradlew installApk -Papk=<...> -Pclone -Pinstall   (same, plus adb install -r)
// (-Papk is optional when INPUT_APK is set in .env; the flag wins.)
//
// Stages: fetch base (optional) -> merge splits (APKEditor) -> build patch bundle (:patches:build) ->
// apply patches (Morphe CLI) -> sign (apksigner, release keystore only) ->
// install (adb, -Pinstall only). Output: ./unhooked.apk, receipt: build/patch-report.json.
//
// Notes vs the old script:
// - Presence of -Pclone/-Pdebug/-Pinstall means true (no values needed).
// - Release signing reads UNHOOKED_KEYSTORE[/_PASS/_ALIAS] from the environment
//   or a local .env file (see .env.example; environment wins). Without
//   UNHOOKED_KEYSTORE_PASS the patcher signs with a throwaway key (testing only).
// - The JVM running Gradle is used for every Java tool (JDK 21 required, as for
//   the rest of the build); no JAVA_HOME pinning, no Git Bash, no python3.

import java.util.Properties

val apkProp = providers.gradleProperty("apk")
val cloneEnabled = providers.gradleProperty("clone").isPresent
val debugEnabled = providers.gradleProperty("debug").isPresent
val installEnabled = providers.gradleProperty("install").isPresent

// .env support: KEY=VALUE lines, '#' comments, optional single/double quotes.
// The file is optional and never committed (see .gitignore); real environment
// variables take precedence over .env entries.
val dotEnv: Map<String, String> by lazy {
    val file = rootDir.resolve(".env")
    if (!file.isFile) return@lazy emptyMap()
    file.readLines().mapNotNull { line ->
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#") || "=" !in trimmed) {
            return@mapNotNull null
        }
        val (key, value) = trimmed.split("=", limit = 2)
        key.trim() to value.trim().removeSurrounding("\"").removeSurrounding("'")
    }.toMap()
}

fun envOrDotEnv(name: String): String? =
    System.getenv(name)?.takeIf { it.isNotEmpty() } ?: dotEnv[name]?.takeIf { it.isNotEmpty() }

// Base APK location: -Papk wins, INPUT_APK (env or .env) is the fallback.
val apkPath: Provider<String> =
    apkProp.orElse(providers.provider { envOrDotEnv("INPUT_APK") ?: "" })

val keystorePass = (envOrDotEnv("UNHOOKED_KEYSTORE_PASS") ?: "").trim()

fun usage(msg: String): Nothing = throw GradleException(
    "$msg\nUsage: ./gradlew buildApk -Papk=<instagram.apk|.apkm|.xapk|.apks> [-Pclone] [-Pinstall] [-Pdebug] (or set INPUT_APK in .env)"
)

fun apkFile(): File {
    val raw = apkPath.orNull?.takeIf { it.isNotEmpty() }
        ?: usage("Missing input APK (-Papk=<file> or INPUT_APK in .env).")
    return rootDir.resolve(raw).takeIf { it.isFile } ?: usage("Input APK not found: $raw")
}

fun isSplitBundle(f: File) = f.extension.lowercase() in setOf("apkm", "xapk", "apks")

fun mergedApkFile(): File =
    layout.buildDirectory.file("merged/${apkFile().nameWithoutExtension}.apk").get().asFile

fun newestJar(dirName: String, vararg prefixes: String): File? =
    rootDir.resolve(dirName)
        .listFiles { f -> f.extension == "jar" && prefixes.any { f.name.startsWith(it) } }
        ?.maxByOrNull { it.lastModified() }

fun jarMainClass(jar: File): String =
    java.util.jar.JarFile(jar).use {
        it.manifest.mainAttributes.getValue("Main-Class")
            ?: throw GradleException("No Main-Class manifest entry in ${jar.name}")
    }

fun newestMpp(): File =
    project(":patches").layout.buildDirectory.dir("libs").get().asFile
        .listFiles { f ->
            f.name.matches(Regex("patches-[0-9].*\\.mpp")) &&
                "sources" !in f.name && "javadoc" !in f.name
        }
        ?.maxByOrNull { it.lastModified() }
        ?: throw GradleException("No .mpp under patches/build/libs/.")

fun sdkDir(): File {
    val props = Properties()
    rootDir.resolve("local.properties").takeIf { it.exists() }?.inputStream()?.use(props::load)
    val home = System.getProperty("user.home")
    val candidates = listOfNotNull(
        props.getProperty("sdk.dir"),
        System.getenv("ANDROID_HOME"),
        System.getenv("ANDROID_SDK_ROOT"),
        "$home/Android/Sdk",
        "$home/Library/Android/sdk",
    )
    return candidates.map(::File).firstOrNull { it.isDirectory }
        ?: throw GradleException("Android SDK not found (local.properties sdk.dir, ANDROID_HOME, ANDROID_SDK_ROOT).")
}

fun newestBuildTool(name: String): File {
    val dirs = File(sdkDir(), "build-tools").listFiles { f -> f.isDirectory }
        ?.sortedByDescending { it.name }
        ?: throw GradleException("No build-tools under ${sdkDir()}.")
    for (dir in dirs) {
        for (candidate in listOf(name, "$name.bat", "$name.exe")) {
            val tool = dir.resolve(candidate)
            if (tool.isFile) return tool
        }
    }
    throw GradleException("$name not found under ${sdkDir()}/build-tools.")
}

fun adb(): File {
    for (candidate in listOf("adb", "adb.exe")) {
        val tool = File(sdkDir(), "platform-tools/$candidate")
        if (tool.isFile) return tool
    }
    throw GradleException("adb not found under ${sdkDir()}/platform-tools.")
}

// Tool binaries (executables, not libraries): pinned here with SHA-256 so a
// fresh clone builds with zero manual downloads. Bump the version in
// gradle/libs.versions.toml and the hash below together — a mismatch fails
// loudly, never silently.
data class ToolJar(val fileName: String, val url: String, val sha256: String)

val toolJars = listOf(
    ToolJar(
        "APKEditor-${libs.versions.apkeditor.get()}.jar",
        "https://github.com/REAndroid/APKEditor/releases/download/V${libs.versions.apkeditor.get()}/APKEditor-${libs.versions.apkeditor.get()}.jar",
        "a9cd40df818845456be6d696de6110c89edf4b0a0580cb83438ed6b25a366e67",
    ),
    ToolJar(
        "morphe-desktop-${libs.versions.morphe.desktop.get()}-all.jar",
        "https://github.com/MorpheApp/morphe-desktop/releases/download/v${libs.versions.morphe.desktop.get()}/morphe-desktop-${libs.versions.morphe.desktop.get()}-all.jar",
        "8cf6a9eab4ee9dab146bddc24681897851564f53116baec11f36ba2fa2f589be",
    ),
)

fun sha256Hex(file: File): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(131072)
        var n: Int
        while (input.read(buffer).also { n = it } > 0) digest.update(buffer, 0, n)
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

// Stamps this build's Unhooked version (gradle.properties) into the runtime so
// the updater compares releases instead of Instagram's versionName. Generated
// into the source tree but never committed (see .gitignore); Morphe's android
// block exposes no buildConfigField, so generation is the portable path.
val generateUnhookedVersion = tasks.register("generateUnhookedVersion") {
    group = "unhooked"
    description = "Generates UnhookedVersion.java carrying project.version for the updater."
    inputs.property("version", project.version.toString())
    outputs.file("extensions/extension/src/main/java/com/unhooked/extension/UnhookedVersion.java")
    doLast {
        val file = rootDir.resolve("extensions/extension/src/main/java/com/unhooked/extension/UnhookedVersion.java")
        file.parentFile.mkdirs()
        file.writeText(
            """
            package com.unhooked.extension;

            // Generated by the generateUnhookedVersion Gradle task — do not edit or commit.
            final class UnhookedVersion {
                static final String NAME = "${project.version}";

                private UnhookedVersion() {}
            }
            """.trimIndent() + "\n"
        )
    }
}

project(":extensions:extension") {
    tasks.matching { it.name.startsWith("compile") }.configureEach {
        dependsOn(generateUnhookedVersion)
    }
}

tasks.register("setupTools") {
    group = "unhooked"
    description = "Downloads missing tool jars (APKEditor, Morphe CLI) into tools/ with SHA-256 verification."
    outputs.files(toolJars.map { rootDir.resolve("tools/${it.fileName}") })
    doLast {
        val dir = rootDir.resolve("tools").apply { mkdirs() }
        // Drop stale versions so newestJar() below can never pick them.
        dir.listFiles { f ->
            f.extension == "jar" &&
                (f.name.startsWith("APKEditor-") || f.name.startsWith("morphe-")) &&
                toolJars.none { it.fileName == f.name }
        }?.forEach { it.delete() }
        for (tool in toolJars) {
            val dest = dir.resolve(tool.fileName)
            if (dest.isFile && sha256Hex(dest).equals(tool.sha256, ignoreCase = true)) {
                println("    cached: ${tool.fileName}")
                continue
            }
            if (dest.exists()) dest.delete()
            println("    fetching: ${tool.url}")
            java.net.URI(tool.url).toURL().openStream().use { input ->
                dest.outputStream().use { input.copyTo(it) }
            }
            val actual = sha256Hex(dest)
            if (!actual.equals(tool.sha256, ignoreCase = true)) {
                dest.delete()
                throw GradleException(
                    "SHA-256 mismatch for ${tool.fileName}: expected ${tool.sha256}, got $actual. " +
                        "Update the pin in build.gradle.kts (or the version in gradle/libs.versions.toml)."
                )
            }
        }
    }
}

fun printPatchReport(report: File) {
    if (!report.isFile) return
    @Suppress("UNCHECKED_CAST")
    val parsed = groovy.json.JsonSlurper().parse(report) as Map<String, Any?>
    @Suppress("UNCHECKED_CAST")
    (parsed["appliedPatches"] as List<Map<String, Any?>>?).orEmpty()
        .forEach { println("    ok   ${it["name"]}") }
    @Suppress("UNCHECKED_CAST")
    (parsed["failedPatches"] as List<Map<String, Any?>>?).orEmpty()
        .forEach { println("    FAIL ${it["name"]}: ${it["exception"] ?: it["error"] ?: ""}") }
}

// Self-hosted stock base: same default as CI (INPUT_APK_URL). -PbaseVersion=<v>
// overrides the pin; the file is base-<major>.xapk next to the project.
val baseProjectId = "86891782"
val baseVersion = providers.gradleProperty("baseVersion").orElse("454.0.0.13.77")

val fetchBase = tasks.register("fetchBase") {
    group = "unhooked"
    description = "Downloads the pinned stock Instagram base from this project's Package Registry (-PbaseVersion=<v> to override)."
    inputs.property("baseVersion", baseVersion)
    outputs.file(baseVersion.map { "base-${it.substringBefore('.')}.apkm" })
    doLast {
        val v = baseVersion.get()
        val dest = rootDir.resolve("base-${v.substringBefore('.')}.apkm")
        if (dest.isFile) {
            println("    cached: ${dest.name} (${dest.length() / 1024 / 1024} MB)")
            return@doLast
        }
        val url =
            "https://gitlab.com/api/v4/projects/$baseProjectId/packages/generic/unhooked-base/$v/${dest.name}"
        println("    fetching: $url")
        java.net.URI(url).toURL().openStream().use { input ->
            dest.outputStream().use { input.copyTo(it) }
        }
        println("    saved: ${dest.name} (${dest.length() / 1024 / 1024} MB)")
    }
}

val mergeSplits = tasks.register<JavaExec>("mergeSplits") {
    group = "unhooked"
    description = "Merges a split bundle (.apkm/.xapk/.apks) into one APK with APKEditor."
    dependsOn("setupTools")
    inputs.property("apk", apkPath)
    inputs.files(fileTree("tools") { include("APKEditor-*.jar") })
    outputs.file(apkPath.map { "merged/${rootDir.resolve(it).nameWithoutExtension}.apk" })
    // Reuse the cached merge while it is newer than the bundle (Gradle checks this).
    outputs.file(layout.buildDirectory.file("merged/merge.log"))
    onlyIf("split bundle input") {
        val raw = apkPath.orNull?.takeIf { it.isNotEmpty() } ?: return@onlyIf false
        isSplitBundle(rootDir.resolve(raw))
    }
    doFirst {
        val input = apkFile()
        val editor = newestJar("tools", "APKEditor-")
            ?: throw GradleException(
                "Split bundle needs APKEditor: gh release download V1.4.9 " +
                    "-R REAndroid/APKEditor -p 'APKEditor-1.4.9.jar' -D tools/"
            )
        val merged = mergedApkFile()
        merged.parentFile.mkdirs()
        val log = layout.buildDirectory.file("merged/merge.log").get().asFile
        classpath(files(editor))
        mainClass.set(jarMainClass(editor))
        args("m", "-f", "-i", input.absolutePath, "-o", merged.absolutePath)
        maxHeapSize = "4g"
        // The per-file merge log is noisy; keep it on disk rather than inline.
        standardOutput = log.outputStream()
    }
    doLast {
        println("    merged: ${mergedApkFile()}")
    }
}

val patchApk = tasks.register<JavaExec>("patchApk") {
    group = "unhooked"
    description = "Builds the patch bundle and applies it to the input APK with the Morphe CLI."
    dependsOn(":patches:build", mergeSplits)
    inputs.files(fileTree("patches/build/libs") {
        include("patches-*[0-9].mpp")
        exclude("*-sources*", "*-javadoc*")
    })
    inputs.files(fileTree("tools") { include("morphe-*.jar") })
    inputs.property("apk", apkPath)
    inputs.property("clone", cloneEnabled)
    inputs.property("debug", debugEnabled)
    inputs.property("deferSigning", keystorePass.isNotEmpty())
    outputs.file(layout.buildDirectory.file("patch-report.json"))
    outputs.file(envOrDotEnv("UNHOOKED_OUTPUT_PATH") ?: "unhooked.apk")
    doFirst {
        val input = apkFile()
        val cli = newestJar("tools", "morphe-desktop-", "morphe-cli-")
            ?: throw GradleException("Morphe CLI not found under tools/ (morphe-cli-*.jar / morphe-desktop-*.jar).")
        val effective = if (isSplitBundle(input)) mergedApkFile() else input
        val report = layout.buildDirectory.file("patch-report.json").get().asFile
        val out = rootDir.resolve(envOrDotEnv("UNHOOKED_OUTPUT_PATH") ?: "unhooked.apk")
        classpath(files(cli))
        mainClass.set(jarMainClass(cli))
        val patchArgs = mutableListOf(
            "patch", "-p", newestMpp().absolutePath, "-f",
            "-r", report.absolutePath, "-o", out.absolutePath,
        )
        if (cloneEnabled) patchArgs.addAll(listOf("-e", "Clone"))
        if (debugEnabled) patchArgs.addAll(listOf("-e", "Debug bridge"))
        if (keystorePass.isNotEmpty()) patchArgs.add("--unsigned")
        patchArgs.add(effective.absolutePath)
        args(patchArgs)
    }
    doLast {
        printPatchReport(layout.buildDirectory.file("patch-report.json").get().asFile)
        if (debugEnabled) {
            logger.warn("!! debug build: settings are drivable over ADB broadcasts — do not release")
        }
    }
}

val signApk = tasks.register<Exec>("signApk") {
    group = "unhooked"
    description = "Signs unhooked.apk with the release keystore (skipped without UNHOOKED_KEYSTORE_PASS)."
    dependsOn(patchApk)
    inputs.file(envOrDotEnv("UNHOOKED_OUTPUT_PATH") ?: "unhooked.apk")
    inputs.property("keystore", envOrDotEnv("UNHOOKED_KEYSTORE") ?: "unhooked.keystore")
    inputs.property("alias", envOrDotEnv("UNHOOKED_KEY_ALIAS") ?: "unhooked")
    inputs.property("passwordSet", keystorePass.isNotEmpty())
    outputs.file(envOrDotEnv("UNHOOKED_OUTPUT_PATH") ?: "unhooked.apk")
    onlyIf("release keystore password set") { keystorePass.isNotEmpty() }
    doFirst {
        val keystore = envOrDotEnv("UNHOOKED_KEYSTORE")?.let(::File)
            ?: rootDir.resolve("unhooked.keystore")
        if (!keystore.isFile) throw GradleException("Keystore not found: $keystore")
        val alias = envOrDotEnv("UNHOOKED_KEY_ALIAS") ?: "unhooked"
        val keyPass = envOrDotEnv("UNHOOKED_KEY_PASS") ?: keystorePass
        val out = rootDir.resolve(envOrDotEnv("UNHOOKED_OUTPUT_PATH") ?: "unhooked.apk")
        // v1+v2+v3 so the signature is accepted across the whole user base's
        // devices; v4 (the .idsig sidecar) is only useful for adb incremental
        // install, so leave it off.
        commandLine(
            newestBuildTool("apksigner"), "sign",
            "--ks", keystore, "--ks-key-alias", alias,
            "--ks-pass", "pass:$keystorePass", "--key-pass", "pass:$keyPass",
            "--v1-signing-enabled", "true", "--v2-signing-enabled", "true",
            "--v3-signing-enabled", "true", "--v4-signing-enabled", "false",
            out,
        )
    }
    doLast {
        rootDir.resolve("unhooked.apk.idsig").takeIf { it.exists() }?.delete()
        // Print the release cert fingerprint, same receipt line the script had.
        try {
            val proc = ProcessBuilder(
                listOf(
                    newestBuildTool("apksigner").absolutePath, "verify", "--print-certs",
                    rootDir.resolve(envOrDotEnv("UNHOOKED_OUTPUT_PATH") ?: "unhooked.apk").absolutePath,
                )
            ).redirectErrorStream(true).start()
            val text = proc.inputStream.bufferedReader().readText()
            proc.waitFor()
            text.lines().firstOrNull { "SHA-256" in it.uppercase() }
                ?.let { println("    cert $it") }
        } catch (e: Exception) {
            logger.warn("apksigner verify skipped: ${e.message}")
        }
    }
}

tasks.register<Exec>("installApk") {
    group = "unhooked"
    description = "Installs unhooked.apk on the connected device (needs -Pinstall)."
    dependsOn(signApk)
    onlyIf("install flag") { installEnabled }
    doFirst {
        commandLine(adb(), "install", "-r", rootDir.resolve(envOrDotEnv("UNHOOKED_OUTPUT_PATH") ?: "unhooked.apk"))
    }
}

tasks.register("buildApk") {
    group = "unhooked"
    description = "Full pipeline: merge splits, patch, sign. Use installApk with -Pinstall to deploy."
    dependsOn(signApk)
}
