# kaidl

The generator of the Binder glue between the interface process and the service process. It is a
KSP processor: for every Kotlin interface marked `@BinderInterface` it writes

- `XDelegate` — the service side: a `Binder` that unpacks the arguments, calls the implementation
  and packs the result;
- `XProxy` — the caller's side: the same interface on top of an `IBinder`;
- `X.wrap()` and `IBinder.unwrap(X::class)` — from an implementation to a binder and back. Inside
  one process both return the object itself, and no parcel is involved.

The generator runs inside the build of `:service` and never reaches the APK. Its runtime — the two
annotations and the transport of `suspend` commands — is three files in
`service/src/main/java/com/github/kr328/kaidl/`. The interfaces are in
`service/src/main/java/com/github/kr328/clash/service/remote/`.

## Origin

[Kr328/kaidl](https://github.com/Kr328/kaidl) by Kr328, MIT — `LICENSE` in this directory covers
the generator and the three runtime files. The source is commit `7710f43`, which is version 1.15,
the one Clash Meta for Android takes as a binary. It lives in the tree because the library has had
no commit since 2021 and its binaries were left on a single third-party mirror.

Changed from that source: `FileSpec.Builder.addComment` became `addFileComment`, because the old
name no longer compiles against KotlinPoet 2. The runtime files are unchanged. The generated code
is byte for byte what the 1.15 binary produced.

KotlinPoet stays at the version Room's compiler asks for (`gradle/libs.versions.toml`): the two
processors share one classpath in the build of `:service`.

## Wire protocol

Both processes always run the same APK, so nothing here has to survive a change of an interface.

The descriptor of an interface is its fully qualified name. The transaction code of a command is
`IBinder.FIRST_CALL_TRANSACTION` plus its position in the interface, counted from zero in
declaration order.

**An ordinary command** is one synchronous transaction.

- Request: the interface token (`writeInterfaceToken`), then the arguments in order.
- Reply: `writeNoException()`, then the result.

The glue does not catch what the implementation throws; Android itself sends back the exception
types `Parcel.writeException` knows.

**A `suspend` command** takes three steps.

1. The caller sends the request with one more value after the arguments — a binder that will
   receive the outcome. The service starts the body in its own scope on `Dispatchers.IO` and
   answers at once with `writeNoException()` and a binder for cancelling.
2. When the body ends, the service sends the outcome to the caller's binder in a one-way
   transaction: code `FIRST_CALL_TRANSACTION` with `writeNoException()` and the result, or with an
   `IllegalArgumentException` carrying the message of whatever the body threw; code
   `FIRST_CALL_TRANSACTION + 1` with nothing else if the body was cancelled.
3. The caller copies that parcel into the reply and continues as after an ordinary command.

Cancelling the caller sends `FIRST_CALL_TRANSACTION` to the binder for cancelling, and the service
cancels the body. Each side also watches the other's binder: the death of the caller cancels the
body, the death of the service ends the call with `DeadObjectException`.

**Values**

| Kotlin type | In the parcel |
|---|---|
| `Int`, `Long`, `Float`, `Double`, `Byte`, `String` | `writeInt` … `writeString` |
| `Boolean` | an `Int`, 1 or 0 |
| `Unit` | nothing |
| `ByteArray`, `CharArray`, `BooleanArray`, `IntArray`, `LongArray`, `FloatArray`, `DoubleArray` | `writeByteArray` … `writeDoubleArray` |
| `IBinder`, `Bundle`, `SparseBooleanArray` | `writeStrongBinder`, `writeBundle`, `writeSparseBooleanArray` |
| `List<T>`, `Set<T>` | an `Int` size, then the elements |
| `Map<K, V>` | an `Int` size, then the key and the value of every entry |
| an interface marked `@BinderInterface` | `writeStrongBinder(value.wrap())` |
| an AIDL interface | `writeStrongBinder(value.asBinder())` |
| a `Parcelable` | `value.writeToParcel(parcel, 0)`, read back through `T.CREATOR`; no class name is written |
| an enum | its `ordinal` as an `Int` |
| any other `java.io.Serializable`, such as `UUID` | `writeSerializable` |
| `T?` | an `Int` 1 and the value, or an `Int` 0 |

## Known limits

- The outcome of a `suspend` command travels one-way, and one-way transactions may fill only half
  of the receiver's Binder buffer, about 0.5 MB. A larger reply never arrives, and the caller
  waits without an error.
- An ordinary command that throws a type Android does not carry gives the caller an empty answer:
  zero, `false`, or a `NullPointerException` while reading.
- A `Set` or a `Pair` standing directly in a signature produces code that does not compile; inside
  a list or a map they work.
- Overloads get the same code, and `@Code` stops the generator.

## Checking a change

`BinderExchangeTest` in `service/src/androidTest` runs an interface with every shape above between
two real processes and prints the time of a round trip; it needs a device, and the commands are in
the file. The four cases that reproduce the first two limits fail until those are fixed. Compare the generated files before and after as well:
`service/build/generated/ksp/*/kotlin/com/github/kr328/clash/service/remote/`.
