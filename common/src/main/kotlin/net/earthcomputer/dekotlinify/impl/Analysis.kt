package net.earthcomputer.dekotlinify.impl

import org.objectweb.asm.Opcodes
import org.objectweb.asm.Opcodes.ASM9
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.VarInsnNode
import org.objectweb.asm.tree.analysis.Analyzer
import org.objectweb.asm.tree.analysis.BasicInterpreter
import org.objectweb.asm.tree.analysis.BasicValue
import org.objectweb.asm.tree.analysis.Frame
import org.objectweb.asm.tree.analysis.Interpreter
import org.objectweb.asm.tree.analysis.SourceInterpreter
import org.objectweb.asm.tree.analysis.SourceValue
import org.objectweb.asm.tree.analysis.Value
import java.util.WeakHashMap

data class SourcesAndUsagesValue(
    val sourceValue: SourceValue,
    val typeInfo: BasicValue,
    var usages: MutableSet<AbstractInsnNode> = mutableSetOf()
) : Value {
    override fun getSize() = sourceValue.getSize()
}

class SourcesAndUsagesInterpreter : Interpreter<SourcesAndUsagesValue>(ASM9) {
    private val sourceInterp = SourceInterpreter()
    private val basicInterp = BasicInterpreter()

    override fun newValue(type: Type): SourcesAndUsagesValue? {
        return if (type === Type.VOID_TYPE) {
            null
        } else {
            SourcesAndUsagesValue(
                sourceInterp.newValue(type),
                basicInterp.newValue(type)
            )
        }
    }

    override fun newOperation(insn: AbstractInsnNode): SourcesAndUsagesValue {
        return SourcesAndUsagesValue(
            sourceInterp.newOperation(insn),
            basicInterp.newOperation(insn)
        )
    }

    override fun copyOperation(insn: AbstractInsnNode, value: SourcesAndUsagesValue): SourcesAndUsagesValue {
        value.usages += insn
        return SourcesAndUsagesValue(
            SourceValue(value.size, insn),
            basicInterp.copyOperation(insn, value.typeInfo)
        )
    }

    override fun unaryOperation(insn: AbstractInsnNode, value: SourcesAndUsagesValue): SourcesAndUsagesValue {
        value.usages += insn
        return SourcesAndUsagesValue(
            sourceInterp.unaryOperation(insn, value.sourceValue),
            basicInterp.unaryOperation(insn, value.typeInfo)
        )
    }

    override fun binaryOperation(insn: AbstractInsnNode, value1: SourcesAndUsagesValue, value2: SourcesAndUsagesValue): SourcesAndUsagesValue {
        value1.usages += insn
        value2.usages += insn
        return SourcesAndUsagesValue(
            sourceInterp.binaryOperation(insn, value1.sourceValue, value2.sourceValue),
            basicInterp.binaryOperation(insn, value1.typeInfo, value2.typeInfo)
        )
    }

    override fun ternaryOperation(
        insn: AbstractInsnNode,
        value1: SourcesAndUsagesValue,
        value2: SourcesAndUsagesValue,
        value3: SourcesAndUsagesValue
    ): SourcesAndUsagesValue {
        value1.usages += insn
        value2.usages += insn
        value2.usages += insn
        return SourcesAndUsagesValue(
            sourceInterp.ternaryOperation(insn, value1.sourceValue, value2.sourceValue, value3.sourceValue),
            basicInterp.ternaryOperation(insn, value1.typeInfo, value2.typeInfo, value3.typeInfo)
        )
    }

    override fun naryOperation(insn: AbstractInsnNode, values: List<SourcesAndUsagesValue>): SourcesAndUsagesValue {
        for (value in values) {
            value.usages += insn
        }
        return SourcesAndUsagesValue(
            sourceInterp.naryOperation(insn, values.map { it.sourceValue }),
            basicInterp.naryOperation(insn, values.map { it.typeInfo })
        )
    }

    override fun returnOperation(insn: AbstractInsnNode, value: SourcesAndUsagesValue, expected: SourcesAndUsagesValue) {
        value.usages += insn
    }

    override fun merge(value1: SourcesAndUsagesValue, value2: SourcesAndUsagesValue): SourcesAndUsagesValue {
        value1.usages += value2.usages
        value2.usages = value1.usages
        return SourcesAndUsagesValue(
            sourceInterp.merge(value1.sourceValue, value2.sourceValue),
            basicInterp.merge(value1.typeInfo, value2.typeInfo),
            value1.usages
        )
    }
}

