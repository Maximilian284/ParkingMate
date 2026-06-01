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

    private const val RADIUS_IN_METERS = 150f

    // Il PendingIntent deve essere stabile e riutilizzabile affinché il sistema di geofencing
    // possa identificarlo come riferimento unico per eventi di ingresso e uscita.
    private fun getPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, GeofenceReceiver::class.java)
        return PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    @SuppressLint("MissingPermission")
    fun addGeofence(context: Context, location: SavedLocation) {
        val geofence = Geofence.Builder()
            .setRequestId(location.id.toString())
            .setCircularRegion(location.latitude, location.longitude, RADIUS_IN_METERS)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
            .build()

        val request = GeofencingRequest.Builder()
            // Evita la generazione di eventi immediati al momento della registrazione del geofence.
            .setInitialTrigger(0)
            .addGeofence(geofence)
            .build()

        val client = LocationServices.getGeofencingClient(context)
        client.addGeofences(request, getPendingIntent(context))
            .addOnSuccessListener {
                Toast.makeText(context, "Geofence attivato per ${location.name} \uD83D\uDCCD", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(context, "Errore Geofence: Assicurati di avere il GPS attivo e i permessi 'Sempre'.", Toast.LENGTH_LONG).show()
            }
    }

    // Rimuove il geofence utilizzando l'identificativo della posizione associata.
    fun removeGeofence(context: Context, locationId: Int) {
        val client = LocationServices.getGeofencingClient(context)
        client.removeGeofences(listOf(locationId.toString()))
            .addOnSuccessListener {
                Toast.makeText(context, "Geofence disattivato \uD83D\uDED1", Toast.LENGTH_SHORT).show()
            }
    }
}