package com.example.parkingmate

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.parkingmate.data.AppDatabase
import com.google.android.gms.location.*
import kotlinx.coroutines.*

class WalkingTrackerService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var activityRecognitionClient: ActivityRecognitionClient

    private var sessionId: Int = -1
    private var startTimeMillis: Long = 0
    private var totalDistanceMeters: Float = 0f
    private var lastLocation: Location? = null

    private var stillTimerJob: Job? = null
    private val WAIT_TIME_MILLIS = 60 * 1000L

    private val CHANNEL_ID = "walking_tracker_channel"
    private val NOTIFICATION_ID = 999

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        activityRecognitionClient = ActivityRecognition.getClient(this)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    if (lastLocation != null) {
                        totalDistanceMeters += lastLocation!!.distanceTo(location)
                    }
                    lastLocation = location
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START" -> {
                sessionId = intent.getIntExtra("SESSION_ID", -1)
                startTimeMillis = System.currentTimeMillis()
                startForegroundService()
                startLocationTracking()
                setupActivityRecognition()
            }
            "STOP" -> stopTrackingAndSave()
            "ACTIVITY_TRANSITION" -> {
                if (ActivityTransitionResult.hasResult(intent)) {
                    val result = ActivityTransitionResult.extractResult(intent)
                    for (event in result!!.transitionEvents) {
                        if (event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER && event.activityType == DetectedActivity.STILL) {
                            // Avvia un timer di inattività: se l'utente rimane fermo per un intervallo continuo,
                            // il tracciamento viene terminato automaticamente per evitare falsi positivi di camminata.
                            if (stillTimerJob == null || !stillTimerJob!!.isActive) {
                                stillTimerJob = CoroutineScope(Dispatchers.Main).launch {
                                    delay(WAIT_TIME_MILLIS)
                                    stopTrackingAndSave()
                                }
                            }
                        } else if (event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER && event.activityType == DetectedActivity.IN_VEHICLE) {
                            stopTrackingAndSave()
                        } else if (event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER &&
                            (event.activityType == DetectedActivity.WALKING || event.activityType == DetectedActivity.ON_FOOT)) {
                            // Interrompe il timer di stop automatico quando viene rilevato nuovamente movimento.
                            stillTimerJob?.cancel()
                        }
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startLocationTracking() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000).setMinUpdateIntervalMillis(2000).build()
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        }
    }

    private fun setupActivityRecognition() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) return

        val transitions = mutableListOf<ActivityTransition>()
        transitions.add(ActivityTransition.Builder().setActivityType(DetectedActivity.STILL).setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER).build())
        transitions.add(ActivityTransition.Builder().setActivityType(DetectedActivity.WALKING).setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER).build())
        transitions.add(ActivityTransition.Builder().setActivityType(DetectedActivity.IN_VEHICLE).setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER).build())

        val request = ActivityTransitionRequest(transitions)
        val intent = Intent(this, WalkingTrackerService::class.java).apply { action = "ACTIVITY_TRANSITION" }
        val pendingIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
        activityRecognitionClient.requestActivityTransitionUpdates(request, pendingIntent)
    }

    private fun stopTrackingAndSave() {
        stillTimerJob?.cancel()
        fusedLocationClient.removeLocationUpdates(locationCallback)

        val intent = Intent(this, WalkingTrackerService::class.java).apply { action = "ACTIVITY_TRANSITION" }
        val pendingIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED) {
            activityRecognitionClient.removeActivityTransitionUpdates(pendingIntent)
        }

        // Correzione della durata effettiva sottraendo il periodo di attesa iniziale prima della conferma di inattività.
        val durationSeconds = ((System.currentTimeMillis() - startTimeMillis) - WAIT_TIME_MILLIS) / 1000
        val finalDuration = if (durationSeconds > 0) durationSeconds else 0L

        if (sessionId != -1) {
            CoroutineScope(Dispatchers.IO).launch {
                val db = AppDatabase.getDatabase(applicationContext)
                db.appDao().updateWalkEffort(sessionId, finalDuration, totalDistanceMeters)
            }
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startForegroundService() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Tracciamento Sforzo", NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }

        val stopIntent = Intent(this, WalkingTrackerService::class.java).apply { action = "STOP" }
        val stopPendingIntent = PendingIntent.getService(this, 1, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setContentTitle("Parking Effort Attivo")
            .setContentText("Rilevamento destinazione in background...")
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "CHIUDI E SALVA", stopPendingIntent)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}