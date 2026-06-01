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

        // Converte il Flow del DAO in uno snapshot sincrono sospendendo la coroutine fino al primo valore disponibile.
        val activeParkings = db.appDao().getActiveParkings().first()

        // Regola di business: il worker considera solo sessioni con tariffa oraria per il calcolo del promemoria.
        val hourlyParkings = activeParkings.filter { it.session.type == "All'ora" || it.session.type == "Hourly" }

        if (hourlyParkings.isEmpty()) {
            return Result.success()
        }

        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "periodic_parking_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Promemoria Sosta Oraria", NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }

        for (item in hourlyParkings) {
            val session = item.session
            val vehicleName = item.vehicle.name

            val elapsedMillis = System.currentTimeMillis() - session.startTime
            val elapsedMinutes = (elapsedMillis / (1000 * 60)).toInt()
            val hours = elapsedMinutes / 60
            val mins = elapsedMinutes % 60

            val timeString = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"

            val costPerHour = session.cost
            // Stima lineare del costo basata sulla tariffa oraria e sul tempo trascorso.
            val currentCost = (elapsedMinutes / 60.0) * costPerHour
            val costFormatted = String.format(Locale.getDefault(), "%.2f €", currentCost)

            val builder = NotificationCompat.Builder(applicationContext, channelId)
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setContentTitle("Sosta in corso: $vehicleName")
                .setContentText("Tempo: $timeString - Costo stimato: $costFormatted")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true)

            notificationManager.notify(session.id * 100, builder.build())
        }

        return Result.success()
    }
}