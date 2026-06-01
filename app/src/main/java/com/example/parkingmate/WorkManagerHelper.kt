package com.example.parkingmate

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object WorkManagerHelper {
    private const val WORK_NAME = "HourlyParkingWork"

    fun startOrUpdatePeriodicWork(context: Context) {
        val prefs = context.getSharedPreferences("ParkingMatePrefs", Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("periodic_enabled", true)

        if (!isEnabled) {
            stopPeriodicWork(context)
            return
        }

        val minutes = prefs.getInt("periodic_minutes", 60).toLong()

        // WorkManager impone un intervallo minimo di 15 minuti per i task periodici.
        val safeMinutes = if (minutes < 15) 15L else minutes

        val workRequest = PeriodicWorkRequestBuilder<HourlyParkingWorker>(safeMinutes, TimeUnit.MINUTES)
            .build()

        // Sostituisce eventuale lavoro già schedulato mantenendo un'unica istanza attiva con lo stesso nome.
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }

    fun stopPeriodicWork(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}