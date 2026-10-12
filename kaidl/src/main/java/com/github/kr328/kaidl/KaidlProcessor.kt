package com.github.kr328.kaidl

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.validate

class KaidlProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {
    override fun process(resolver: Resolver): List<KSAnnotated> {
        // What refers to code another processor has yet to write waits for the next round.
        val (ready, later) = resolver.getSymbolsWithAnnotation(BINDER_INTERFACE)
            .partition { it.validate(enableNewFeatures = true) }

        for (symbol in ready) {
            if (symbol is KSClassDeclaration && symbol.classKind == ClassKind.INTERFACE) {
                generate(symbol)
            } else {
                logger.error("@BinderInterface is for interfaces", symbol)
            }
        }

        return later
    }

    private fun generate(declaration: KSClassDeclaration) {
        val commands = declaration.commands(logger) ?: return
        val type = declaration.className()

        // Isolating: the file follows this one source and what was resolved from it, so a
        // change elsewhere does not write it again.
        val dependencies = Dependencies(false, declaration.containingFile!!)

        codeGenerator.createNewFile(dependencies, type.packageName, type.simpleName).writer().use {
            glue(type, commands).writeTo(it)
        }
    }
}
