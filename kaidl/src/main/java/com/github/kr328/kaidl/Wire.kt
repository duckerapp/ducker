package com.github.kr328.kaidl

import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName

/**
 * How one type travels in a `Parcel`: an expression that reads a value and statements that write
 * one. The values are named, never computed: a nullable one is checked and then used.
 */
internal class Wire(
    val type: TypeName,
    val read: (parcel: String) -> CodeBlock,
    val write: (parcel: String, value: String) -> CodeBlock,
)

/** Something the generator does not write code for, and the place in the source to point at. */
internal class Refused(message: String, val at: KSNode) : Exception(message)

internal const val BINDER_INTERFACE = "com.github.kr328.kaidl.BinderInterface"

/**
 * The whole table of what travels; README.md repeats it for people. Everything is reached from
 * the type itself, never looked up by name: that is what lets KSP see which sources a generated
 * file depends on.
 */
internal fun KSType.wire(at: KSNode): Wire {
    val declaration = declaration as? KSClassDeclaration ?: refuse(at)
    val name = declaration.className()
    val arguments = arguments.map { (it.type ?: refuse(at)).resolve().wire(at) }
    val type = if (arguments.isEmpty()) name else name.parameterizedBy(arguments.map { it.type })

    val wire = when (name.canonicalName) {
        "kotlin.Unit" -> Wire(type, { CodeBlock.of("Unit") }, { _, _ -> CodeBlock.of("") })
        "kotlin.Boolean" -> Wire(
            type,
            { parcel -> CodeBlock.of("%N.readInt()·!=·0", parcel) },
            { parcel, value -> CodeBlock.of("%N.writeInt(if·(%N)·1·else·0)\n", parcel, value) },
        )
        "java.util.UUID" -> Wire(
            type,
            { parcel -> CodeBlock.of("%T(%N.readLong(),·%N.readLong())", type, parcel, parcel) },
            { parcel, value ->
                CodeBlock.of(
                    "%N.writeLong(%N.mostSignificantBits)\n%N.writeLong(%N.leastSignificantBits)\n",
                    parcel, value, parcel, value,
                )
            },
        )
        "kotlin.Pair" -> Wire(
            type,
            { parcel -> CodeBlock.of("%T(%L,·%L)", name, arguments[0].read(parcel), arguments[1].read(parcel)) },
            { parcel, value ->
                CodeBlock.builder()
                    .add("%N.%M·{·(first,·second)·->\n⇥", value, MemberName("kotlin", "let"))
                    .add(arguments[0].write(parcel, "first"))
                    .add(arguments[1].write(parcel, "second"))
                    .add("⇤}\n")
                    .build()
            },
        )
        "kotlin.collections.List" -> many(type, arguments[0], null)
        "kotlin.collections.Set" -> many(type, arguments[0], "toSet")
        "kotlin.collections.Map" -> Wire(
            type,
            { parcel ->
                CodeBlock.of(
                    "%T(%N.readInt())·{\n⇥%T(%L,·%L)\n⇤}.%M()",
                    LIST.parameterizedBy(PAIR.parameterizedBy(arguments[0].type, arguments[1].type)), parcel,
                    PAIR, arguments[0].read(parcel), arguments[1].read(parcel),
                    MemberName("kotlin.collections", "toMap"),
                )
            },
            { parcel, value ->
                CodeBlock.builder()
                    .add("%N.writeInt(%N.size)\n", parcel, value)
                    .add("%N.%M·{·(key,·value)·->\n⇥", value, FOR_EACH)
                    .add(arguments[0].write(parcel, "key"))
                    .add(arguments[1].write(parcel, "value"))
                    .add("⇤}\n")
                    .build()
            },
        )
        else -> SCALARS[name.canonicalName]?.let { (read, write) -> scalar(type, read, write) }
            ?: declaration.takeIf { it.typeParameters.isEmpty() }?.declared(name)
            ?: refuse(at)
    }

    return if (isMarkedNullable) wire.nullable() else wire
}

private fun KSType.refuse(at: KSNode): Nothing {
    // The full name where it says more: `Type` alone does not tell whose it is.
    val name = declaration.qualifiedName?.asString()?.takeIf { arguments.isEmpty() } ?: toString()

    throw Refused("$name does not travel between processes; the types that do are listed in kaidl/README.md", at)
}

