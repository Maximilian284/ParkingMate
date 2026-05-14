package com.example.parkingmate.data
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "parking_sessions")
data class ParkingSession(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val vehicleId: Int,
    val locationName: String? = null,
    val type: String,
    val startTime: Long,
    val endTime: Long? = null,
    val latitude: Double,
    val longitude: Double,
    val note: String? = null,
    val photoPath: String? = null,
    val cost: Double = 0.0,
    val initialCost: Double = 0.0, // Fisso Iniziale
    val maxCost: Double = 0.0,     // Max Giornaliero
    val isActive: Boolean = true
)