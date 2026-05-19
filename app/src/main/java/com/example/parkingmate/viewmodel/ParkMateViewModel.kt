package com.example.parkingmate.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.parkingmate.data.AppDao
import com.example.parkingmate.data.Vehicle
import com.example.parkingmate.data.SavedLocation
import com.example.parkingmate.data.ParkingSession
import com.example.parkingmate.data.SessionWithVehicle
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import android.app.Application
import com.example.parkingmate.AlarmHelper
import com.example.parkingmate.WorkManagerHelper

class ParkMateViewModel(private val application: Application, private val dao: AppDao) : ViewModel() {
    val vehiclesList: StateFlow<List<Vehicle>> = dao.getAllVehicles()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000), // Ottimizza la batteria
            initialValue = emptyList()
        )

    val activeParkings: StateFlow<List<SessionWithVehicle>> = dao.getActiveParkings()
        .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = emptyList())

    val historyParkings: StateFlow<List<SessionWithVehicle>> = dao.getHistoryParkings()
        .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = emptyList())

    fun addVehicle(name: String, type: String) {
        viewModelScope.launch {
            val newVehicle = Vehicle(name = name, type = type)
            dao.insertVehicle(newVehicle)
        }
    }

    fun removeVehicle(vehicle: Vehicle) {
        viewModelScope.launch {
            dao.deleteVehicle(vehicle)
        }
    }

    fun updateVehicle(vehicle: Vehicle) {
        viewModelScope.launch {
            dao.updateVehicle(vehicle)
        }
    }

    val savedLocationsList: StateFlow<List<SavedLocation>> = dao.getAllLocations()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addSavedLocation(name: String, lat: Double, lng: Double, type: String, notes: String?, photoPath: String?, cost: Double, initialCost: Double = 0.0, maxCost: Double = 0.0, isGeofenceEnabled: Boolean = false, onSaved: ((Int) -> Unit)? = null) {
        viewModelScope.launch {
            val location = SavedLocation(name = name, latitude = lat, longitude = lng, defaultType = type, notes = notes, photoPath = photoPath, defaultCost = cost, initialCost = initialCost, maxCost = maxCost, isGeofenceEnabled = isGeofenceEnabled)
            val newId = dao.insertLocation(location).toInt()
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                onSaved?.invoke(newId)
            }
        }
    }

    fun updateSavedLocation(id: Int, name: String, lat: Double, lng: Double, type: String, notes: String?, photoPath: String?, isGeofenceEnabled: Boolean = false, cost: Double, initialCost: Double = 0.0, maxCost: Double = 0.0) {
        viewModelScope.launch {
            val location = SavedLocation(id = id, name = name, latitude = lat, longitude = lng, defaultType = type, notes = notes, photoPath = photoPath, isGeofenceEnabled = isGeofenceEnabled, defaultCost = cost, initialCost = initialCost, maxCost = maxCost)
            dao.updateLocation(location)
        }
    }

    fun removeSavedLocation(location: SavedLocation) {
        viewModelScope.launch {
            dao.deleteLocation(location)
        }
    }

    fun addParkingSession(
        vehicleId: Int, vehicleName: String, locationName: String?, type: String,
        lat: Double, lng: Double, notes: String?, photoPath: String?,
        cost: Double, initialCost: Double = 0.0, maxCost: Double = 0.0, endTime: Long? = null,
        onSessionSaved: ((Int) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()

            // --- REQUISITO PROFESSORE: CHIUDI I VECCHI PARCHEGGI ATTIVI DELLO STESSO VEICOLO ---
            val oldSessions = dao.getActiveParkingsForVehicle(vehicleId)
            for (oldSession in oldSessions) {
                val finishedSession = oldSession.copy(isActive = false, endTime = now)
                dao.updateSession(finishedSession) // Spostato nello storico con i dati definitivi!
                AlarmHelper.cancelAlarms(application.applicationContext, oldSession.id) // Cancella eventuali notifiche rimaste pendenti
            }
            // -----------------------------------------------------------------------------------

            // --- CREA IL NUOVO PARCHEGGIO ---
            val session = ParkingSession(
                vehicleId = vehicleId, locationName = locationName, type = type,
                startTime = now, latitude = lat, longitude = lng,
                note = notes, photoPath = photoPath,
                cost = cost, initialCost = initialCost, maxCost = maxCost,
                isActive = true, endTime = endTime
            )

            val newId = dao.insertSession(session).toInt()
            val savedSession = session.copy(id = newId)

            // Imposta i nuovi allarmi / notifiche
            AlarmHelper.scheduleFixedTicketAlarms(application.applicationContext, savedSession, vehicleName)
            if (type == "All'ora" || type == "Hourly") {
                WorkManagerHelper.startOrUpdatePeriodicWork(application.applicationContext)
            }

            // Restituisce l'ID al frammento in primo piano per far partire l'Effort Score
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                onSessionSaved?.invoke(newId)
            }
        }
    }

    fun finishParking(session: ParkingSession) {
        viewModelScope.launch {
            val finishedSession = session.copy(isActive = false, endTime = System.currentTimeMillis())
            dao.updateSession(finishedSession)

            AlarmHelper.cancelAlarms(application.applicationContext, session.id)
        }
    }

    fun updateParkingSession(session: ParkingSession) {
        viewModelScope.launch {
            dao.updateSession(session)
        }
    }

    fun deleteParkingSession(session: ParkingSession) {
        viewModelScope.launch {
            dao.deleteSession(session)
        }
    }

    fun saveWalkEffort(sessionId: Int, durationSeconds: Long, distanceMeters: Float) {
        viewModelScope.launch {
            dao.updateWalkEffort(sessionId, durationSeconds, distanceMeters)
        }
    }
}