package net.earthcomputer.dekotlinify.impl

import net.earthcomputer.dekotlinify.Dekotlinify
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode

class DekotlinifyClassWriter(private val dekotlinify: Dekotlinify) : ClassWriter(COMPUTE_FRAMES) {
    override fun getCommonSuperClass(type1: String, type2: String): String {
        if (isDerivedFrom(type1, type2)) {
            return type2
        } else if (isDerivedFrom(type2, type1)) {
            return type1
        } else if ((resolve(type1).access and Opcodes.ACC_INTERFACE) != 0 || (resolve(type2).access and Opcodes.ACC_INTERFACE) != 0) {
            return "java/lang/Object"
        }

        var parentType = type1
        do {
            parentType = resolve(parentType).superName ?: return "java/lang/Object"
        } while (!isDerivedFrom(type2, parentType))
        return type1
    }

    private fun isDerivedFrom(subtype: String, supertype: String): Boolean {
        var parent = resolve(subtype).superName
        val visitedTypes = mutableSetOf<String>()
        while (parent != null) {
            if (!visitedTypes.add(parent)) {
                return false
            }
            if (supertype == parent) {
                return true
            }
            parent = resolve(parent).superName
        }
        return false
    }

    private fun resolve(internalName: String): ClassNode {
        return dekotlinify.resolveClass(internalName) ?: throw TypeNotPresentException(internalName, null)
    }
}
