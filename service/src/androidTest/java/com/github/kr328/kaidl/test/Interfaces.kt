package com.github.kr328.kaidl.test

import android.os.Bundle
import android.os.IBinder
import android.os.Parcel
import android.os.Parcelable
import android.util.SparseBooleanArray
import com.github.kr328.kaidl.BinderInterface
import java.util.UUID

/**
 * Every type shape the generator knows, as an ordinary and as a `suspend` command, and the
 * commands the failure cases of [BinderExchangeTest] need. Nothing in the app uses it.
 */
@BinderInterface
interface IExchange {
    fun nothing()
    fun echoInt(v: Int): Int
    fun echoLong(v: Long): Long
    fun echoFloat(v: Float): Float
    fun echoDouble(v: Double): Double
    fun echoByte(v: Byte): Byte
    fun echoBoolean(v: Boolean): Boolean
    fun echoString(v: String): String

    fun echoByteArray(v: ByteArray): ByteArray
    fun echoCharArray(v: CharArray): CharArray
    fun echoBooleanArray(v: BooleanArray): BooleanArray
    fun echoIntArray(v: IntArray): IntArray
    fun echoLongArray(v: LongArray): LongArray
    fun echoFloatArray(v: FloatArray): FloatArray
    fun echoDoubleArray(v: DoubleArray): DoubleArray

    fun echoBinder(v: IBinder): IBinder
    fun echoBundle(v: Bundle): Bundle
    fun echoSparseBooleanArray(v: SparseBooleanArray): SparseBooleanArray

    fun echoList(v: List<String>): List<String>
    fun echoSet(v: Set<Long>): Set<Long>
    fun echoSets(v: List<Set<Long>>): List<Set<Long>>
    fun echoPair(v: Pair<String, Long?>): Pair<String, Long?>
    fun echoMap(v: Map<String, Long>): Map<String, Long>
    fun echoNested(v: Map<String, List<Set<Long>>>): Map<String, List<Set<Long>>>

    /** Two containers of each kind in one command: what the glue declares for one must not meet the other's. */
    fun merge(a: Map<String, Long>, b: Map<String, Long>, c: Pair<String, Long>, d: Pair<String, Long>): Map<String, Long>

    fun echoModel(v: Model): Model
    fun echoModels(v: List<Model>): List<Model>
    fun echoEnum(v: Level): Level
    fun echoNestedEnum(v: Model.Kind): Model.Kind
    fun echoUuid(v: UUID): UUID

    fun echoNullableInt(v: Int?): Int?
    fun echoNullableString(v: String?): String?
    fun echoNullableModel(v: Model?): Model?
    fun echoNullableEnum(v: Level?): Level?
    fun echoNullableUuid(v: UUID?): UUID?
    fun echoNullableMap(v: Map<String, Long?>?): Map<String, Long?>?

    /** Several arguments of different kinds in one command. */
    fun describe(count: Int, name: String, model: Model?, levels: List<Level>): String

    /** Parameters named like what the glue itself has in scope. */
    fun echoNames(code: Int, data: String, reply: String, flags: Int, remote: String, impl: String, it: String): String
    suspend fun suspendEchoNames(code: Int, data: String, reply: String, flags: Int, remote: String, impl: String, it: String): String

    fun tell(observer: IObserver, text: String)
    fun tellAll(observers: List<IObserver>, text: String)
    fun peer(name: String): IPeer

    suspend fun suspendNothing()
    suspend fun suspendEchoInt(v: Int): Int
    suspend fun suspendEchoString(v: String): String
    suspend fun suspendEchoModels(v: List<Model>): List<Model>
    suspend fun suspendEchoNullableModel(v: Model?): Model?
    suspend fun suspendEchoUuid(v: UUID): UUID
    suspend fun suspendPeer(name: String): IPeer

    /** Default arguments and an optional observer, as `IProfileManager.commit` has them. */
    suspend fun suspendTell(text: String = "default", observer: IObserver? = null): Boolean

    fun fail(failure: Failure): Int
    suspend fun suspendFail(failure: Failure): Int

    fun bytes(size: Int): ByteArray
    suspend fun suspendBytes(size: Int): ByteArray

    /** Replies when [openTheGate] is called: any number of commands then end at one moment. */
    suspend fun suspendBytesAtTheGate(size: Int): ByteArray

    /** How many commands wait at the gate. */
    fun atTheGate(): Int
    fun openTheGate()

    /** `bytes` under the key "bytes" and, if asked, a binder under "binder". */
    suspend fun suspendBundle(bytes: Int, binder: Boolean): Bundle

    suspend fun suspendEchoAfter(v: Int, millis: Long): Int

    /** Never returns: it ends by cancellation or with the process. */
    suspend fun suspendHang()

    /** How many [suspendHang] bodies have started and how many of them were cancelled. */
    fun hangs(): IntArray

    /** Turns the roles around: this side becomes the caller of [peer]'s `suspendHang`. */
    fun callHang(peer: IExchange)

    fun sleep(millis: Long)
    fun pid(): Int
}

@BinderInterface
fun interface IObserver {
    fun onEvent(text: String)
}

@BinderInterface
interface IPeer {
    fun name(): String
}

enum class Level { Debug, Info, Warning }

enum class Failure {
    IllegalState, IllegalArgumentSubclass, Security, NullPointer, UnsupportedOperation,
    Io, OwnRuntime, Cancellation
}

data class Model(val id: Int, val name: String, val kind: Kind, val tags: List<String>) : Parcelable {
    enum class Kind { First, Second, Third }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(id)
        parcel.writeString(name)
        parcel.writeInt(kind.ordinal)
        parcel.writeStringList(tags)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<Model> {
        override fun createFromParcel(parcel: Parcel): Model {
            return Model(
                parcel.readInt(),
                parcel.readString()!!,
                Kind.values()[parcel.readInt()],
                parcel.createStringArrayList()!!,
            )
        }

        override fun newArray(size: Int): Array<Model?> = arrayOfNulls(size)
    }
}
