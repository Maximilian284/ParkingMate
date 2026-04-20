package com.example.parkingmate.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVehicle(vehicle: Vehicle)

    @Query("SELECT * FROM vehicles")
    fun getAllVehicles(): Flow<List<Vehicle>>

    @Delete
    suspend fun deleteVehicle(vehicle: Vehicle)

    @Insert
    suspend fun insertLocation(location: SavedLocation)

    @Query("SELECT * FROM saved_locations")
    fun getAllLocations(): Flow<List<SavedLocation>>

    @Insert
    suspend fun insertSession(session: ParkingSession)

    @Query("SELECT * FROM parking_sessions WHERE isActive = 1")
    fun getActiveSessions(): Flow<List<ParkingSession>>

    @Query("SELECT * FROM parking_sessions WHERE isActive = 0 ORDER BY startTime DESC")
    fun getHistorySessions(): Flow<List<ParkingSession>>

    @Update
    suspend fun updateSession(session: ParkingSession)
}