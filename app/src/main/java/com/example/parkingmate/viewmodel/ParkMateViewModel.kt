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

class ParkMateViewModel(private val dao: AppDao) : ViewModel() {

    // 1. I DATI IN TEMPO REALE (StateFlow)
    // stateIn converte il Flow del Database in uno StateFlow perfetto per la UI moderna.
    // Se aggiungi un veicolo, questa lista si aggiorna da sola in tutti i Fragment!
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

    // (Aggiungeremo qui le liste per i Parcheggi e i Luoghi in seguito, per ora partiamo dai Veicoli)

    // 2. LE AZIONI (Inserisci, Elimina, ecc.)
    // Usiamo viewModelScope.launch per fare il lavoro "in background" senza bloccare l'app
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

    // --- SALVATAGGIO LUOGO PREFERITO ---
    fun addSavedLocation(name: String, lat: Double, lng: Double, type: String, notes: String?) {
        viewModelScope.launch {
            val location = SavedLocation(name = name, latitude = lat, longitude = lng, defaultType = type, notes = notes)
            dao.insertLocation(location)
        }
    }

    // --- SALVATAGGIO PARCHEGGIO (E COSTI) ---
    // (Per ora salviamo il costo iniziale base, poi per calcolare il totale servirà un calcolo matematico)
    // --- SALVATAGGIO PARCHEGGIO (E COSTI/SCADENZA) ---
    fun addParkingSession(
        vehicleId: Int,
        type: String,
        lat: Double,
        lng: Double,
        notes: String?,
        photoPath: String?,
        initialCost: Double,
        endTime: Long? = null // <--- ECCO IL PARAMETRO MANCANTE!
    ) {
        viewModelScope.launch {
            val session = ParkingSession(
                vehicleId = vehicleId,
                type = type,
                startTime = System.currentTimeMillis(), // Orario di ORA
                latitude = lat,
                longitude = lng,
                note = notes,
                photoPath = photoPath,
                cost = initialCost,
                isActive = true, // È un parcheggio attivo!
                endTime = endTime // Salviamo la scadenza nel database
            )
            dao.insertSession(session)
        }
    }

    val savedLocationsList: StateFlow<List<SavedLocation>> = dao.getAllLocations()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun removeSavedLocation(location: SavedLocation) {
        viewModelScope.launch {
            dao.deleteLocation(location)
        }
    }

    fun updateSavedLocation(id: Int, name: String, lat: Double, lng: Double, type: String, notes: String?) {
        viewModelScope.launch {
            val location = SavedLocation(id = id, name = name, latitude = lat, longitude = lng, defaultType = type, notes = notes)
            dao.updateLocation(location)
        }
    }

    // --- TERMINA UN PARCHEGGIO ATTIVO ---
    fun finishParking(session: ParkingSession) {
        viewModelScope.launch {
            val finishedSession = session.copy(
                isActive = false, // Lo sposta nello storico!
                endTime = System.currentTimeMillis() // Salva l'ora esatta di fine
            )
            dao.updateSession(finishedSession)

            // TODO (In futuro per la fase 4): Se serve tracciare la camminata (Effort Score),
            // la faremo partire da qui!
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
}