package com.example.parkingmate.data

import androidx.room.Embedded
import androidx.room.Relation

data class SessionWithVehicle(
    @Embedded val session: ParkingSession,
    @Relation(
        parentColumn = "vehicleId",
        entityColumn = "id"
    )
    val vehicle: Vehicle
)