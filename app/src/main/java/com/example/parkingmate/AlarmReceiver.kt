package com.example.parkingmate

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.parkingmate.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val vehicleName = intent.getStringExtra("VEHICLE_NAME") ?: "Veicolo"
        val isExpiry = intent.getBooleanExtra("IS_EXPIRY", false)
        val parkingId = intent.getIntExtra("PARKING_ID", 0)

        // --- 1. SE È LA SCADENZA, TERMINIAMO IL PARCHEGGIO NEL DATABASE ---
        if (isExpiry && parkingId != 0) {
            // goAsync() dice ad Android "Aspetta a uccidere questo processo, sto scrivendo nel DB!"
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getDatabase(context)
                    db.appDao().terminateSessionById(parkingId, System.currentTimeMillis())
                } finally {
                    pendingResult.finish()
                }
            }
        }

        // --- 2. MOSTRA LA NOTIFICA A SCHERMO ---
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "parking_ticket_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Scadenze Ticket",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val title = if (isExpiry) "Ticket Scaduto!" else "Il ticket sta per scadere!"
        val message = if (isExpiry) {
            "Il ticket per $vehicleName è scaduto. La sosta è stata terminata e spostata nello storico."
        } else {
            "Il ticket per $vehicleName scadrà a breve. Controlla l'app."
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        val notificationId = if (isExpiry) parkingId * 10 + 1 else parkingId * 10
        notificationManager.notify(notificationId, builder.build())
    }
}