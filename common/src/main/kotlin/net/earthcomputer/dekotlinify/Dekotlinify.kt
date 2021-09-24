package net.earthcomputer.dekotlinify

import net.earthcomputer.dekotlinify.impl.DekotlinifyClassWriter
import net.earthcomputer.dekotlinify.impl.RemoveIntrinsics
import net.earthcomputer.dekotlinify.impl.RemoveKotlinMetadata
import org.jetbrains.annotations.ApiStatus
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import java.lang.ref.SoftReference
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class Dekotlinify {
    var removeNullAssertions = false

    private val classResolvers: MutableList<ClassResolver> = mutableListOf(LocalClassResolver)
    private val classCache = mutableMapOf<String, SoftReference<ClassNode>>()

    fun clearClassResolvers() {
        classResolvers.clear()
    }

    fun addClassResolver(classResolver: ClassResolver) {
        classResolvers += classResolver
    }

    fun addClassResolverFirst(classResolver: ClassResolver) {
        classResolvers.add(0, classResolver)
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
                        val writer = DekotlinifyClassWriter(this)
                        clazz.accept(writer)
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
        RemoveKotlinMetadata.process(clazz)
        RemoveIntrinsics.process(clazz, this)
    }
}
