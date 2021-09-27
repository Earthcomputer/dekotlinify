package net.earthcomputer.dekotlinify.impl

import net.earthcomputer.dekotlinify.Dekotlinify
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Opcodes.ASM9
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.IincInsnNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.IntInsnNode
import org.objectweb.asm.tree.InvokeDynamicInsnNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TypeInsnNode
import org.objectweb.asm.tree.VarInsnNode
import org.objectweb.asm.tree.analysis.Analyzer
import org.objectweb.asm.tree.analysis.BasicInterpreter
import org.objectweb.asm.tree.analysis.BasicValue
import org.objectweb.asm.tree.analysis.Frame
import org.objectweb.asm.tree.analysis.Interpreter
import org.objectweb.asm.tree.analysis.SourceInterpreter
import org.objectweb.asm.tree.analysis.SourceValue
import org.objectweb.asm.tree.analysis.Value
import org.objectweb.asm.util.Textifier
import org.objectweb.asm.util.TraceMethodVisitor
import java.io.FileWriter
import java.io.PrintWriter
import java.util.WeakHashMap

data class SourcesAndUsagesValue(
    val sourceValue: SourceValue,
    val typeInfo: BasicValue,
    var usages: MutableSet<AbstractInsnNode> = mutableSetOf(),
    val constantValue: Any? = null
) : Value {
    override fun getSize() = sourceValue.getSize()
}

class SourcesAndUsagesInterpreter : Interpreter<SourcesAndUsagesValue>(ASM9) {
    private val sourceInterp = SourceInterpreter()
    private val basicInterp = BasicInterpreter()

    override fun newValue(type: Type?): SourcesAndUsagesValue? {
        val typeInfo = basicInterp.newValue(type) ?: return null
        return SourcesAndUsagesValue(
            sourceInterp.newValue(type),
            typeInfo
        )
    }

    override fun newOperation(insn: AbstractInsnNode): SourcesAndUsagesValue? {
        val typeInfo = basicInterp.newOperation(insn) ?: return null
        val constantValue = when (insn.opcode) {
            in Opcodes.ICONST_M1..Opcodes.ICONST_5 -> insn.opcode - Opcodes.ICONST_0
            in Opcodes.FCONST_0..Opcodes.FCONST_2 -> (insn.opcode - Opcodes.FCONST_0).toFloat()
            Opcodes.ACONST_NULL -> NullValue
            Opcodes.LCONST_0, Opcodes.LCONST_1 -> (insn.opcode - Opcodes.LCONST_0).toLong()
            Opcodes.DCONST_0, Opcodes.DCONST_1 -> (insn.opcode - Opcodes.DCONST_0).toDouble()
            Opcodes.BIPUSH, Opcodes.SIPUSH -> (insn as IntInsnNode).operand
            Opcodes.LDC -> (insn as LdcInsnNode).cst
            Opcodes.NEW -> {
                if ((insn as TypeInsnNode).desc == "java/lang/StringBuilder") {
                    StringBuilderValue(StringBuilder())
                } else {
                    null
                }
            }
            else -> null
        }
        return SourcesAndUsagesValue(
            sourceInterp.newOperation(insn),
            typeInfo,
            constantValue = constantValue
        )
    }

    override fun copyOperation(insn: AbstractInsnNode, value: SourcesAndUsagesValue): SourcesAndUsagesValue? {
        value.usages += insn
        val typeInfo = basicInterp.copyOperation(insn, value.typeInfo) ?: return null
        val cstValue = if (value.constantValue is StringBuilderValue) {
            var prevInsn = insn.previous
            while (prevInsn != null && prevInsn.opcode == -1) {
                prevInsn = prevInsn.previous
            }
            if (prevInsn != null && prevInsn.opcode == Opcodes.NEW && (prevInsn as TypeInsnNode).desc == "java/lang/StringBuilder") {
                value.constantValue
            } else {
                StringBuilderValue(StringBuilder(value.constantValue.sb))
            }
        } else {
            value.constantValue
        }
        return SourcesAndUsagesValue(
            SourceValue(value.size, insn),
            typeInfo,
            constantValue = cstValue
        )
    }

