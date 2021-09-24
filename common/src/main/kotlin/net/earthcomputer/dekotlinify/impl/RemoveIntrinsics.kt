package net.earthcomputer.dekotlinify.impl

import net.earthcomputer.dekotlinify.Dekotlinify
import org.objectweb.asm.Handle
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.InvokeDynamicInsnNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TypeInsnNode

object RemoveIntrinsics : CodeProcessor() {
    override fun process(clazz: ClassNode, method: MethodNode, insns: InsnList, dekotlinify: Dekotlinify) {
        insns.replace { insn ->
            if (insn !is MethodInsnNode) return@replace
            if (insn.owner != INTRINSICS) return@replace
            when (insn.name) {
                "stringPlus" -> processStringPlus(clazz, method, insn)
                "checkNotNull" -> processCheckNotNull(clazz, method, insn, dekotlinify.removeNullAssertions)
                "throwNpe", "throwJavaNpe" -> throwException(clazz, method, insn, "java/lang/NullPointerException")
                "throwAssert" -> throwException(clazz, method, insn, "java/lang/AssertionError")
                "throwIllegalArgument" -> throwException(clazz, method, insn, "java/lang/IllegalArgumentException")
                "throwIllegalState" -> throwException(clazz, method, insn, "java/lang/IllegalStateException")
            }
        }
        method.onModify()
    }

    private fun ReplacementsProcessor.processStringPlus(
        clazz: ClassNode,
        method: MethodNode,
        insn: MethodInsnNode
    ) {
        if (clazz.version <= Opcodes.V1_8) {
            val sbLocs = Analysis.getSafePushLocations(clazz.name, method, insn, 2)
            val param1Modifies = Analysis.getSafeModifyStackLocations(clazz.name, method, insn, 1)
            if (sbLocs.isEmpty() || param1Modifies.isEmpty()) {
                val toReplace = InsnList()
                val loadInsns = Analysis.addFallbackUnderStackInsns(clazz.name, method, insn, 2, toReplace)
                toReplace.add(TypeInsnNode(Opcodes.NEW, "java/lang/StringBuilder"))
                toReplace.add(InsnNode(Opcodes.DUP))
                toReplace.add(loadInsns[1])
                toReplace.add(
                    MethodInsnNode(
                        Opcodes.INVOKESPECIAL,
                        "java/lang/StringBuilder",
                        "<init>",
                        "(Ljava/lang/String;)V",
                        false
                    )
                )
                toReplace.add(loadInsns[0])
                toReplace.add(
                    MethodInsnNode(
                        Opcodes.INVOKEVIRTUAL,
                        "java/lang/StringBuilder",
                        "append",
                        "(Ljava/lang/Object;)Ljava/lang/StringBuilder;",
                        false
                    )
                )
                toReplace.add(
                    MethodInsnNode(
                        Opcodes.INVOKEVIRTUAL,
                        "java/lang/StringBuilder",
                        "toString",
                        "()Ljava/lang/String;"
                    )
                )
                replace(toReplace)
            } else {
                for (sbLoc in sbLocs) {
                    val toInsert = InsnList()
                    toInsert.add(TypeInsnNode(Opcodes.NEW, "java/lang/StringBuilder"))
                    toInsert.add(InsnNode(Opcodes.DUP))
                    insertBefore(sbLoc, toInsert)
                }
                for (param1Modify in param1Modifies) {
                    val toInsert = InsnList()
                    toInsert.add(
                        MethodInsnNode(
                            Opcodes.INVOKESPECIAL,
                            "java/lang/StringBuilder",
                            "<init>",
                            "(Ljava/lang/String;)V",
                            false
                        )
                    )
                    insertBefore(param1Modify, toInsert)
                }
                val toReplace = InsnList()
                toReplace.add(
                    MethodInsnNode(
                        Opcodes.INVOKEVIRTUAL,
                        "java/lang/StringBuilder",
                        "append",
                        "(Ljava/lang/Object;)Ljava/lang/StringBuilder;",
                        false
                    )
                )
                toReplace.add(
                    MethodInsnNode(
                        Opcodes.INVOKEVIRTUAL,
                        "java/lang/StringBuilder",
                        "toString",
                        "()Ljava/lang/String;"
                    )
                )
                replace(toReplace)
            }
        } else {
            val toReplace = InsnList()
            toReplace.add(
                InvokeDynamicInsnNode(
                    "stringPlus",
                    "(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/String;",
                    Handle(
                        Opcodes.H_INVOKESTATIC,
                        "java/lang/invoke/StringConcatFactory",
                        "makeConcat",
                        "(Ljava/lang/invoke/MethodHandles\$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
                        false
                    )
                )
            )
            replace(toReplace)
        }
    }

