plugins {
    kotlin("jvm")
    dekotlinify
    `maven-publish`
}

publishing {
    repositories {
        maven {
            name = "localPluginRepository"
            url = rootProject.uri("temp/local-plugin-repository")
        }
    }
    publications {
        create<MavenPublication>("dekotlinify") {
            artifactId = "dekotlinify"
            from(components["java"])
        }
    }
}
