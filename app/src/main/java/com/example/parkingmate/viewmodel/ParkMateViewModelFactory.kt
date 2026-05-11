package com.example.parkingmate.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.parkingmate.data.AppDao

class ParkMateViewModelFactory(
    private val application: Application, // <--- Aggiunto!
    private val dao: AppDao
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ParkMateViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ParkMateViewModel(application, dao) as T // <--- Aggiunto!
        }
        throw IllegalArgumentException("Errore: Classe ViewModel sconosciuta!")
    }
}