// weak hash map is ok for a cache here because the MethodNodes are kept in memory in the Dekotlinify instance.
private val analyzerCache = WeakHashMap<MethodNode, Array<Frame<SourcesAndUsagesValue>>>()

fun MethodNode.analyzeSourcesAndUsages(owner: String): Array<Frame<SourcesAndUsagesValue>> {
    return analyzerCache.computeIfAbsent(this) { method ->
        Analyzer(SourcesAndUsagesInterpreter()).analyze(owner, method)
    }.also { frames ->
        assert(frames.size == instructions.size()) { "Forgot to call onModify on modified MethodNode" }
    }
}

fun MethodNode.onModify() {
    analyzerCache.remove(this)
}

object Analysis {
    fun getSafePushLocations(owner: String, method: MethodNode, insn: AbstractInsnNode, stackDepth: Int): List<AbstractInsnNode> {
        val frames = method.analyzeSourcesAndUsages(owner)
        val frame = frames[method.instructions.indexOf(insn)]
        if (stackDepth > frame.stackSize) return emptyList()
        if (stackDepth == 0) return listOf(insn)
        val stackIndex = frame.stackSize - stackDepth
        val sourcesAndUsages = frame.getStack(stackIndex)
        if (sourcesAndUsages.usages.size > 1) return emptyList()
        val sourcesProcessed = mutableSetOf<AbstractInsnNode>()
        var sourcesToProcess = sourcesAndUsages.sourceValue.insns.toMutableSet()
        var nextSourcesToProcess = mutableSetOf<AbstractInsnNode>()
        val pushLocations = mutableSetOf<AbstractInsnNode>()
        val extraUsages = mutableSetOf<AbstractInsnNode>()
        while (sourcesToProcess.isNotEmpty()) {
            sourcesProcessed += sourcesToProcess
            for (source in sourcesToProcess) {
                val sourceFrame = frames[method.instructions.indexOf(source)]
                if (sourceFrame.stackSize < stackIndex) return emptyList()
                for (i in 0 until stackIndex) {
                    if (frame.getStack(i) !== sourceFrame.getStack(i)) {
                        return emptyList()
                    }
                }
                if (sourceFrame.stackSize == stackIndex) {
                    pushLocations += source
                } else {
                    val sourceSources = sourceFrame.getStack(stackIndex)
                    extraUsages += sourceSources.usages
                    for (sourceSourceInsn in sourceSources.sourceValue.insns) {
                        if (sourceSourceInsn !in sourcesProcessed) {
                            nextSourcesToProcess += sourceSourceInsn
                        }
                    }
                }
            }

            sourcesToProcess.clear()
            val temp = sourcesToProcess
            sourcesToProcess = nextSourcesToProcess
            nextSourcesToProcess = temp
        }

        sourcesAndUsages.usages.firstOrNull()?.let { extraUsages.remove(it) }
        extraUsages.removeAll(sourcesProcessed)
        if (extraUsages.isNotEmpty()) return emptyList()

        return pushLocations.sortedBy { method.instructions.indexOf(it) }
    }

    fun getSafeModifyStackLocations(owner: String, method: MethodNode, insn: AbstractInsnNode, stackDepth: Int): List<AbstractInsnNode> {
        val frames = method.analyzeSourcesAndUsages(owner)
        val frame = frames[method.instructions.indexOf(insn)]
        if (stackDepth >= frame.stackSize) return emptyList()
        val sourcesAndUsages = frame.getStack(frame.stackSize - 1 - stackDepth)
        if (sourcesAndUsages.usages.size > 1) return emptyList()
        return sourcesAndUsages.sourceValue.insns.sortedBy { method.instructions.indexOf(it) }.map { it.next }
    }

    fun addFallbackUnderStackInsns(
        owner: String,
        method: MethodNode,
        insn: AbstractInsnNode,
        stackDepth: Int,
        toInsert: InsnList
    ): Array<VarInsnNode> {
        if (stackDepth == 0) return emptyArray()
        val frames = method.analyzeSourcesAndUsages(owner)
        val frame = frames[method.instructions.indexOf(insn)]
        if (stackDepth > frame.stackSize) return emptyArray()
        var varIndex = frame.locals
        val loadInsns = arrayOfNulls<VarInsnNode>(stackDepth)
        for (i in 0 until stackDepth) {
            val type = frame.getStack(frame.stackSize - 1 - i).typeInfo.type
            toInsert.add(VarInsnNode(type.getOpcode(Opcodes.ISTORE), varIndex))
            loadInsns[i] = VarInsnNode(type.getOpcode(Opcodes.ILOAD), varIndex)
            varIndex += type.size
        }
        return loadInsns.coerceNotNull()
    }
}