    private fun ReplacementsProcessor.processCheckNotNull(
        clazz: ClassNode,
        method: MethodNode,
        insn: MethodInsnNode,
        removeNullAssertions: Boolean
    ) {
        if (removeNullAssertions) {
            val toReplace = InsnList()
            for (paramType in Type.getArgumentTypes(insn.desc).reversed()) {
                if (paramType.size == 1) {
                    toReplace.add(InsnNode(Opcodes.POP))
                } else {
                    toReplace.add(InsnNode(Opcodes.POP2))
                }
            }
            replace(toReplace)
            return
        }

        if (insn.desc == "(Ljava/lang/Object;)V") {
            val toReplace = InsnList()
            // idiomatic null check
            if (clazz.version <= Opcodes.V1_8) {
                toReplace.add(MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Object", "getClass", "()Ljava/lang/Class;"))
                toReplace.add(InsnNode(Opcodes.POP))
            } else {
                toReplace.add(MethodInsnNode(Opcodes.INVOKESTATIC, "java/util/Objects", "requireNonNull", "(Ljava/lang/Object;)Ljava/lang/Object;", false))
                toReplace.add(InsnNode(Opcodes.POP))
            }
            replace(toReplace)
        } else if (insn.desc == "(Ljava/lang/Object;Ljava/lang/String;)V") {
            val toReplace = InsnList()
            if (clazz.version <= Opcodes.V1_8) {
                val param1Locs = Analysis.getSafeModifyStackLocations(clazz.name, method, insn, 1)
                val label = LabelNode()
                if (param1Locs.isEmpty()) {
                    val loadInsns = Analysis.addFallbackUnderStackInsns(clazz.name, method, insn, 2, toReplace)
                    toReplace.add(loadInsns[1])
                    toReplace.add(JumpInsnNode(Opcodes.IFNONNULL, label))
                    toReplace.add(TypeInsnNode(Opcodes.NEW, "java/lang/NullPointerException"))
                    toReplace.add(InsnNode(Opcodes.DUP))
                    toReplace.add(loadInsns[0])
                    toReplace.add(MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/NullPointerException", "<init>", "(Ljava/lang/String;)V", false))
                    toReplace.add(InsnNode(Opcodes.ATHROW))
                    toReplace.add(label)
                } else {
                    for (param1Loc in param1Locs) {
                        val toInsert = InsnList()
                        toInsert.add(JumpInsnNode(Opcodes.IFNONNULL, label))
                        toInsert.add(TypeInsnNode(Opcodes.NEW, "java/lang/NullPointerException"))
                        toReplace.add(InsnNode(Opcodes.DUP))
                        insertBefore(param1Loc, toInsert)
                    }
                    toReplace.add(MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/NullPointerException", "<init>", "(Ljava/lang/String;)V", false))
                    toReplace.add(InsnNode(Opcodes.ATHROW))
                    toReplace.add(label)
                }
            } else {
                toReplace.add(MethodInsnNode(Opcodes.INVOKESTATIC, "java/util/Objects", "requireNonNull", "(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;", false))
                toReplace.add(InsnNode(Opcodes.POP))
            }
            replace(toReplace)
        }
    }

    private fun ReplacementsProcessor.throwException(
        clazz: ClassNode,
        method: MethodNode,
        insn: MethodInsnNode,
        exceptionType: String
    ) {
        if (insn.desc == "()V") {
            val toReplace = InsnList()
            toReplace.add(TypeInsnNode(Opcodes.NEW, exceptionType))
            toReplace.add(InsnNode(Opcodes.DUP))
            toReplace.add(MethodInsnNode(Opcodes.INVOKESPECIAL, exceptionType, "<init>", "()V", false))
            toReplace.add(InsnNode(Opcodes.ATHROW))
            replace(toReplace)
        } else if (insn.desc == "(Ljava/lang/String;)V") {
            val constructLocs = Analysis.getSafePushLocations(clazz.name, method, insn, 1)
            if (constructLocs.isEmpty()) {
                val toReplace = InsnList()
                val loadInsns = Analysis.addFallbackUnderStackInsns(clazz.name, method, insn, 1, toReplace)
                toReplace.add(TypeInsnNode(Opcodes.NEW, exceptionType))
                toReplace.add(InsnNode(Opcodes.DUP))
                toReplace.add(loadInsns[0])
                toReplace.add(MethodInsnNode(Opcodes.INVOKESPECIAL, exceptionType, "<init>", "(Ljava/lang/String;)V", false))
                toReplace.add(InsnNode(Opcodes.ATHROW))
                replace(toReplace)
            } else {
                for (constructLoc in constructLocs) {
                    val toInsert = InsnList()
                    toInsert.add(TypeInsnNode(Opcodes.NEW, exceptionType))
                    toInsert.add(InsnNode(Opcodes.DUP))
                    insertBefore(constructLoc, toInsert)
                }
                val toReplace = InsnList()
                toReplace.add(MethodInsnNode(Opcodes.INVOKESPECIAL, exceptionType, "<init>", "(Ljava/lang/String;)V", false))
                toReplace.add(InsnNode(Opcodes.ATHROW))
                replace(toReplace)
            }
        }
    }
}
