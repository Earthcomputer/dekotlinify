
plugins {
    kotlin("jvm")
    dekotlinify
    `java-gradle-plugin`
    `maven-publish`
    id("com.gradle.plugin-publish") version "0.14.0"
}

base {
    archivesName.set("dekotlinify-gradle")
}

dependencies {
    implementation(project(":common"))
}

gradlePlugin {
    plugins {
        create("dekotlinify") {
            id = "net.earthcomputer.dekotlinify"
            displayName = "Dekotlinify Gradle Plugin"
            description = "Gradle plugin to apply Dekotlinify in Gradle"
            implementationClass = "net.earthcomputer.dekotlinify.gradle.DekotlinifyPlugin"
        }
    }
}

pluginBundle {
    website = "https://earthcomputer.net/"
    vcsUrl = "https://github.com/Earthcomputer/dekotlinify"
    tags = listOf("compilation")
}

publishing {
    repositories {
        maven {
            name = "localPluginRepository"
            url = rootProject.uri("temp/local-plugin-repository")
        }
    }
}
