package net.earthcomputer.dekotlinify.impl

import net.earthcomputer.dekotlinify.Dekotlinify
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode

object ReplaceWithSurrogates : CodeProcessor() {
    override fun process(clazz: ClassNode, method: MethodNode, insns: InsnList, dekotlinify: Dekotlinify) {
        insns.replace { insn ->
            if (insn !is MethodInsnNode) return@replace
            when (insn.owner) {
                INTRINSICS -> when (insn.name) {
                    "checkNotNull" -> {
                        val methodName = when {
                            dekotlinify.removeNullAssertions -> "noOp"
                            clazz.version <= Opcodes.V1_8 -> "checkNotNullJava8"
                            else -> "checkNotNullJava9"
                        }
                        replace(insnListOf(MethodInsnNode(Opcodes.INVOKESTATIC, INTRINSICS_SURROGATAE, methodName, insn.desc, false)))
                    }
                    "checkParameterIsNotNull",
                    "checkNotNullParameter" -> {
                        val methodName = if (dekotlinify.removeNullAssertions) "noOp" else insn.name
                        replace(insnListOf(
                            LdcInsnNode(clazz.name.replace('/', '.')),
                            LdcInsnNode(method.name),
                            MethodInsnNode(Opcodes.INVOKESTATIC, INTRINSICS_SURROGATAE, methodName, "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V", false)
                        ))
                    }
                    "checkExpressionValueIsNotNull",
                    "checkNotNullExpressionValue",
                    "checkReturnedValueIsNotNull",
                    "checkFieldIsNotNull" -> {
                        val methodName = if (dekotlinify.removeNullAssertions) "noOp" else insn.name
                        replace(insnListOf(MethodInsnNode(Opcodes.INVOKESTATIC, INTRINSICS_SURROGATAE, methodName, insn.desc, false)))
                    }
                    "throwNpe",
                    "throwJavaNpe",
                    "throwUninitializedProperty",
                    "throwUninitializedPropertyAccessException",
                    "throwAssert",
                    "throwIllegalArgument",
                    "throwIllegalState",
                    "throwUndefinedForReified",
                    "reifiedOperationMarker",
                    "needClassReification",
                    "checkHasClass" -> replace(insnListOf(MethodInsnNode(Opcodes.INVOKESTATIC, INTRINSICS_SURROGATAE, insn.name, insn.desc, false)))
                }
            }
        }
        method.onModify()
    }
}
