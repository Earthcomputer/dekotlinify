package net.earthcomputer.dekotlinify.impl

import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.util.CheckClassAdapter

class DebugClassVisitor(cv: ClassVisitor) : ClassVisitor(Opcodes.ASM9, CheckClassAdapter(cv)) {
    override fun visit(
        version: Int,
        access: Int,
        name: String,
        signature: String?,
        superName: String?,
        interfaces: Array<out String>?
    ) {
        println("Visiting class $name")
        super.visit(version, access, name, signature, superName, interfaces)
    }

    override fun visitMethod(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        exceptions: Array<out String>?
    ): MethodVisitor {
        println("   Visiting method $name $descriptor")
        return super.visitMethod(access, name, descriptor, signature, exceptions)
    }
}
