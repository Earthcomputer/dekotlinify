package net.earthcomputer.dekotlinify

import net.earthcomputer.dekotlinify.impl.DebugClassVisitor
import net.earthcomputer.dekotlinify.impl.DekotlinifyClassWriter
import net.earthcomputer.dekotlinify.impl.FunctionInliner
import net.earthcomputer.dekotlinify.impl.ReplaceWithSurrogates
import net.earthcomputer.dekotlinify.impl.RemoveKotlinMetadata
import net.earthcomputer.dekotlinify.impl.analyzeSourcesAndUsages
import org.jetbrains.annotations.ApiStatus
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.util.TraceClassVisitor
import java.io.FileWriter
import java.io.PrintWriter
import java.lang.ref.SoftReference
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class Dekotlinify {
    var debug = false
    var removeNullAssertions = false

    private val classResolvers: MutableList<ClassResolver> = mutableListOf(SurrogateClassResolver, LocalClassResolver)
    private val classCache = mutableMapOf<String, SoftReference<ClassNode>>()

    fun clearClassResolvers() {
        classResolvers.clear()
        classResolvers += SurrogateClassResolver
    }

    fun addClassResolver(classResolver: ClassResolver) {
        classResolvers += classResolver
    }

    fun addClassResolverFirst(classResolver: ClassResolver) {
        classResolvers.add(1, classResolver) // after SurrogateClassResolver
    }

    @ApiStatus.Internal
    fun resolveClass(internalName: String): ClassNode? {
        classCache[internalName]?.get()?.let { return it }
        return classResolvers.firstNotNullOfOrNull { it.resolve(internalName) }?.also {
            classCache[internalName] = SoftReference(it)
        }
    }

    fun processJar(inputJar: Path, outputJar: Path) {
        ZipInputStream(Files.newInputStream(inputJar)).use { zipIn ->
            ZipOutputStream(Files.newOutputStream(outputJar)).use { zipOut ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    zipOut.putNextEntry(ZipEntry(entry.name))
                    if (entry.name.endsWith(".class")) {
                        val clazz = ClassNode()
                        ClassReader(zipIn).accept(clazz, ClassReader.SKIP_FRAMES)
                        process(clazz)
                        for (method in clazz.methods) {
                            method.analyzeSourcesAndUsages(clazz.name)
                        }
                        val writer = DekotlinifyClassWriter(this)
                        if (debug) {
                            try {
                                clazz.accept(DebugClassVisitor(writer))
                            } catch (e: Throwable) {
                                println("Class verify failed, outputting to debug-class.txt")
                                FileWriter("debug-class.txt").use {
                                    clazz.accept(TraceClassVisitor(PrintWriter(it)))
                                }
                                throw e
                            }
                        } else {
                            clazz.accept(writer)
                        }
                        zipOut.write(writer.toByteArray())
                    } else if (!entry.isDirectory) {
                        zipIn.copyTo(zipOut)
                    }
                    zipOut.closeEntry()
                    entry = zipIn.nextEntry
                }
            }
        }
    }

    fun process(clazz: ClassNode) {
        threadLocalShouldUseDebug.set(debug)
        RemoveKotlinMetadata.process(clazz)
        ReplaceWithSurrogates.process(clazz, this)
        FunctionInliner.process(clazz, this)
    }

    companion object {
        val shouldUseDebug: Boolean
            get() = threadLocalShouldUseDebug.get()
        private val threadLocalShouldUseDebug = ThreadLocal.withInitial { false }
    }
}
