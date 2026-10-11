plugins {
    // lets Gradle download the JetBrains Runtime that runServer uses for hotswap
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "ChunkKeeper"
