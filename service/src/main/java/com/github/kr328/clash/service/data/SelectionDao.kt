package com.github.kr328.clash.service.data

import androidx.room3.*
import java.util.*

@Dao
@ColumnTypeConverters(Converters::class)
interface SelectionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setSelected(selection: Selection)

    @Query("DELETE FROM selections WHERE uuid = :uuid AND proxy = :proxy")
    suspend fun removeSelected(uuid: UUID, proxy: String)

    @Query("SELECT * FROM selections WHERE uuid = :uuid")
    suspend fun querySelections(uuid: UUID): List<Selection>

    @Query("DELETE FROM selections WHERE uuid = :uuid AND proxy in (:proxies)")
    suspend fun removeSelections(uuid: UUID, proxies: List<String>)
}
