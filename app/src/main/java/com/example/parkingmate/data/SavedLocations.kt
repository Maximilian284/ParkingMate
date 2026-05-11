package com.example.parkingmate.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_locations")
data class SavedLocation(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val defaultType: String = "Libero",
    val notes: String? = null,
    val isGeofenceEnabled: Boolean = false
)