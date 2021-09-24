package net.earthcomputer.dekotlinify.impl

import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.InsnList
import java.util.TreeMap

const val INTRINSICS = "kotlin/jvm/internal/Intrinsics"

inline fun InsnList.replace(crossinline func: ReplacementsProcessor.(AbstractInsnNode) -> Unit) {
    val insertBefore = TreeMap<AbstractInsnNode, InsnList>(Comparator.comparingInt { indexOf(it) })
    val replacements = TreeMap<AbstractInsnNode, InsnList>(Comparator.comparingInt { indexOf(it) })
    for (insn in iterator()) {
        val processor = ReplacementsProcessor()
        processor.func(insn)
        processor.insertBeforeCurrentInsn?.let {
            if (insertBefore.put(insn, it) != null) {
                throw IllegalStateException("Inserting before same instruction twice")
            }
        }
        processor.replaceCurrentInsn?.let {
            if (replacements.put(insn, it) != null) {
                throw IllegalStateException("Replacing same instruction twice")
            }
        }
        for ((otherInsn, insns) in processor.insertBefore) {
            if (insertBefore.put(otherInsn, insns) != null) {
                throw IllegalStateException("Inserting before same instruction twice")
            }
        }
        for ((otherInsn, insns) in processor.replacements) {
            if (replacements.put(otherInsn, insns) != null) {
                throw IllegalStateException("Replacing same instruction twice")
            }
        }

        if (processor.stopped) {
            break
        }
    }

    for ((insn, toInsert) in insertBefore.descendingMap()) {
        insertBefore(insn, toInsert)
    }
    for ((insn, toInsert) in replacements.descendingMap()) {
        insert(insn, toInsert)
        remove(insn)
    }
}

class ReplacementsProcessor {
    @PublishedApi
    internal var insertBeforeCurrentInsn: InsnList? = null
    @PublishedApi
    internal var replaceCurrentInsn: InsnList? = null
    @PublishedApi
    internal val insertBefore = mutableMapOf<AbstractInsnNode, InsnList>()
    @PublishedApi
    internal val replacements = mutableMapOf<AbstractInsnNode, InsnList>()
    @PublishedApi
    internal var stopped = false

    fun insertBefore(insns: InsnList) {
        insertBeforeCurrentInsn = insns
    }

    fun insertBefore(insn: AbstractInsnNode, insns: InsnList) {
        insertBefore[insn] = insns
    }

    fun replace(insns: InsnList) {
        replaceCurrentInsn = insns
    }

    fun replace(insn: AbstractInsnNode, insns: InsnList) {
        replacements[insn] = insns
    }

    fun stop() {
        stopped = true
    }
}

fun <T : Any> Array<T?>.coerceNotNull(): Array<T> {
    assert(all { it != null })
    @Suppress("UNCHECKED_CAST")
    return this as Array<T>
}
