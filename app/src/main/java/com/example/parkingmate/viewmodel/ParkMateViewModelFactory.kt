package com.example.parkingmate.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.parkingmate.data.AppDao

class ParkMateViewModelFactory(private val dao: AppDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ParkMateViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ParkMateViewModel(dao) as T
        }
        throw IllegalArgumentException("Errore: Classe ViewModel sconosciuta!")
    }
}