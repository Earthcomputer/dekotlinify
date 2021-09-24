package net.earthcomputer.dekotlinify.impl

import org.objectweb.asm.tree.ClassNode

object RemoveKotlinMetadata {
    fun process(clazz: ClassNode) {
        clazz.visibleAnnotations?.removeIf { it.desc == "Lkotlin/Metadata;" }
    }
}
