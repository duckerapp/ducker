package com.github.kr328.kaidl

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.UNIT
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.joinToCode
import kotlin.reflect.KClass

private val BINDER = ClassName("android.os", "Binder")
private val IBINDER = ClassName("android.os", "IBinder")
private val PARCEL = ClassName("android.os", "Parcel")

/** The package of the runtime in `:service`. */
private const val RUNTIME = "com.github.kr328.kaidl"

private const val DESCRIPTOR = "DESCRIPTOR"

// Names of our own in the generated code. The underscore keeps them apart from the parameters
// of a command, which stand next to them under the names the interface gave.
private const val DATA = "_data"
private const val REPLY = "_reply"
private const val OUTCOME = "_outcome"
private const val RESULT = "_result"

private val Command.code get() = "TRANSACTION_$name"

/**
 * Everything written for one interface: `XDelegate` for the service, `XProxy` for the caller,
 * `X.wrap()` and `IBinder.unwrap(X::class)`. In templates `·` is a space where the line must not
 * be broken, `⇥` and `⇤` move the indent.
 */
internal fun glue(type: ClassName, commands: List<Command>): FileSpec {
    val delegate = ClassName(type.packageName, type.simpleName + "Delegate")
    val proxy = ClassName(type.packageName, type.simpleName + "Proxy")

    return FileSpec.builder(type.packageName, type.simpleName)
        .addFileComment("Generated for %L", type.canonicalName)
        .addAnnotation(
            AnnotationSpec.builder(Suppress::class)
                .addMember("%S, %S", "NAME_SHADOWING", "UNNECESSARY_NOT_NULL_ASSERTION")
                .build()
        )
        .addType(
            TypeSpec.classBuilder(delegate)
                .addModifiers(KModifier.OPEN)
                .primaryConstructor(FunSpec.constructorBuilder().addParameter("impl", type).build())
                .superclass(BINDER)
                .addSuperinterface(type, "impl")
                .addFunction(
                    FunSpec.builder("getInterfaceDescriptor")
                        .addModifiers(KModifier.OVERRIDE)
                        .returns(STRING)
                        .addCode("return·%N\n", DESCRIPTOR)
                        .build()
                )
                .addFunction(
                    FunSpec.builder("onTransact")
                        .addModifiers(KModifier.OVERRIDE)
                        .addParameter("code", INT)
                        .addParameter(DATA, PARCEL)
                        .addParameter(REPLY, PARCEL.copy(nullable = true))
                        .addParameter("flags", INT)
                        .returns(BOOLEAN)
                        .addCode(onTransact(commands))
                        .build()
                )
                .addType(
                    TypeSpec.companionObjectBuilder()
                        .addProperty(
                            PropertySpec.builder(DESCRIPTOR, STRING, KModifier.CONST)
                                .initializer("%S", type.canonicalName)
                                .build()
                        )
                        .addProperties(commands.mapIndexed { index, command ->
                            PropertySpec.builder(command.code, INT, KModifier.CONST)
                                .initializer("%T.FIRST_CALL_TRANSACTION·+·%L", IBINDER, index)
                                .build()
                        })
                        .build()
                )
                .build()
        )
        .addType(
            TypeSpec.classBuilder(proxy)
                .addSuperinterface(type)
                .primaryConstructor(FunSpec.constructorBuilder().addParameter("remote", IBINDER).build())
                .addProperty(PropertySpec.builder("remote", IBINDER).initializer("remote").build())
                .addFunctions(commands.map { it.proxy(delegate) })
                .build()
        )
        .addFunction(
            FunSpec.builder("wrap")
                .receiver(type)
                .returns(IBINDER)
                .addCode("return·if·(this·is·%T)·this·else·%T(this)\n", IBINDER, delegate)
                .build()
        )
        .addFunction(
            FunSpec.builder("unwrap")
                .receiver(IBINDER)
                // Only tells this `unwrap` from those of the other interfaces.
                .addParameter("c", KClass::class.asClassName().parameterizedBy(type))
                .returns(type)
                .addCode("return·if·(this·is·%T)·this·else·%T(this)\n", type, proxy)
                .build()
        )
        .build()
}

/**
 * The service side. One `catch` for all commands: whatever one of them throws, while its
 * arguments are read or in the implementation, goes back to the caller as its reply.
 */
private fun onTransact(commands: List<Command>): CodeBlock {
    val body = CodeBlock.builder().add("try·{\n⇥when·(code)·{\n⇥")

    for (command in commands) {
        body.add("%N·->·{\n⇥", command.code)
            .add("%N·?:·return·false\n", REPLY)
            .add("%N.enforceInterface(%N)\n", DATA, DESCRIPTOR)

        for ((name, wire) in command.parameters) {
            body.add("val·%N:·%T·=·%L\n", name, wire.type, wire.read(DATA))
        }

        if (command.suspend) {
            // The arguments are read here, while the caller waits; the body runs after it has been let go.
            body.add("%M(%N,·%N)·{·%N·->\n⇥", MemberName(RUNTIME, "launchSuspend"), DATA, REPLY, OUTCOME)
                .add(command.answer(OUTCOME))
                .add("⇤}\n")
        } else {
            body.add(command.answer(REPLY))
        }

        body.add("⇤}\n")
    }

    return body
        .add("else·->·return·super.onTransact(code,·%N,·%N,·flags)\n", DATA, REPLY)
        .add("⇤}\n")
        .add("⇤}·catch·(e:·Exception)·{\n⇥")
        .add("(%N·?:·throw·e).%M(e)\n", REPLY, MemberName(RUNTIME, "writeFailure"))
        .add("⇤}\n")
        .add("return·true\n")
        .build()
}

/** Calls the implementation and writes what it returned into [parcel]. */
private fun Command.answer(parcel: String): CodeBlock {
    val call = CodeBlock.of("%N(%L)", name, parameters.map { CodeBlock.of("%N", it.first) }.joinToCode())

    return CodeBlock.builder()
        .apply { if (result.type == UNIT) add("%L\n", call) else add("val·%N:·%T·=·%L\n", RESULT, result.type, call) }
        .add("%N.writeNoException()\n", parcel)
        .add(result.write(parcel, RESULT))
        .build()
}

/** The caller's side of one command. */
private fun Command.proxy(delegate: ClassName): FunSpec {
    val body = CodeBlock.builder()
        .add("val·%N·=·%T.obtain()\n", DATA, PARCEL)
        .add("val·%N·=·%T.obtain()\n", REPLY, PARCEL)
        .add("try·{\n⇥")
        .add("%N.writeInterfaceToken(%T.%N)\n", DATA, delegate, DESCRIPTOR)

    for ((name, wire) in parameters) {
        body.add(wire.write(DATA, name))
    }

    body.add(
        "this.remote.%M(%T.%N,·%N,·%N)\n",
        MemberName(RUNTIME, if (suspend) "suspendCall" else "call"), delegate, code, DATA, REPLY,
    )

    if (result.type != UNIT) body.add("return·%L\n", result.read(REPLY))

    body.add("⇤}·finally·{\n⇥")
        .add("%N.recycle()\n", DATA)
        .add("%N.recycle()\n", REPLY)
        .add("⇤}\n")

    return FunSpec.builder(name)
        .addModifiers(listOfNotNull(KModifier.OVERRIDE, KModifier.SUSPEND.takeIf { suspend }))
        .addParameters(parameters.map { (name, wire) -> ParameterSpec(name, wire.type) })
        .returns(result.type)
        .addCode(body.build())
        .build()
}
