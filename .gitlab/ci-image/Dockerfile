# Unhooked CI runner image — JDK 21 + Android SDK baked in.
#
# What CI used to install on every job (~2-3 min): apt packages, the Android
# CLI, and SDK components. All of that lives in this image now; the pipeline
# only needs gradlew + PATH exports.
#
# Rebuild + push (needs local Docker, once per pin change):
#   docker build -t registry.gitlab.com/rohandacoder/unhooked/ci:jdk21-sdk36.0.0-v1 .gitlab/ci-image
#   docker push registry.gitlab.com/rohandacoder/unhooked/ci:jdk21-sdk36.0.0-v1
#
# Tag rule: immutable, encodes the pins (jdk21-sdk36.0.0-vN). Never :latest —
# a floating tag reintroduces the drift this image removes. Bump -vN whenever
# SDK pins or the JDK change, and update .gitlab-ci.yml's `image:` lines.
#
# Pinned components (must match .gitlab-ci.yml's expectations):
#   platforms;android-35, platforms;android-36, build-tools;36.0.0, platform-tools
FROM eclipse-temurin:21-jdk-jammy

ENV ANDROID_HOME=/opt/android-sdk \
    PATH=/opt/android-sdk/build-tools/36.0.0:/opt/android-sdk/platform-tools:/root/.local/bin:$PATH

RUN apt-get update -y -qq && apt-get install -y -qq curl unzip jq \
    && rm -rf /var/lib/apt/lists/* \
    && curl -fsSL https://dl.google.com/android/cli/latest/linux_x86_64/install.sh | bash \
    && android --sdk="$ANDROID_HOME" sdk install \
        'platforms;android-35' \
        'platforms;android-36' \
        'build-tools;36.0.0' \
        platform-tools
