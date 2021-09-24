package net.earthcomputer.dekotlinify

import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode

fun interface ClassResolver {
    fun resolve(internalName: String): ClassNode?
}

object LocalClassResolver : ClassResolver {
    override fun resolve(internalName: String): ClassNode? {
        return Sequence::class.java.classLoader.getResourceAsStream("$internalName.class")?.let {
            val node = ClassNode()
            ClassReader(it).accept(node, ClassReader.SKIP_FRAMES)
            node
        }
    }
}
