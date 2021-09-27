# Dekotlinify

This project aims to take compiled Kotlin Java bytecode (compiled by the standard Kotlin compiler) and remove all references to the Kotlin standard library, so that the resulting jar can be used without the Kotlin standard library being on the classpath.

This means that you can write Kotlin source code without this disadvantage, as if you had written it in Java (but with nicer syntax). This does *not* mean that you can use random Kotlin libraries and expect them to work (this can only work if those libraries can also be properly dekotlinified). In particular, Kotlin reflection, Kotlin serialization, and anything that relies on such features are not going to work very well.

This project is very experimental, it's a fun toy project and I don't know how well it's going to work out. It is not functional yet.