    override fun unaryOperation(insn: AbstractInsnNode, value: SourcesAndUsagesValue): SourcesAndUsagesValue? {
        value.usages += insn
        val typeInfo = basicInterp.unaryOperation(insn, value.typeInfo) ?: return null
        val constantValue = if (value.constantValue != null) {
            when (insn.opcode) {
                Opcodes.INEG -> (value.constantValue as? Int)?.unaryMinus()
                Opcodes.LNEG -> (value.constantValue as? Long)?.unaryMinus()
                Opcodes.FNEG -> (value.constantValue as? Float)?.unaryMinus()
                Opcodes.DNEG -> (value.constantValue as? Double)?.unaryMinus()
                Opcodes.IINC -> (value.constantValue as? Int)?.plus((insn as IincInsnNode).incr)
                Opcodes.I2L -> (value.constantValue as? Int)?.toLong()
                Opcodes.I2F -> (value.constantValue as? Int)?.toFloat()
                Opcodes.I2D -> (value.constantValue as? Int)?.toDouble()
                Opcodes.L2I -> (value.constantValue as? Long)?.toInt()
                Opcodes.L2F -> (value.constantValue as? Long)?.toFloat()
                Opcodes.L2D -> (value.constantValue as? Long)?.toDouble()
                Opcodes.F2I -> (value.constantValue as? Float)?.toInt()
                Opcodes.F2L -> (value.constantValue as? Float)?.toLong()
                Opcodes.F2D -> (value.constantValue as? Float)?.toDouble()
                Opcodes.D2I -> (value.constantValue as? Double)?.toInt()
                Opcodes.D2L -> (value.constantValue as? Double)?.toLong()
                Opcodes.D2F -> (value.constantValue as? Double)?.toFloat()
                Opcodes.I2B -> (value.constantValue as? Int)?.toByte()?.toInt()
                Opcodes.I2C -> (value.constantValue as? Int)?.toChar()?.code
                Opcodes.I2S -> (value.constantValue as? Int)?.toShort()?.toInt()
                else -> null
            }
        } else {
            null
        }
        return SourcesAndUsagesValue(
            sourceInterp.unaryOperation(insn, value.sourceValue),
            typeInfo,
            constantValue = constantValue
        )
    }

