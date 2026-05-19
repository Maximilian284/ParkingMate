package com.example.parkingmate.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVehicle(vehicle: Vehicle)

    @Query("SELECT * FROM vehicles")
    fun getAllVehicles(): Flow<List<Vehicle>>

    @Update
    suspend fun updateVehicle(vehicle: Vehicle)

    @Delete
    suspend fun deleteVehicle(vehicle: Vehicle)

    @Insert
    suspend fun insertLocation(location: SavedLocation): Long

    @Update
    suspend fun updateLocation(location: SavedLocation)

    @Delete
    suspend fun deleteLocation(location: SavedLocation)

    @Query("SELECT * FROM saved_locations")
    fun getAllLocations(): Flow<List<SavedLocation>>

    @Transaction
    @Query("SELECT * FROM parking_sessions WHERE isActive = 1 ORDER BY startTime DESC")
    fun getActiveParkings(): Flow<List<SessionWithVehicle>>

    @Transaction
    @Query("SELECT * FROM parking_sessions WHERE isActive = 0 ORDER BY startTime DESC")
    fun getHistoryParkings(): Flow<List<SessionWithVehicle>>

    @Insert
    suspend fun insertSession(session: ParkingSession): Long

    @Update
    suspend fun updateSession(session: ParkingSession)

    @Delete
    suspend fun deleteSession(session: ParkingSession)

    @Query("UPDATE parking_sessions SET isActive = 0, endTime = :endTime WHERE id = :sessionId")
    suspend fun terminateSessionById(sessionId: Int, endTime: Long)

    @Query("UPDATE parking_sessions SET walkDuration = :duration, walkDistance = :distance WHERE id = :sessionId")
    suspend fun updateWalkEffort(sessionId: Int, duration: Long, distance: Float)

    @Query("SELECT * FROM parking_sessions WHERE vehicleId = :vId AND isActive = 1")
    suspend fun getActiveParkingsForVehicle(vId: Int): List<ParkingSession>
}