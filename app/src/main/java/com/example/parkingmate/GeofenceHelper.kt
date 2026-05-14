package com.example.parkingmate

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.example.parkingmate.data.SavedLocation
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices

object GeofenceHelper {

    // Raggio del recinto in metri (150 metri è la misura consigliata da Google)
    private const val RADIUS_IN_METERS = 150f

    private fun getPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, GeofenceReceiver::class.java)
        // Per il Geofence serve obbligatoriamente FLAG_MUTABLE da Android 12 in poi
        return PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    @SuppressLint("MissingPermission") // Il permesso viene controllato prima di chiamare questa funzione
    fun addGeofence(context: Context, location: SavedLocation) {
        val geofence = Geofence.Builder()
            .setRequestId(location.id.toString()) // Usiamo l'ID del luogo come codice univoco
            .setCircularRegion(location.latitude, location.longitude, RADIUS_IN_METERS)
            .setExpirationDuration(Geofence.NEVER_EXPIRE) // Dura finché l'utente non lo spegne
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
            .build()

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()

        val client = LocationServices.getGeofencingClient(context)
        client.addGeofences(request, getPendingIntent(context))
            .addOnSuccessListener {
                Toast.makeText(context, "Geofence attivato per ${location.name} 📍", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(context, "Errore Geofence: Assicurati di avere il GPS attivo e i permessi 'Sempre' concessi.", Toast.LENGTH_LONG).show()
            }
    }

    fun removeGeofence(context: Context, locationId: Int) {
        val client = LocationServices.getGeofencingClient(context)
        client.removeGeofences(listOf(locationId.toString()))
            .addOnSuccessListener {
                Toast.makeText(context, "Geofence disattivato 🛑", Toast.LENGTH_SHORT).show()
            }
    }
}