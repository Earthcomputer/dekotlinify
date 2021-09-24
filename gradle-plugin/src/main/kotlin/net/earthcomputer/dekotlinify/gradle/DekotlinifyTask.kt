package net.earthcomputer.dekotlinify.gradle

import net.earthcomputer.dekotlinify.Dekotlinify
import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import java.util.jar.JarFile

@CacheableTask
open class DekotlinifyTask : DefaultTask() {
    @get:InputFiles
    @get:Classpath
    var classpath: FileCollection = project.objects.fileCollection()

    @get:PathSensitive(PathSensitivity.NONE)
    @get:InputFile
    var input: Any? = null

    @get:Input
    var useLocalClasspath = true

    @get:Input
    var removeNullAssertions = false

    @get:OutputFile
    var output: Any? = null

    @TaskAction
    fun run() {
        val inputFile = input?.let { project.file(it) } ?: throw IllegalStateException("Need to specify an input jar for the dekotlinify task")
        val outputFile = output?.let { project.file(it) } ?: throw IllegalStateException("Need to specify an output jar for the dekotlinify task")

        val dekotlinify = Dekotlinify()
        dekotlinify.removeNullAssertions = removeNullAssertions
        if (!useLocalClasspath) {
            dekotlinify.clearClassResolvers()
        }

        val indexedClasspath = (classpath.asFileTree.asSequence() + sequenceOf(inputFile))
            .filter { !it.isDirectory }
            .flatMap { file ->
                if (file.name.endsWith(".jar")) {
                    JarFile(file).use { jar ->
                        jar.entries().asSequence()
                            .filter { it.name.endsWith(".class") }
                            .map { it.name.substring(0, it.name.length - 6) to file }
                            .toList() // convert to list back to sequence to avoid accessing the jar once it's closed
                            .asSequence()
                    }
                } else if (file.name.endsWith(".class")) {
                    sequenceOf(file.name.substring(0, file.name.length - 6) to file)
                } else {
                    emptySequence()
                }
            }
            .groupBy({ it.first }) { it.second }
        dekotlinify.addClassResolverFirst { className ->
            for (nameCandidate in listOf(className, className.substringAfterLast('/'))) {
                val files = indexedClasspath[nameCandidate] ?: continue
                for (file in files) {
                    val reader = if (file.name.endsWith(".jar")) {
                        JarFile(file).use { jar ->
                            val entry = jar.getJarEntry("$className.class") ?: return@use null
                            ClassReader(jar.getInputStream(entry))
                        }
                    } else {
                        file.inputStream().use {
                            ClassReader(it)
                        }
                    } ?: continue
                    if (reader.className == className) {
                        val node = ClassNode()
                        reader.accept(node, ClassReader.SKIP_FRAMES)
                        return@addClassResolverFirst node
                    }
                }
            }
            null
        }

        dekotlinify.processJar(inputFile.toPath(), outputFile.toPath())
    }
}
