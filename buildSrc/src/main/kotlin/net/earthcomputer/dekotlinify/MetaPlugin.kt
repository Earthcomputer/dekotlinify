package net.earthcomputer.dekotlinify

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

class MetaPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.doApply()
    }
}

private fun Project.doApply() {
    group = "net.earthcomputer.dekotlinify"
    version = "1.0"

    repositories.apply {
        mavenCentral()
    }

    dependencies.apply {
        add("api", "org.ow2.asm:asm:9.2")
        add("api", "org.ow2.asm:asm-tree:9.2")
        add("implementation", "org.ow2.asm:asm-analysis:9.2")
        add("compileOnly", "org.jetbrains:annotations:22.0.0")

        //add("testImplementation", kotlin("test"))
    }

    tasks.getByName("test").apply {
        this as Test
        useJUnit()
    }

    tasks.withType(KotlinCompile::class.java) {
        it.kotlinOptions.jvmTarget = "1.8"
    }
}

