package com.example.parkingmate.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.parkingmate.data.AppDao

class ParkMateViewModelFactory(
    private val application: Application,
    private val dao: AppDao
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ParkMateViewModel::class.java)) {
            // Il cast è sicuro perché il controllo precedente garantisce che venga richiesta
            // esclusivamente un'istanza di ParkMateViewModel.
            @Suppress("UNCHECKED_CAST")
            return ParkMateViewModel(application, dao) as T
        }
        throw IllegalArgumentException("Errore: Classe ViewModel sconosciuta!")
    }
}