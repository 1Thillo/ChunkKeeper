plugins {
    java
    id("xyz.jpenilla.run-paper") version "3.1.0"
}

group = "de.thillo.chunkkeeper"
version = "1.1.0"

repositories {
    mavenCentral()
    maven(url = uri("https://repo.papermc.io/repository/maven-public/"))
    maven(url = uri("https://maven.enginehub.org/repo/"))
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.128-stable")
    // Optional: only used when WorldEdit/FastAsyncWorldEdit or WorldGuard is installed
    compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.4.5") {
        isTransitive = false
    }
    compileOnly("com.sk89q.worldedit:worldedit-core:7.4.5") {
        isTransitive = false
    }
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.19") {
        isTransitive = false
    }
    compileOnly("com.sk89q.worldguard:worldguard-core:7.0.19") {
        isTransitive = false
    }

    testImplementation("io.papermc.paper:paper-api:26.2.build.128-stable")
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks {
    compileJava {
        options.encoding = Charsets.UTF_8.name()
        options.release.set(25)
    }
    javadoc {
        options.encoding = Charsets.UTF_8.name()
    }
    processResources {
        filteringCharset = Charsets.UTF_8.name()
        val pluginVersion = project.version.toString()
        inputs.property("version", pluginVersion)
        filesMatching("plugin.yml") {
            expand(mapOf("version" to pluginVersion))
        }
    }
    test {
        useJUnitPlatform()
    }
    runServer {
        minecraftVersion("26.2")
    }
}

// Development server for hotswap: ./gradlew runServer, then attach a debugger to localhost:5005.
// The JetBrains Runtime with enhanced class redefinition also applies changes to fields, methods
// and class structure, not only to method bodies.
tasks.withType<xyz.jpenilla.runtask.task.AbstractRun>().configureEach {
    javaLauncher = javaToolchains.launcherFor {
        vendor = JvmVendorSpec.JETBRAINS
        languageVersion = JavaLanguageVersion.of(25)
    }
    jvmArgs(
        "-XX:+AllowEnhancedClassRedefinition",
        "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=localhost:5005",
    )
}
