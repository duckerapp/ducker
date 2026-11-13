package com.github.kr328.clash.service.data

import androidx.room3.ColumnTypeConverter
import com.github.kr328.clash.service.model.Profile
import java.util.*

class Converters {
    @ColumnTypeConverter
    fun fromUUID(uuid: UUID): String {
        return uuid.toString()
    }

    @ColumnTypeConverter
    fun toUUID(uuid: String): UUID {
        return UUID.fromString(uuid)
    }

    @ColumnTypeConverter
    fun fromProfileType(type: Profile.Type): String {
        return type.name
    }

    @ColumnTypeConverter
    fun toProfileType(type: String): Profile.Type {
        return Profile.Type.valueOf(type)
    }
}