    override fun binaryOperation(insn: AbstractInsnNode, value1: SourcesAndUsagesValue, value2: SourcesAndUsagesValue): SourcesAndUsagesValue? {
        value1.usages += insn
        value2.usages += insn
        val typeInfo = basicInterp.binaryOperation(insn, value1.typeInfo, value2.typeInfo) ?: return null
        val constantValue = if (value1.constantValue != null && value2.constantValue != null) {
            when (insn.opcode) {
                Opcodes.IADD -> (value2.constantValue as? Int)?.let { (value1.constantValue as? Int)?.plus(it) }
                Opcodes.LADD -> (value2.constantValue as? Long)?.let { (value1.constantValue as? Long)?.plus(it) }
                Opcodes.FADD -> (value2.constantValue as? Float)?.let { (value1.constantValue as? Float)?.plus(it) }
                Opcodes.DADD -> (value2.constantValue as? Double)?.let { (value1.constantValue as? Double)?.plus(it) }
                Opcodes.ISUB -> (value2.constantValue as? Int)?.let { (value1.constantValue as? Int)?.minus(it) }
                Opcodes.LSUB -> (value2.constantValue as? Long)?.let { (value1.constantValue as? Long)?.minus(it) }
                Opcodes.FSUB -> (value2.constantValue as? Float)?.let { (value1.constantValue as? Float)?.minus(it) }
                Opcodes.DSUB -> (value2.constantValue as? Double)?.let { (value1.constantValue as? Double)?.minus(it) }
                Opcodes.IMUL -> (value2.constantValue as? Int)?.let { (value1.constantValue as? Int)?.times(it) }
                Opcodes.LMUL -> (value2.constantValue as? Long)?.let { (value1.constantValue as? Long)?.times(it) }
                Opcodes.FMUL -> (value2.constantValue as? Float)?.let { (value1.constantValue as? Float)?.times(it) }
                Opcodes.DMUL -> (value2.constantValue as? Double)?.let { (value1.constantValue as? Double)?.times(it) }
                Opcodes.IDIV -> (value2.constantValue as? Int)?.let { (value1.constantValue as? Int)?.div(it) }
                Opcodes.LDIV -> (value2.constantValue as? Long)?.let { (value1.constantValue as? Long)?.div(it) }
                Opcodes.FDIV -> (value2.constantValue as? Float)?.let { (value1.constantValue as? Float)?.div(it) }
                Opcodes.DDIV -> (value2.constantValue as? Double)?.let { (value1.constantValue as? Double)?.div(it) }
                Opcodes.IREM -> (value2.constantValue as? Int)?.let { (value1.constantValue as? Int)?.rem(it) }
                Opcodes.LREM -> (value2.constantValue as? Long)?.let { (value1.constantValue as? Long)?.rem(it) }
                Opcodes.FREM -> (value2.constantValue as? Float)?.let { (value1.constantValue as? Float)?.rem(it) }
                Opcodes.DREM -> (value2.constantValue as? Double)?.let { (value1.constantValue as? Double)?.rem(it) }
                Opcodes.ISHL -> (value2.constantValue as? Int)?.let { (value1.constantValue as? Int)?.shl(it) }
                Opcodes.LSHL -> (value2.constantValue as? Int)?.let { (value1.constantValue as? Long)?.shl(it) }
                Opcodes.ISHR -> (value2.constantValue as? Int)?.let { (value1.constantValue as? Int)?.shr(it) }
                Opcodes.LSHR -> (value2.constantValue as? Int)?.let { (value1.constantValue as? Long)?.shr(it) }
                Opcodes.IUSHR -> (value2.constantValue as? Int)?.let { (value1.constantValue as? Int)?.ushr(it) }
                Opcodes.LUSHR -> (value2.constantValue as? Int)?.let { (value1.constantValue as? Long)?.ushr(it) }
                Opcodes.IAND -> (value2.constantValue as? Int)?.let { (value1.constantValue as? Int)?.and(it) }
                Opcodes.LAND -> (value2.constantValue as? Long)?.let { (value1.constantValue as? Long)?.and(it) }
                Opcodes.IOR -> (value2.constantValue as? Int)?.let { (value1.constantValue as? Int)?.or(it) }
                Opcodes.LOR -> (value2.constantValue as? Long)?.let { (value1.constantValue as? Long)?.or(it) }
                Opcodes.IXOR -> (value2.constantValue as? Int)?.let { (value1.constantValue as? Int)?.xor(it) }
                Opcodes.LXOR -> (value2.constantValue as? Long)?.let { (value1.constantValue as? Long)?.xor(it) }
                Opcodes.LCMP -> (value2.constantValue as? Long)?.let { v2 -> (value1.constantValue as? Long)?.let { v1 -> if (v1 < v2) -1 else if (v1 > v2) 1 else 0 } }
                Opcodes.FCMPL -> (value2.constantValue as? Float)?.let { v2 -> (value1.constantValue as? Float)?.let { v1 -> if (v1 < v2) -1 else if (v1 > v2) 1 else if (v1 == v2) 0 else -1 } }
                Opcodes.FCMPG -> (value2.constantValue as? Float)?.let { v2 -> (value1.constantValue as? Float)?.let { v1 -> if (v1 < v2) -1 else if (v1 > v2) 1 else if (v1 == v2) 0 else 1 } }
                Opcodes.DCMPL -> (value2.constantValue as? Double)?.let { v2 -> (value1.constantValue as? Double)?.let { v1 -> if (v1 < v2) -1 else if (v1 > v2) 1 else if (v1 == v2) 0 else -1 } }
                Opcodes.DCMPG -> (value2.constantValue as? Double)?.let { v2 -> (value1.constantValue as? Double)?.let { v1 -> if (v1 < v2) -1 else if (v1 > v2) 1 else if (v1 == v2) 0 else 1 } }
                else -> null
            }
        } else {
            null
        }
        return SourcesAndUsagesValue(
            sourceInterp.binaryOperation(insn, value1.sourceValue, value2.sourceValue),
            typeInfo,
            constantValue = constantValue
        )
    }

