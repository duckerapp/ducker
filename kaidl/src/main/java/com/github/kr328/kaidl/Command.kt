package com.github.kr328.kaidl

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.Modifier

/** One function of a `@BinderInterface`. Its transaction code is its place in the interface. */
internal class Command(
    val name: String,
    val suspend: Boolean,
    val parameters: List<Pair<String, Wire>>,
    val result: Wire,
)

/**
 * The commands of an interface in declaration order; `null` after every function that cannot be
 * generated has been reported.
 */
internal fun KSClassDeclaration.commands(logger: KSPLogger): List<Command>? {
    val commands = ArrayList<Command>()
    val names = HashSet<String>()
    var refused = false

    for (function in getDeclaredFunctions()) {
        try {
            val name = function.simpleName.asString()

            // The generated constant of a command carries its name.
            if (!names.add(name)) throw Refused("A @BinderInterface cannot have two functions named $name", function)

            commands += Command(
                name,
                Modifier.SUSPEND in function.modifiers,
                function.parameters.map { it.name!!.asString() to it.type.resolve().wire(it) },
                function.returnType!!.resolve().wire(function),
            )
        } catch (e: Refused) {
            logger.error(e.message!!, e.at)

            refused = true
        }
    }

    return commands.takeUnless { refused }
}
