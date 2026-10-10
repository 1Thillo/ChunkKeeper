plugins {
    java
    id("xyz.jpenilla.run-paper") version "3.1.0"
}

group = "de.thillo.chunkkeeper"
version = "0.1.0"

repositories {
    mavenCentral()
    maven(url = uri("https://repo.papermc.io/repository/maven-public/"))
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.128-stable")
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
    runServer {
        minecraftVersion("26.2")
    }
}
