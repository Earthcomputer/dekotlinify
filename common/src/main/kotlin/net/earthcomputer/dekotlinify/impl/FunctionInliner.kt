package net.earthcomputer.dekotlinify.impl

import net.earthcomputer.dekotlinify.Dekotlinify
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.IincInsnNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LineNumberNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.VarInsnNode
import java.util.Collections
import java.util.WeakHashMap

object FunctionInliner : CodeProcessor() {
    private val recursiveInlineCache = WeakHashMap<MethodNode, MethodNode>()
    private val cannotInlineCache = Collections.newSetFromMap<MethodNode>(WeakHashMap())

    override fun process(clazz: ClassNode, method: MethodNode, insns: InsnList, dekotlinify: Dekotlinify) {
        inline(clazz, method, mutableSetOf(), dekotlinify)
        method.onModify()
    }

    private fun inline(clazz: ClassNode, method: MethodNode, alreadyInlined: MutableSet<String>, dekotlinify: Dekotlinify) {
        val newTryCatchBlocks = method.tryCatchBlocks.toMutableList()
        val newLocalVariables = method.localVariables?.toMutableList() ?: mutableListOf()

        method.instructions.replace { insn ->
            if (insn !is MethodInsnNode) return@replace
            if (!shouldInline(insn.owner, insn.name)) return@replace

            val (targetClass, targetMethod) = resolveMethod(insn, dekotlinify) ?: return@replace
            if (targetMethod in cannotInlineCache) return@replace
            val methodId = "${targetClass.name}.${targetMethod.name}"
            if (methodId in alreadyInlined) {
                cannotInlineCache += targetMethod
                return@replace
            }
            alreadyInlined += methodId

            val methodToInline = (recursiveInlineCache[targetMethod] ?: run {
                val result = targetMethod.copy()
                inline(targetClass, result, alreadyInlined, dekotlinify)
                recursiveInlineCache[targetMethod] = result
                result
            }).copy()

            val returnType = Type.getReturnType(targetMethod.desc)
            val returnAddr = Analysis.getNextFreeLocal(clazz.name, method, insn)
            val localsOffset = returnAddr + returnType.size

            val returnLabel = LabelNode()
            var usedReturnLabel = false
            methodToInline.instructions.replace { insnToInline ->
                when {
                    insnToInline.opcode in Opcodes.IRETURN..Opcodes.RETURN -> {
                        val toReplace = InsnList()
                        if (returnType.sort != Type.VOID) {
                            toReplace.add(VarInsnNode(returnType.getOpcode(Opcodes.ISTORE), returnAddr))
                        }
                        if (insnToInline.next != null) {
                            usedReturnLabel = true
                            toReplace.add(JumpInsnNode(Opcodes.GOTO, returnLabel))
                        }
                        replace(toReplace)
                    }
                    insnToInline is VarInsnNode -> {
                        replace(insnListOf(VarInsnNode(insnToInline.opcode, insnToInline.`var` + localsOffset)))
                    }
                    insnToInline is IincInsnNode -> {
                        replace(insnListOf(IincInsnNode(insnToInline.`var` + localsOffset, insnToInline.incr)))
                    }
                    insnToInline is LineNumberNode -> {
                        replace(InsnList())
                    }
                }
            }
            if (usedReturnLabel) {
                methodToInline.instructions.add(returnLabel)
            }
            if (returnType.sort != Type.VOID) {
                methodToInline.instructions.add(VarInsnNode(returnType.getOpcode(Opcodes.ILOAD), returnAddr))
            }
            if (methodToInline.localVariables != null) {
                for (localVar in methodToInline.localVariables) {
                    localVar.index += localsOffset
                }
            }

            val toReplace = InsnList()
            var paramVar = localsOffset
            if (insn.opcode != Opcodes.INVOKESTATIC) paramVar++
            for (paramType in Type.getArgumentTypes(targetMethod.desc)) {
                val varInsn = VarInsnNode(paramType.getOpcode(Opcodes.ISTORE), paramVar)
                if (toReplace.size() == 0) {
                    toReplace.add(varInsn)
                } else {
                    toReplace.insertBefore(toReplace.first, varInsn)
                }
                paramVar += paramType.size
            }
            if (insn.opcode != Opcodes.INVOKESTATIC) {
                toReplace.add(VarInsnNode(Opcodes.ASTORE, localsOffset)) // this
            }
            toReplace.add(methodToInline.instructions)
            replace(toReplace)
            methodToInline.tryCatchBlocks?.let { newTryCatchBlocks += it }
            methodToInline.localVariables?.let { newLocalVariables += it }

            alreadyInlined.remove(methodId)
        }

        method.tryCatchBlocks = newTryCatchBlocks
        method.localVariables = newLocalVariables.takeIf { it.isNotEmpty() }
        method.onModify()
    }

    private fun resolveMethod(methodInsn: MethodInsnNode, dekotlinify: Dekotlinify): Pair<ClassNode, MethodNode>? {
        var ownerClass: ClassNode? = dekotlinify.resolveClass(methodInsn.owner)
        while (ownerClass != null) {
            if (ownerClass.methods != null) {
                for (method in ownerClass.methods) {
                    if (method.name == methodInsn.name && method.desc == methodInsn.desc) {
                        if (method.instructions == null || method.instructions.size() == 0) return null
                        return ownerClass to method
                    }
                }
            }

            if (methodInsn.opcode == Opcodes.INVOKESTATIC || methodInsn.opcode == Opcodes.INVOKESPECIAL) {
                break
            }

            if (!shouldInline(ownerClass.superName, methodInsn.name)) break
            ownerClass = dekotlinify.resolveClass(ownerClass.superName)
        }
        return null
    }

    private fun shouldInline(className: String, methodName: String): Boolean {
        if (methodName.startsWith("<")) return false
        return className.startsWith("kotlin/") || className.startsWith("net/earthcomputer/dekotlinify/surrogate/")
    }
}
