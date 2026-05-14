package com.example.parkingmate

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.activity.enableEdgeToEdge
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.example.parkingmate.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        NavigationUI.setupWithNavController(bottomNav, navController)

        // Controlliamo se l'app è stata aperta da una notifica!
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return

        val action = intent.action

        // --- 1. L'UTENTE È ENTRATO IN UN LUOGO SALVATO ---
        if (action == "GEOFENCE_ENTER") {
            val locationId = intent.getIntExtra("LOCATION_ID", -1)
            val name = intent.getStringExtra("LOCATION_NAME") ?: ""
            val lat = intent.getDoubleExtra("LOCATION_LAT", 0.0)
            val lng = intent.getDoubleExtra("LOCATION_LNG", 0.0)
            val type = intent.getStringExtra("LOCATION_TYPE") ?: "Libero"

            if (locationId != -1) {
                // Apriamo in automatico il fragment di Aggiunta Parcheggio pre-compilato!
                val form = AddParkingFragment()
                val b = Bundle()
                b.putInt("location_id", locationId)
                b.putString("name", name)
                b.putDouble("lat", lat)
                b.putDouble("lng", lng)
                b.putString("type", type)
                b.putBoolean("is_location_locked", true) // Blocca coordinate
                form.arguments = b
                form.show(supportFragmentManager, "AddParkingDialog")
            }
        }

        // --- 2. L'UTENTE È USCITO DA UN LUOGO DOVE AVEVA UN PARCHEGGIO ATTIVO ---
        else if (action == "GEOFENCE_EXIT") {
            val parkingId = intent.getIntExtra("PARKING_ID", -1)
            val vehicleName = intent.getStringExtra("VEHICLE_NAME") ?: "il tuo veicolo"

            if (parkingId != -1) {
                // Come richiesto dalle tue direttive (Punto 3.3): Chiediamo CONFERMA all'utente
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