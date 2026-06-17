# Ktor backend image for Coolify.
# Build context is the REPO ROOT (the Gradle build needs the wrapper, settings and
# version catalog). :server has no dependency on :shared/:composeApp, so configuration
# on demand lets us build it without the Android SDK.

# ── Build stage ───────────────────────────────────────────────
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Wrapper + build scripts first for better layer caching.
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY server ./server
# Project dirs referenced by settings.gradle.kts must exist for evaluation,
# but configuration-on-demand skips configuring them for :server tasks.
COPY composeApp/build.gradle.kts ./composeApp/build.gradle.kts
COPY shared/build.gradle.kts ./shared/build.gradle.kts

RUN chmod +x gradlew && \
    ./gradlew :server:installDist --no-daemon -Dorg.gradle.configureondemand=true

# ── Runtime stage ─────────────────────────────────────────────
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /workspace/server/build/install/server ./

# Uploaded files — mount a Coolify volume here to persist across deploys.
RUN mkdir -p /app/uploads
VOLUME ["/app/uploads"]

# Provided by Coolify at runtime: DATABASE_URL, JWT_SECRET,
# ONESIGNAL_APP_ID, ONESIGNAL_API_KEY (see application.conf).
EXPOSE 8080

ENTRYPOINT ["./bin/server"]
