package com.example.parkingmate.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.parkingmate.data.AppDao
import com.example.parkingmate.data.Vehicle
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
}