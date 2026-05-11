package com.example.parkingmate

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.parkingmate.data.AppDatabase
import kotlinx.coroutines.flow.first
import java.util.Locale

class HourlyParkingWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val db = AppDatabase.getDatabase(applicationContext)

        // .first() prende la lista attuale dal Flow e si ferma (perfetto per lavori one-shot)
        val activeParkings = db.appDao().getActiveParkings().first()

        // Filtriamo solo i parcheggi attivi con tariffa "All'ora"
        val hourlyParkings = activeParkings.filter { it.session.type == "All'ora" || it.session.type == "Hourly" }

        // Se non ci sono parcheggi a ore attivi, il lavoro finisce qui senza consumare batteria
        if (hourlyParkings.isEmpty()) {
            return Result.success()
        }

        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "periodic_parking_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Usiamo un'importanza bassa (LOW) così la notifica appare in silenzio senza far vibrare il telefono ogni ora
            val channel = NotificationChannel(channelId, "Promemoria Sosta Oraria", NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }

        for (item in hourlyParkings) {
            val session = item.session
            val vehicleName = item.vehicle.name

            // Calcolo tempo trascorso
            val elapsedMillis = System.currentTimeMillis() - session.startTime
            val elapsedMinutes = (elapsedMillis / (1000 * 60)).toInt()
            val hours = elapsedMinutes / 60
            val mins = elapsedMinutes % 60

            val timeString = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"

            // Calcolo costo (supponiamo proporzionale al minuto: costo_orario * ore_trascorse)
            val costPerHour = session.cost
            val currentCost = (elapsedMinutes / 60.0) * costPerHour
            val costFormatted = String.format(Locale.getDefault(), "%.2f €", currentCost)

            val builder = NotificationCompat.Builder(applicationContext, channelId)
                .setSmallIcon(android.R.drawable.ic_menu_info_details) // Icona della "i"
                .setContentTitle("Sosta in corso: $vehicleName")
                .setContentText("Tempo: $timeString - Costo stimato: $costFormatted")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true)

            // Usiamo un ID diverso rispetto agli allarmi esatti
            notificationManager.notify(session.id * 100, builder.build())
        }

        return Result.success()
    }
}