/** `Parcel` methods that read and write a value whole: the reading call and the name of the writing one. */
private val SCALARS = mapOf(
    "kotlin.Int" to ("readInt()" to "writeInt"),
    "kotlin.Long" to ("readLong()" to "writeLong"),
    "kotlin.Float" to ("readFloat()" to "writeFloat"),
    "kotlin.Double" to ("readDouble()" to "writeDouble"),
    "kotlin.Byte" to ("readByte()" to "writeByte"),
    "kotlin.String" to ("readString()!!" to "writeString"),
    "kotlin.ByteArray" to ("createByteArray()!!" to "writeByteArray"),
    "kotlin.CharArray" to ("createCharArray()!!" to "writeCharArray"),
    "kotlin.BooleanArray" to ("createBooleanArray()!!" to "writeBooleanArray"),
    "kotlin.IntArray" to ("createIntArray()!!" to "writeIntArray"),
    "kotlin.LongArray" to ("createLongArray()!!" to "writeLongArray"),
    "kotlin.FloatArray" to ("createFloatArray()!!" to "writeFloatArray"),
    "kotlin.DoubleArray" to ("createDoubleArray()!!" to "writeDoubleArray"),
    "android.os.IBinder" to ("readStrongBinder()!!" to "writeStrongBinder"),
    "android.os.Bundle" to ("readBundle()!!" to "writeBundle"),
    "android.util.SparseBooleanArray" to ("readSparseBooleanArray()!!" to "writeSparseBooleanArray"),
)

private val PAIR = ClassName("kotlin", "Pair")
private val FOR_EACH = MemberName("kotlin.collections", "forEach")

private fun scalar(type: TypeName, read: String, write: String) = Wire(
    type,
    { parcel -> CodeBlock.of("%N.$read", parcel) },
    { parcel, value -> CodeBlock.of("%N.$write(%N)\n", parcel, value) },
)

/** A size and the elements. Read as a list; [convert] turns that into the collection asked for. */
private fun many(type: TypeName, element: Wire, convert: String?) = Wire(
    type,
    { parcel ->
        CodeBlock.builder()
            .add("%T(%N.readInt())·{\n⇥%L\n⇤}", LIST.parameterizedBy(element.type), parcel, element.read(parcel))
            .apply { if (convert != null) add(".%M()", MemberName("kotlin.collections", convert)) }
            .build()
    },
    { parcel, value ->
        CodeBlock.builder()
            .add("%N.writeInt(%N.size)\n", parcel, value)
            .add("%N.%M·{\n⇥", value, FOR_EACH)
            .add(element.write(parcel, "it"))
            .add("⇤}\n")
            .build()
    },
)

/** The types that say themselves how they travel. */
private fun KSClassDeclaration.declared(name: ClassName): Wire? = when {
    annotations.any {
        it.shortName.asString() == "BinderInterface" &&
            it.annotationType.resolve().declaration.qualifiedName?.asString() == BINDER_INTERFACE
    } -> Wire(
        name,
        { parcel -> CodeBlock.of("%N.readStrongBinder()!!.%M(%T::class)", parcel, MemberName(name.packageName, "unwrap"), name) },
        { parcel, value -> CodeBlock.of("%N.writeStrongBinder(%N.%M())\n", parcel, value, MemberName(name.packageName, "wrap")) },
    )
    getAllSuperTypes().any { it.declaration.qualifiedName?.asString() == "android.os.Parcelable" } -> Wire(
        name,
        { parcel -> CodeBlock.of("%T.CREATOR.createFromParcel(%N)!!", name, parcel) },
        { parcel, value -> CodeBlock.of("%N.writeToParcel(%N,·0)\n", value, parcel) },
    )
    classKind == ClassKind.ENUM_CLASS -> Wire(
        name,
        { parcel -> CodeBlock.of("%T.entries[%N.readInt()]", name, parcel) },
        { parcel, value -> CodeBlock.of("%N.writeInt(%N.ordinal)\n", parcel, value) },
    )
    else -> null
}

/** A flag, then the value if there is one. */
private fun Wire.nullable() = Wire(
    type.copy(nullable = true),
    { parcel -> CodeBlock.of("if·(%N.readInt()·!=·0)·%L·else·null", parcel, read(parcel)) },
    { parcel, value ->
        CodeBlock.builder()
            .add("if·(%N·!=·null)·{\n⇥", value)
            .add("%N.writeInt(1)\n", parcel)
            .add(write(parcel, value))
            .add("⇤}·else·{\n⇥")
            .add("%N.writeInt(0)\n", parcel)
            .add("⇤}\n")
            .build()
    },
)

internal fun KSClassDeclaration.className(): ClassName {
    val names = generateSequence<KSDeclaration>(this) { it.parentDeclaration }.map { it.simpleName.asString() }.toList()

    return ClassName(packageName.asString(), names.asReversed())
}
