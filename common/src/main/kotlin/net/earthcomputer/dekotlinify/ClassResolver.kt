package net.earthcomputer.dekotlinify

import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode

fun interface ClassResolver {
    fun resolve(internalName: String): ClassNode?
}

internal object LocalClassResolver : ClassResolver {
    override fun resolve(internalName: String): ClassNode? {
        return LocalClassResolver::class.java.classLoader.getResourceAsStream("$internalName.class")?.let {
            val node = ClassNode()
            ClassReader(it).accept(node, ClassReader.SKIP_FRAMES)
            node
        }
    }
}

internal object SurrogateClassResolver : ClassResolver {
    override fun resolve(internalName: String): ClassNode? {
        return if (internalName.startsWith("net/earthcomputer/dekotlinify/surrogate/")) {
            LocalClassResolver.resolve(internalName)
        } else {
            null
        }
    }
}
