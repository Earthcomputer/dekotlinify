
plugins {
    kotlin("jvm") version "+"
    `java-gradle-plugin`
}

repositories {
    mavenCentral()
    maven { url = uri("https://plugins.gradle.org/m2/") }
}

dependencies {
    implementation("org.gradle.kotlin:gradle-kotlin-dsl-plugins:+")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:+")
}

gradlePlugin {
    plugins {
        create("dekotlinify") {
            id = "dekotlinify"
            implementationClass = "net.earthcomputer.dekotlinify.MetaPlugin"
        }
    }
}
