#!/usr/bin/env python3
"""Static linkage check between jars.

Every reference from the consumer jars into one package must resolve in the provider jars: the
class is there, and a method or field with that name and descriptor is declared in it or in one
of its supertypes and is not private or package-private. This is what would otherwise surface at
build time as NoClassDefFoundError, NoSuchMethodError or IllegalAccessError.

    scripts/check-linkage.py <package/prefix/> --consumers a.jar b.jar --providers c.jar d.jar

The case it was written for — Room's compiler against a newer KotlinPoet (kaidl/README.md):

    C=~/.gradle/caches/modules-2/files-2.1
    scripts/check-linkage.py com/squareup/kotlinpoet/ \\
        --consumers $C/androidx.room3/room3-compiler/3.0.3/*/room3-compiler-3.0.3.jar \\
                    $C/androidx.room3/room3-compiler-processing/3.0.3/*/room3-compiler-processing-3.0.3.jar \\
                    $C/com.squareup/kotlinpoet-javapoet/2.0.0/*/kotlinpoet-javapoet-2.0.0.jar \\
        --providers kotlinpoet-jvm-2.4.0.jar \\
                    $C/com.squareup/kotlinpoet-javapoet/2.0.0/*/kotlinpoet-javapoet-2.0.0.jar

Supertypes outside the provider jars are not read: of them only the members of `Object` and
`Enum` are known here, and a reference that would have to resolve further is listed as unchecked.
Exit code: 0 — all resolved, 1 — something is missing, 2 — nothing missing, something unchecked.
"""
import struct
import sys
import zipfile

ACC_PUBLIC, ACC_PRIVATE, ACC_PROTECTED = 0x0001, 0x0002, 0x0004

# Members of the two supertypes every class reaches, as (name, descriptor).
OUTSIDE = {
    "java/lang/Object": {
        ("<init>", "()V"), ("toString", "()Ljava/lang/String;"), ("hashCode", "()I"),
        ("equals", "(Ljava/lang/Object;)Z"), ("getClass", "()Ljava/lang/Class;"),
    },
    "java/lang/Enum": {
        ("name", "()Ljava/lang/String;"), ("ordinal", "()I"), ("compareTo", "(Ljava/lang/Enum;)I"),
        ("getDeclaringClass", "()Ljava/lang/Class;"),
    },
}
OUTSIDE["java/lang/Enum"] |= OUTSIDE["java/lang/Object"]


def parse(data):
    """One class file: (name, superclass, interfaces, {(name, descriptor): access}, references, classes)."""
    pos = 8
    count, = struct.unpack_from(">H", data, pos)
    pos += 2
    pool = [None] * count
    i = 1
    while i < count:
        tag = data[pos]
        pos += 1
        if tag == 1:
            n, = struct.unpack_from(">H", data, pos)
            pos += 2
            pool[i] = ("utf8", data[pos:pos + n].decode("utf-8", "replace"))
            pos += n
        elif tag in (3, 4):
            pos += 4
        elif tag in (5, 6):
            pos += 8
            i += 1
        elif tag == 7:
            pool[i] = ("class", struct.unpack_from(">H", data, pos)[0])
            pos += 2
        elif tag == 8:
            pos += 2
        elif tag in (9, 10, 11):
            pool[i] = ("field" if tag == 9 else "method",) + struct.unpack_from(">HH", data, pos)
            pos += 4
        elif tag == 12:
            pool[i] = ("name-and-type",) + struct.unpack_from(">HH", data, pos)
            pos += 4
        elif tag == 15:
            pos += 3
        elif tag in (16, 19, 20):
            pos += 2
        elif tag in (17, 18):
            pos += 4
        else:
            raise ValueError(f"constant pool tag {tag}")
        i += 1

    utf = lambda k: pool[k][1]
    cls = lambda k: utf(pool[k][1])

    _, this, parent = struct.unpack_from(">HHH", data, pos)
    pos += 6
    n, = struct.unpack_from(">H", data, pos)
    pos += 2
    interfaces = [cls(k) for k in struct.unpack_from(f">{n}H", data, pos)]
    pos += 2 * n

    members = {}
    for _ in range(2):  # fields, then methods
        n, = struct.unpack_from(">H", data, pos)
        pos += 2
        for _ in range(n):
            access, name, descriptor, attributes = struct.unpack_from(">HHHH", data, pos)
            pos += 8
            members[(utf(name), utf(descriptor))] = access
            for _ in range(attributes):
                _, length = struct.unpack_from(">HI", data, pos)
                pos += 6 + length

    references, classes = [], set()
    for entry in pool:
        if entry is None:
            continue
        if entry[0] in ("field", "method"):
            name_and_type = pool[entry[2]]
            references.append((entry[0], cls(entry[1]), utf(name_and_type[1]), utf(name_and_type[2])))
        elif entry[0] == "class":
            classes.add(utf(entry[1]).lstrip("[").removeprefix("L").removesuffix(";"))

    return cls(this), (cls(parent) if parent else None), interfaces, members, references, classes


def classes_of(jar):
    with zipfile.ZipFile(jar) as archive:
        for name in archive.namelist():
            if name.endswith(".class") and not name.startswith("META-INF/"):
                yield parse(archive.read(name))


def main():
    args = sys.argv[1:]
    if len(args) < 5 or "--consumers" not in args or "--providers" not in args:
        sys.exit(__doc__)

    prefix = args[0]
    consumers = args[args.index("--consumers") + 1:args.index("--providers")]
    providers = args[args.index("--providers") + 1:]

    provided = {}
    for jar in providers:
        for name, parent, interfaces, members, _, _ in classes_of(jar):
            provided[name] = (parent, interfaces, members)

    def resolve(owner, member, seen):
        """'found', 'missing', 'hidden' (not accessible from outside) or 'unchecked'."""
        if owner in seen:
            return "missing"
        seen.add(owner)

        if owner not in provided:
            if owner in OUTSIDE:
                return "found" if member in OUTSIDE[owner] else "missing"
            return "unchecked"

        parent, interfaces, members = provided[owner]
        if member in members:
            return "found" if members[member] & (ACC_PUBLIC | ACC_PROTECTED) else "hidden"

        answers = [resolve(above, member, seen) for above in ([parent] if parent else []) + interfaces]
        for answer in ("found", "hidden", "unchecked"):
            if answer in answers:
                return answer
        return "missing"

    total = missing = unchecked = 0
    for jar in consumers:
        for name, _, _, _, references, classes in classes_of(jar):
            if name.startswith(prefix):
                continue
            for cls in sorted(classes):
                if cls.startswith(prefix):
                    total += 1
                    if cls not in provided:
                        missing += 1
                        print(f"MISSING class {cls}  (from {name})")
            for kind, owner, member, descriptor in references:
                if not owner.startswith(prefix):
                    continue
                total += 1
                answer = resolve(owner, (member, descriptor), set()) if owner in provided else "missing"
                if answer in ("missing", "hidden"):
                    missing += 1
                    print(f"{answer.upper()} {kind} {owner}.{member}{descriptor}  (from {name})")
                elif answer == "unchecked":
                    unchecked += 1
                    print(f"UNCHECKED {kind} {owner}.{member}{descriptor}  (from {name})")

    print(f"{total} references into {prefix}: {missing} unresolved, {unchecked} unchecked")
    sys.exit(1 if missing else 2 if unchecked else 0)


if __name__ == "__main__":
    main()
