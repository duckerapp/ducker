package com.github.kr328.clash.common.util

import android.content.Intent
import android.net.Uri
import java.util.*

var Intent.uuid: UUID?
    get() {
        return data?.takeIf { it.scheme == "uuid" }?.schemeSpecificPart?.let(UUID::fromString)
    }
    set(value) {
        data = Uri.fromParts("uuid", value.toString(), null)
    }

fun Intent.setUUID(uuid: UUID): Intent {
    this.uuid = uuid

    return this
}