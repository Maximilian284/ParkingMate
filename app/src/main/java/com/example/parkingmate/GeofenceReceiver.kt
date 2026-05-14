package com.example.parkingmate

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.parkingmate.data.AppDatabase
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class GeofenceReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent)
        if (geofencingEvent == null || geofencingEvent.hasError()) return

        val transition = geofencingEvent.geofenceTransition
        val triggeringGeofences = geofencingEvent.triggeringGeofences ?: return

        if (triggeringGeofences.isEmpty()) return
        val geofenceId = triggeringGeofences[0].requestId.toIntOrNull() ?: return

        // Diciamo ad Android di aspettare a uccidere l'app perché dobbiamo leggere il Database
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context)
                // Troviamo il luogo salvato a cui corrisponde questo Geofence
                val location = db.appDao().getAllLocations().first().find { it.id == geofenceId }

                if (location != null) {
                    if (transition == Geofence.GEOFENCE_TRANSITION_ENTER) {
                        sendEnterNotification(context, location)
                    }
                    else if (transition == Geofence.GEOFENCE_TRANSITION_EXIT) {
                        // Se stiamo uscendo, cerchiamo se c'è un parcheggio attivo nei paraggi
                        val activeParkings = db.appDao().getActiveParkings().first()
                        val activeSession = activeParkings.find {
                            Math.abs(it.session.latitude - location.latitude) < 0.001 &&
                                    Math.abs(it.session.longitude - location.longitude) < 0.001
                        }
                        if (activeSession != null) {
                            sendExitNotification(context, location, activeSession.session.id, activeSession.vehicle.name)
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun sendEnterNotification(context: Context, location: com.example.parkingmate.data.SavedLocation) {
        val mainIntent = Intent(context, MainActivity::class.java).apply {
            action = "GEOFENCE_ENTER"
            putExtra("LOCATION_ID", location.id)
            putExtra("LOCATION_NAME", location.name)
            putExtra("LOCATION_LAT", location.latitude)
            putExtra("LOCATION_LNG", location.longitude)
            putExtra("LOCATION_TYPE", location.defaultType)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(context, location.id, mainIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        showNotification(context, location.id, "Sei arrivato a ${location.name} 📍", "Vuoi avviare il parcheggio? Clicca qui.", pendingIntent)
    }

    private fun sendExitNotification(context: Context, location: com.example.parkingmate.data.SavedLocation, parkingId: Int, vehicleName: String) {
        val mainIntent = Intent(context, MainActivity::class.java).apply {
            action = "GEOFENCE_EXIT"
            putExtra("PARKING_ID", parkingId)
            putExtra("VEHICLE_NAME", vehicleName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(context, parkingId + 10000, mainIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        showNotification(context, parkingId + 10000, "Stai lasciando ${location.name} 🚗", "Hai dimenticato di terminare la sosta per $vehicleName? Clicca qui.", pendingIntent)
    }

    private fun showNotification(context: Context, id: Int, title: String, message: String, pendingIntent: PendingIntent) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "geofence_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Avvisi Geofence (Luoghi)", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        notificationManager.notify(id, builder.build())
    }
}