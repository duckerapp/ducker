package com.github.kr328.clash.service.data

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.ColumnTypeConverters
import java.util.*

@Entity(
    tableName = "selections",
    foreignKeys = [ForeignKey(
        entity = Imported::class,
        childColumns = ["uuid"],
        parentColumns = ["uuid"],
        onDelete = ForeignKey.CASCADE,
        onUpdate = ForeignKey.CASCADE
    )],
    primaryKeys = ["uuid", "proxy"]
)
@ColumnTypeConverters(Converters::class)
data class Selection(
    @ColumnInfo(name = "uuid") val uuid: UUID,
    @ColumnInfo(name = "proxy") val proxy: String,
    @ColumnInfo(name = "selected") val selected: String,
)