    override fun ternaryOperation(
        insn: AbstractInsnNode,
        value1: SourcesAndUsagesValue,
        value2: SourcesAndUsagesValue,
        value3: SourcesAndUsagesValue
    ): SourcesAndUsagesValue? {
        value1.usages += insn
        value2.usages += insn
        value2.usages += insn
        val typeInfo = basicInterp.ternaryOperation(insn, value1.typeInfo, value2.typeInfo, value3.typeInfo) ?: return null
        return SourcesAndUsagesValue(
            sourceInterp.ternaryOperation(insn, value1.sourceValue, value2.sourceValue, value3.sourceValue),
            typeInfo
        )
    }

    override fun naryOperation(insn: AbstractInsnNode, values: List<SourcesAndUsagesValue>): SourcesAndUsagesValue? {
        for (value in values) {
            value.usages += insn
        }
        // basic string concatenation interpretation
        val constantValue: Any? = if (values.all { it.constantValue != null }) {
            if (insn is MethodInsnNode && insn.owner == "java/lang/StringBuilder") {
                val sb = "Ljava/lang/StringBuilder;"
                when (insn.name) {
                    "<init>" -> when (insn.desc) {
                        "(Ljava/lang/String;)V", "(Ljava/lang/CharSequence;)V" -> {
                            val param = values.getOrNull(1)?.constantValue
                            if (param != null) {
                                (values.firstOrNull()?.constantValue as? StringBuilderValue)?.append(param)
                            }
                            null
                        }
                        else -> null
                    }
                    "toString" -> (values.firstOrNull()?.constantValue as? StringBuilderValue)?.toString()
                    "append" -> when (insn.desc) {
                        "(Ljava/lang/Object;)$sb",
                        "(Ljava/lang/String;)$sb",
                        "(Ljava/lang/CharSequence;)$sb",
                        "(I)$sb",
                        "(J)$sb",
                        "(F)$sb",
                        "(D)$sb",
                        "(S)$sb" -> when (val arg = values.getOrNull(1)?.constantValue) {
                            is Any -> (values.firstOrNull()?.constantValue as? StringBuilderValue)?.append(arg)
                            else -> null
                        }
                        "(Z)$sb" -> (values.getOrNull(1)?.constantValue as? Int)?.let { (values.firstOrNull()?.constantValue as? StringBuilderValue)?.append((it != 0).toString()) }
                        "(C)$sb" -> (values.getOrNull(1)?.constantValue as? Int)?.let { (values.firstOrNull()?.constantValue as? StringBuilderValue)?.append(it.toChar().toString()) }
                        else -> null
                    }
                    else -> null
                }
            } else if (insn is InvokeDynamicInsnNode && insn.bsm.owner == "java/lang/invoke/StringConcatFactory") {
                val args = Type.getArgumentTypes(insn.desc)
                when (insn.bsm.name) {
                    "makeConcat" -> values.withIndex().map { (index, value) ->
                        val cst = value.constantValue!!
                        when (args[index]) {
                            Type.BOOLEAN_TYPE -> cst != 0
                            Type.CHAR_TYPE -> (cst as Int).toChar()
                            else -> cst
                        }
                    }.joinToString("")
                    "makeConcatWithConstants" -> {
                        val string = StringBuilder(insn.bsmArgs[0] as String)
                        var i = 0
                        var argIndex = 0
                        var cstIndex = 1
                        while (i < string.length) {
                            if (string[i] == '\u0001') {
                                val cst = values[argIndex].constantValue!!.let {
                                    when (args[argIndex]) {
                                        Type.BOOLEAN_TYPE -> it != 0
                                        Type.CHAR_TYPE -> (it as Int).toChar()
                                        else -> it
                                    }
                                }
                                val str = cst.toString()
                                string.replace(i, i + 1, str)
                                i += str.length - 1
                                argIndex++
                            } else if (string[i] == '\u0002') {
                                val str = insn.bsmArgs[cstIndex].toString()
                                string.replace(i, i + 1, str)
                                i += str.length - 1
                                cstIndex++
                            }
                            i++
                        }
                        string.toString()
                    }
                    else -> null
                }
            } else {
                null
            }
        } else {
            null
        }
        val typeInfo = basicInterp.naryOperation(insn, values.map { it.typeInfo }) ?: return null
        return SourcesAndUsagesValue(
            sourceInterp.naryOperation(insn, values.map { it.sourceValue }),
            typeInfo,
            constantValue = constantValue
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
            value1.usages,
            constantValue = value1.constantValue?.takeIf { it == value2.constantValue }
        )
    }
}

