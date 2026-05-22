package com.example.parkingmate

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.parkingmate.data.AppDatabase
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class GeofenceReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        // --- 1. GESTIONE: L'UTENTE HA PARCHEGGIATO (È FERMO O STA CAMMINANDO) ---
        if (intent.action == "WAIT_FOR_STOP") {
            if (ActivityTransitionResult.hasResult(intent)) {
                val result = ActivityTransitionResult.extractResult(intent)
                for (event in result!!.transitionEvents) {

                    // FIX: Ora accettiamo non solo che sia FERMO, ma anche che stia CAMMINANDO!
                    if (event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER &&
                        (event.activityType == DetectedActivity.STILL ||
                                event.activityType == DetectedActivity.WALKING ||
                                event.activityType == DetectedActivity.ON_FOOT)) {

                        val locId = intent.getIntExtra("LOCATION_ID", -1)
                        val name = intent.getStringExtra("LOCATION_NAME") ?: ""
                        val lat = intent.getDoubleExtra("LOCATION_LAT", 0.0)
                        val lng = intent.getDoubleExtra("LOCATION_LNG", 0.0)
                        val type = intent.getStringExtra("LOCATION_TYPE") ?: "Libero"

                        if (locId != -1) {
                            sendEnterNotification(context, locId, name, lat, lng, type)
                        }

                        // Spegniamo l'ascolto dei sensori per risparmiare batteria
                        val pi = PendingIntent.getBroadcast(context, 100, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED) {
                            ActivityRecognition.getClient(context).removeActivityTransitionUpdates(pi)
                        }
                    }
                }
            }
            return
        }

        // --- 2. GESTIONE INGRESSO/USCITA DAL RECINTO ---
        val geofencingEvent = GeofencingEvent.fromIntent(intent)
        if (geofencingEvent == null || geofencingEvent.hasError()) return

        val transition = geofencingEvent.geofenceTransition
        val triggeringGeofences = geofencingEvent.triggeringGeofences ?: return

        if (triggeringGeofences.isEmpty()) return
        val geofenceId = triggeringGeofences[0].requestId.toIntOrNull() ?: return

        val speed = if (geofencingEvent.triggeringLocation?.hasSpeed() == true) {
            geofencingEvent.triggeringLocation!!.speed
        } else {
            -1f
        }

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val location = db.appDao().getAllLocations().first().find { it.id == geofenceId }

                if (location != null) {
                    if (transition == Geofence.GEOFENCE_TRANSITION_ENTER) {
                        // Se entri veloce o a velocità incerta, ci mettiamo in ascolto dei tuoi prossimi passi
                        if (speed > 2.5f || speed == -1f) {
                            registerForStopEvent(context, location)
                        }
                    }
                    else if (transition == Geofence.GEOFENCE_TRANSITION_EXIT) {
                        // Disattiviamo l'attesa se ne usciamo
                        val stopIntent = Intent(context, GeofenceReceiver::class.java).apply { action = "WAIT_FOR_STOP" }
                        val stopPi = PendingIntent.getBroadcast(context, 100, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED) {
                            ActivityRecognition.getClient(context).removeActivityTransitionUpdates(stopPi)
                        }

                        // Manda la notifica di uscita solo se vai a velocità da auto
                        if (speed > 2.5f || speed == -1f) {
                            val activeParkings = db.appDao().getActiveParkings().first()
                            val activeSession = activeParkings.find {
                                Math.abs(it.session.latitude - location.latitude) < 0.001 &&
                                        Math.abs(it.session.longitude - location.longitude) < 0.001
                            }
                            if (activeSession != null) {
                                sendExitNotification(context, location.name, activeSession.session.id, activeSession.vehicle.name)
                            }
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun registerForStopEvent(context: Context, location: com.example.parkingmate.data.SavedLocation) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) return

        val transitions = mutableListOf<ActivityTransition>()
        // Ascoltiamo 3 eventi: Fermo, in Camminata o A Piedi.
        transitions.add(ActivityTransition.Builder()
            .setActivityType(DetectedActivity.STILL)
            .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
            .build())
        transitions.add(ActivityTransition.Builder()
            .setActivityType(DetectedActivity.WALKING)
            .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
            .build())
        transitions.add(ActivityTransition.Builder()
            .setActivityType(DetectedActivity.ON_FOOT)
            .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
            .build())

        val request = ActivityTransitionRequest(transitions)

        val intent = Intent(context, GeofenceReceiver::class.java).apply {
            action = "WAIT_FOR_STOP"
            putExtra("LOCATION_ID", location.id)
            putExtra("LOCATION_NAME", location.name)
            putExtra("LOCATION_LAT", location.latitude)
            putExtra("LOCATION_LNG", location.longitude)
            putExtra("LOCATION_TYPE", location.defaultType)
        }

        val pendingIntent = PendingIntent.getBroadcast(context, 100, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
        ActivityRecognition.getClient(context).requestActivityTransitionUpdates(request, pendingIntent)
    }

    private fun sendEnterNotification(context: Context, locId: Int, locName: String, lat: Double, lng: Double, type: String) {
        val mainIntent = Intent(context, MainActivity::class.java).apply {
            action = "GEOFENCE_ENTER"
            putExtra("LOCATION_ID", locId)
            putExtra("LOCATION_NAME", locName)
            putExtra("LOCATION_LAT", lat)
            putExtra("LOCATION_LNG", lng)
            putExtra("LOCATION_TYPE", type)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(context, locId, mainIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        showNotification(context, locId, "Sei arrivato a $locName \uD83D\uDCCD", "Hai parcheggiato? Clicca qui per avviare la sosta.", pendingIntent)
    }

    private fun sendExitNotification(context: Context, locName: String, parkingId: Int, vehicleName: String) {
        val mainIntent = Intent(context, MainActivity::class.java).apply {
            action = "GEOFENCE_EXIT"
            putExtra("PARKING_ID", parkingId)
            putExtra("VEHICLE_NAME", vehicleName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(context, parkingId + 10000, mainIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        showNotification(context, parkingId + 10000, "Stai lasciando $locName \uD83D\uDE97", "Ti sei allontanato in auto. Vuoi terminare la sosta per $vehicleName?", pendingIntent)
    }

    private fun showNotification(context: Context, id: Int, title: String, message: String, pendingIntent: PendingIntent) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "geofence_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Avvisi Luoghi Salvati", NotificationManager.IMPORTANCE_HIGH)
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