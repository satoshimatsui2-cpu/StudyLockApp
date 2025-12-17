package com.example.studylockapp.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "locked_apps")
data class LockedAppEntity(
    @PrimaryKey val packageName: String,
    val label: String,
    val isLocked: Boolean = true,
    val createdAtMillis: Long = System.currentTimeMillis()
)

