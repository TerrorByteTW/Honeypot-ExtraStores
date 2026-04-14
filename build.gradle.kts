import org.apache.tools.ant.filters.ReplaceTokens

plugins {
    java
    id("com.gradleup.shadow") version "9.2.2"
    alias(libs.plugins.lombok);
}

project.group = "org.reprogle"
project.version = "1.0"
project.description = "Provides extra data stores to Honeypot"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }

    maven {
        name = "sonatype"
        url = uri("https://oss.sonatype.org/content/groups/public/")
    }

    maven {
        name = "jitpack"
        url = uri("https://jitpack.io")
    }
    mavenCentral()
}

dependencies {
    compileOnly(libs.paper.api)
    compileOnly(libs.honeypot.api)
    compileOnly(libs.boosted.yaml)
    implementation(libs.hikaricp)
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.processResources {
    outputs.upToDateWhen { false }
    from(sourceSets.main.get().resources.srcDirs) {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
        filter<ReplaceTokens>(
            "tokens" to mapOf(
                "version" to project.version.toString(),
            )
        )
    }
}
