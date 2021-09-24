package net.earthcomputer.dekotlinify.impl

import net.earthcomputer.dekotlinify.Dekotlinify
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.MethodNode

abstract class CodeProcessor {
    fun process(clazz: ClassNode, dekotlinify: Dekotlinify) {
        val methods = clazz.methods ?: return
        for (method in methods) {
            val insns = method.instructions ?: continue
            process(clazz, method, insns, dekotlinify)
        }
    }

    protected abstract fun process(clazz: ClassNode, method: MethodNode, insns: InsnList, dekotlinify: Dekotlinify)
}
