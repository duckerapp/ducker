# kaidl

The generator of the Binder glue between the interface process and the service process. It is a
KSP processor: for every Kotlin interface marked `@BinderInterface` it writes

- `XDelegate` — the service side: a `Binder` that unpacks the arguments, calls the implementation
  and packs the result;
- `XProxy` — the caller's side: the same interface on top of an `IBinder`;
- `X.wrap()` and `IBinder.unwrap(X::class)` — from an implementation to a binder and back. Inside
  one process both return the object itself, and no parcel is involved.

The generator runs inside the build of `:service` and never reaches the APK. Its runtime — the
annotation, the call of an ordinary command and the transport of `suspend` ones — is three files
in `service/src/main/java/com/github/kr328/kaidl/`. The interfaces are in
`service/src/main/java/com/github/kr328/clash/service/remote/`.

## Origin

[Kr328/kaidl](https://github.com/Kr328/kaidl) by Kr328, MIT — `LICENSE` in this directory covers
the generator and the runtime files. The starting point was commit `7710f43`, which is version
1.15, the one Clash Meta for Android takes as a binary. It lives in the tree because the library
has had no commit since 2021 and its binaries were left on a single third-party mirror.

The generator and the runtime have since been rewritten here. The annotation, the shape of an
interface and the names of what is generated are as they were. What behaves differently:

- a `suspend` command always ends, and its reply may be of any size — before, a reply over half a
  megabyte never arrived and the caller waited without an error;
- an ordinary command that throws gives the caller an exception — before, a type Android does not
  carry gave an empty answer: zero, `false`, or a `NullPointerException` while reading;
- `DeadObjectException` reaches the caller only when the other process is gone;
- a type the generator does not know, two functions with one name, or the annotation on something
  that is not an interface stop the build with the file and the line;
- `UUID` travels as two `Long`s instead of Java serialization, the generic `Serializable` and AIDL
  branches and the `@Code` annotation, which never worked, are gone;
- a generated file depends on its interface alone: an edit elsewhere does not write it again.

## What an interface may hold

Functions, ordinary or `suspend`, with the types of the table below. Default arguments work: they
are applied on the caller's side. Two functions cannot share a name. An interface that is passed
as an argument or returned has to be in the same module: the annotation stays in the source.

Use a `suspend` command for anything that may take time or return much; an ordinary one blocks
the caller's thread and has to fit one transaction.

## Wire protocol

Both processes always run the same APK, so nothing here has to survive a change of an interface.

The descriptor of an interface is its fully qualified name. The transaction code of a command is
`IBinder.FIRST_CALL_TRANSACTION` plus its position in the interface, counted from zero in
declaration order.

**An ordinary command** is one synchronous transaction.

- Request: the interface token (`writeInterfaceToken`), then the arguments in order.
- Reply: `writeNoException()`, then the result — or what was thrown.

Whatever is thrown while the arguments are read or in the implementation becomes the reply,
written by `Parcel.writeException`. The types that method carries keep their type and message:
`SecurityException`, `BadParcelableException`, `IllegalArgumentException`, `NullPointerException`,
`IllegalStateException`, `NetworkOnMainThreadException`, `UnsupportedOperationException`; a
subclass arrives as the type it extends. Anything else arrives as `IllegalArgumentException` with
the same message. A `java.lang.Error` is not caught: as everywhere in Android it ends the service
process, and the caller gets `DeadObjectException`.

On the caller's side a transaction that fails while the other process is alive is a
`TransactionTooLargeException` (Android itself reports it as the death of the other process when
the request is small), and a code the other side does not know is an
`UnsupportedOperationException`.

**A `suspend` command** has two ends, a Binder object in each process, for one call.

1. The caller starts watching the death of the service and sends the request with one more value
   after the arguments — its end of the call. The service reads the arguments, starts the body in
   a scope of its own on `Dispatchers.IO` and answers at once with `writeNoException()` and its
   end of the call.
2. When the body ends, the service tells the caller's end in a one-way transaction:
   - `FIRST_CALL_TRANSACTION` — the reply itself, `writeNoException()` and the result or what the
     body threw, by the rule of an ordinary command; only if it is 64 KB or less;
   - `FIRST_CALL_TRANSACTION + 1` — nothing: the body was cancelled, and so is the caller;
   - `FIRST_CALL_TRANSACTION + 2` — the service's end: the reply is larger, or the caller's
     buffer had no room for it, and waits in the service as bytes.
3. A reply that waits is pulled by the caller from the service's end with synchronous
   transactions `FIRST_CALL_TRANSACTION + 1`: the request is an offset, the reply the size of the
   whole and the next piece, 256 KB at most. A process pulls one reply at a time. After the last
   piece the caller sends `FIRST_CALL_TRANSACTION` to the service's end, one-way, and the service
   drops the bytes.
4. Cancelling the caller sends that same one-way `FIRST_CALL_TRANSACTION`: the service cancels the
   body and drops a reply that waits.
5. The death of the service ends the call with `DeadObjectException`. The death of the caller
   cancels the body and drops a reply that waits.

The sizes come from the Binder buffer of the receiving process: 1 MB for everything that is on
its way to it. One-way transactions may fill half of it, and Android reaches the process through
the same half, so a one-way message of ours stays small; a synchronous reply may use the whole,
and one piece at a time leaves three quarters of it free.

If a one-way message cannot be handed over because that half is full at the moment, the reply
waits in the service like a large one, and the short message about it is tried again a few times
over a third of a second.

**Values**

| Kotlin type | In the parcel |
|---|---|
| `Int`, `Long`, `Float`, `Double`, `Byte`, `String` | `writeInt` … `writeString` |
| `Boolean` | an `Int`, 1 or 0 |
| `Unit` | nothing |
| `ByteArray`, `CharArray`, `BooleanArray`, `IntArray`, `LongArray`, `FloatArray`, `DoubleArray` | `writeByteArray` … `writeDoubleArray` |
| `IBinder`, `Bundle`, `SparseBooleanArray` | `writeStrongBinder`, `writeBundle`, `writeSparseBooleanArray` |
| `UUID` | two `Long`s, the most significant bits first |
| `List<T>`, `Set<T>` | an `Int` size, then the elements |
| `Map<K, V>` | an `Int` size, then the key and the value of every entry |
| `Pair<A, B>` | the first, then the second |
| an interface marked `@BinderInterface` | `writeStrongBinder(value.wrap())` |
| a `Parcelable` | `value.writeToParcel(parcel, 0)`, read back through `T.CREATOR`; no class name is written |
| an enum | its `ordinal` as an `Int` |
| `T?` | an `Int` 1 and the value, or an `Int` 0 |

Collections keep their order. Any other type is a build error: `MutableList`, `Array<T>`, a type
alias, a class with type parameters, a plain `Serializable`.

## Limits

- The arguments of a command are one synchronous transaction: together they have to fit into
  what is free of the service's buffer, or the call ends with `TransactionTooLargeException`. The
  reply of an ordinary command is bound the same way by the caller's buffer.
- A `suspend` reply over 64 KB cannot hold a binder or a file descriptor: the caller gets an
  `IllegalArgumentException` that says so.
- If the caller's process is alive and yet takes no one-way message for a third of a second, the
  outcome of a `suspend` command is dropped with a warning in the log, and that caller waits
  until it is cancelled.

## Rules the runtime keeps

Whoever changes `SuspendCall.kt` has to keep them; the comments there say where each lives.

- Every ending of a call — the reply, a cancellation on either side, a failed start, the death of
  the service — goes through one gate at the caller's end, and only the first one acts.
- Neither end keeps the other's binder, the caller's continuation or its reply parcel after the
  call. Each end outlives the call until the other process collects its garbage, and on kernels
  older than 6.x the time of every Binder call grows with the number of foreign binders a
  process holds.
- Nothing over 64 KB goes one-way.

## Checking a change

`BinderExchangeTest` in `service/src/androidTest` runs an interface with every shape above
between two real processes — errors, cancellation both ways, the death of either process, replies
up to 8 MB, a burst of replies larger than the buffer — and prints the time of a round trip. It
needs a device; the commands are in the file. Compare the generated files before and after as
well: `service/build/generated/ksp/*/kotlin/com/github/kr328/clash/service/remote/`.

To see what the generator refuses, add a function with such a type to the test interface and run
`./gradlew :service:kspMetaDebugAndroidTestKotlin`.

## KotlinPoet

The generator writes its output with KotlinPoet, and Room's compiler does too: the two processors
share one classpath in the build of `:service`, where the higher version wins. The version in
`gradle/libs.versions.toml` is therefore the one Room's compiler was built against, 2.0.0 for
Room 2.8. The latest, 2.4.0, generates the same files for our interfaces and for Room's, but
Room's compiler calls an internal function of KotlinPoet by a name it no longer has there
(`TypeVariableName.of`), and the first declaration that takes Room down that path would stop the
build.

Before raising the version, within the same major version only:

1. `scripts/check-linkage.py` — every reference of Room's compiler into KotlinPoet resolves in
   the new jar (the command is in the script);
2. the files Room generates are the same before and after:
   `service/build/generated/ksp/*/kotlin/com/github/kr328/clash/service/data/`;
3. so are ours, or the difference is understood.
