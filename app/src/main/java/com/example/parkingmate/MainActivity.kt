package com.example.parkingmate

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import androidx.activity.enableEdgeToEdge
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.example.parkingmate.data.AppDatabase
import com.google.android.material.navigation.NavigationBarView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private val startupPermissionsLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        val navView = findViewById<NavigationBarView>(R.id.bottom_navigation)
        NavigationUI.setupWithNavController(navView, navController)

        handleIntent(intent)
        askForStartupPermissions()
    }

    private fun askForStartupPermissions() {
        val permissionsToAsk = mutableListOf<String>()
        permissionsToAsk.add(android.Manifest.permission.ACCESS_FINE_LOCATION)
        permissionsToAsk.add(android.Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) permissionsToAsk.add(android.Manifest.permission.POST_NOTIFICATIONS)
        // Permesso richiesto solo da Android Q in poi per l'accesso ai dati di attività fisica dell'utente.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) permissionsToAsk.add(android.Manifest.permission.ACTIVITY_RECOGNITION)

        val missingPermissions = permissionsToAsk.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missingPermissions.isNotEmpty()) startupPermissionsLauncher.launch(missingPermissions.toTypedArray())
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action

        if (action == "GEOFENCE_ENTER") {
            val locationId = intent.getIntExtra("LOCATION_ID", -1)
            val name = intent.getStringExtra("LOCATION_NAME") ?: ""
            val lat = intent.getDoubleExtra("LOCATION_LAT", 0.0)
            val lng = intent.getDoubleExtra("LOCATION_LNG", 0.0)
            val type = intent.getStringExtra("LOCATION_TYPE") ?: "Libero"

            if (locationId != -1) {
                val form = AddParkingFragment()
                val b = Bundle()
                b.putInt("location_id", locationId)
                b.putString("name", name)
                b.putDouble("lat", lat)
                b.putDouble("lng", lng)
                b.putString("type", type)
                b.putBoolean("is_location_locked", true)
                form.arguments = b
                form.show(supportFragmentManager, "AddParkingDialog")
            }
        }
        else if (action == "GEOFENCE_EXIT") {
            val parkingId = intent.getIntExtra("PARKING_ID", -1)
            val vehicleName = intent.getStringExtra("VEHICLE_NAME") ?: "il tuo veicolo"

            if (parkingId != -1) {
                MaterialAlertDialogBuilder(this)
                    .setTitle("Termina Parcheggio")
                    .setMessage("Sembra che ti stia allontanando dal parcheggio. Vuoi terminare la sosta per $vehicleName?")
                    .setPositiveButton("Termina Sosta") { _, _ ->
                        CoroutineScope(Dispatchers.IO).launch {
                            val db = AppDatabase.getDatabase(applicationContext)
                            db.appDao().terminateSessionById(parkingId, System.currentTimeMillis())
                            withContext(Dispatchers.Main) {
                                android.widget.Toast.makeText(applicationContext, "Sosta terminata!", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    .setNegativeButton("Ignora", null)
                    .show()
            }
        }
    }
}