// weak hash map is ok for a cache here because the MethodNodes are kept in memory in the Dekotlinify instance.
private val analyzerCache = WeakHashMap<MethodNode, Array<Frame<SourcesAndUsagesValue>>>()

fun MethodNode.analyzeSourcesAndUsages(owner: String): Array<Frame<SourcesAndUsagesValue>> {
    return analyzerCache.computeIfAbsent(this) { method ->
        val analyzer = Analyzer(SourcesAndUsagesInterpreter())
        try {
            if (method.maxStack == -1) {
                analyzer.analyzeAndComputeMaxs(owner, method)
            } else {
                analyzer.analyze(owner, method)
            }
        } catch (e: Throwable) {
            if (Dekotlinify.shouldUseDebug) {
                val textifier = Textifier()
                method.accept(TraceMethodVisitor(textifier))
                println("Analysis failed, outputting to failed-analysis.txt")
                FileWriter("failed-analysis.txt").use {
                    textifier.print(PrintWriter(it))
                }
            }
            throw e
        }
    }.also { frames ->
        assert(frames.size == instructions.size()) { "Forgot to call onModify on modified MethodNode" }
    }
}

fun MethodNode.onModify() {
    analyzerCache.remove(this)
    this.maxStack = -1
}

object NullValue {
    override fun toString() = "null"
}

private class StringBuilderValue(val sb: StringBuilder) {
    private var cachedString: String? = null

    fun append(other: Any): StringBuilderValue {
        sb.append(other)
        cachedString = null
        return this
    }

    override fun toString(): String {
        return cachedString ?: sb.toString().also { cachedString = it }
    }

    override fun hashCode(): Int {
        return toString().hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other !is StringBuilderValue) return false
        return toString() == other.toString()
    }
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

    fun getConstantStackValue(owner: String, method: MethodNode, insn: AbstractInsnNode, stackDepth: Int): Any? {
        val frames = method.analyzeSourcesAndUsages(owner)
        val frame = frames[method.instructions.indexOf(insn)]
        if (stackDepth >= frame.stackSize) return null
        val cst = frame.getStack(frame.stackSize - 1 - stackDepth).constantValue
        return if (cst is StringBuilderValue) {
            cst.sb
        } else {
            cst
        }
    }

    fun getNextFreeLocal(owner: String, method: MethodNode, insn: AbstractInsnNode): Int {
        val frames = method.analyzeSourcesAndUsages(owner)
        val frame = frames[method.instructions.indexOf(insn)]
        return frame.locals
    }
}
