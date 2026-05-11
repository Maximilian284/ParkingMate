package com.example.parkingmate.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "parking_sessions")
data class ParkingSession(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val vehicleId: Int,
    val name: String? = null,
    val type: String, // "Free", "Hourly", "Fixed"
    val startTime: Long,
    val endTime: Long? = null,
    val latitude: Double,
    val longitude: Double,
    val note: String? = null,
    val photoPath: String? = null,
    val cost: Double = 0.0,
    val isActive: Boolean = true // true -> home/active, false -> home/history
)