group = "com.unhooked"

patches {
    about {
        name = "Unhooked Patches"
        description = "Distraction-free Instagram: blocks the feed, stories, explore, reels, ads and suggestions, with a runtime toggle."
        source = "git@gitlab.com:RohanDaCoder/Unhooked.git"
        author = "RohanDaCoder"
        contact = "na"
        website = "na"
        license = "GPLv3"
    }
}

dependencies {
    // Provides app.morphe.util.* helpers (DOM, bytecode, references) used by the patches.
    implementation(libs.morphe.patches.library)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}
