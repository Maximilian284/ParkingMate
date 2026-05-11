package com.example.parkingmate

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch

class ProfileFragment : Fragment(R.layout.fragment_profile) {

    private lateinit var prefs: SharedPreferences

    // --- GESTIONE PERMESSO NOTIFICHE (Per Android 13+) ---
    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(requireContext(), "Permesso notifiche negato. Non riceverai gli avvisi!", Toast.LENGTH_LONG).show()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Inizializziamo il file di salvataggio invisibile del telefono
        prefs = requireContext().getSharedPreferences("ParkingMatePrefs", Context.MODE_PRIVATE)

        val switchFixedTicket = view.findViewById<MaterialSwitch>(R.id.switchFixedTicket)
        val layoutFixedSettings = view.findViewById<LinearLayout>(R.id.layoutFixedSettings)
        val btnFixedTimeBefore = view.findViewById<Button>(R.id.btnFixedTimeBefore)

        val switchPeriodic = view.findViewById<MaterialSwitch>(R.id.switchPeriodic)
        val layoutPeriodicSettings = view.findViewById<LinearLayout>(R.id.layoutPeriodicSettings)
        val btnPeriodicInterval = view.findViewById<Button>(R.id.btnPeriodicInterval)

        val switchGeofencing = view.findViewById<MaterialSwitch>(R.id.switchGeofencing)

        // --- 1. CARICAMENTO VALORI SALVATI (o default se è la prima volta) ---
        val isFixedEnabled = prefs.getBoolean("fixed_enabled", true)
        val fixedMinutes = prefs.getInt("fixed_minutes", 15) // Default: 15 min prima

        val isPeriodicEnabled = prefs.getBoolean("periodic_enabled", true)
        val periodicMinutes = prefs.getInt("periodic_minutes", 60) // Default: 60 min

        val isGeofenceEnabled = prefs.getBoolean("geofence_global_enabled", false)

        // --- 2. APPLICAZIONE DEI VALORI ALLA GRAFICA ---
        switchFixedTicket.isChecked = isFixedEnabled
        layoutFixedSettings.alpha = if (isFixedEnabled) 1.0f else 0.5f
        btnFixedTimeBefore.isEnabled = isFixedEnabled
        btnFixedTimeBefore.text = getFixedTimeText(fixedMinutes)

        switchPeriodic.isChecked = isPeriodicEnabled
        layoutPeriodicSettings.alpha = if (isPeriodicEnabled) 1.0f else 0.5f
        btnPeriodicInterval.isEnabled = isPeriodicEnabled
        btnPeriodicInterval.text = getPeriodicTimeText(periodicMinutes)

        switchGeofencing.isChecked = isGeofenceEnabled

        // --- 3. GESTIONE CLICK SUGLI INTERRUTTORI ---
        switchFixedTicket.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("fixed_enabled", isChecked).apply()
            layoutFixedSettings.alpha = if (isChecked) 1.0f else 0.5f
            btnFixedTimeBefore.isEnabled = isChecked
            if (isChecked) checkNotificationPermission()

            // Purtroppo per l'AlarmManager cancellare gli allarmi globalmente è complesso,
            // Ma la nostra funzione AlarmHelper prima di suonare controlla questa preferenza!
            // Quindi se è disattivata, le sveglie verranno ignorate silenziosamente.
        }

        switchPeriodic.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("periodic_enabled", isChecked).apply()
            layoutPeriodicSettings.alpha = if (isChecked) 1.0f else 0.5f
            btnPeriodicInterval.isEnabled = isChecked
            if (isChecked) checkNotificationPermission()

            // ATTIVIAMO O SPEGNIAMO IL WORKMANAGER
            if (isChecked) {
                WorkManagerHelper.startOrUpdatePeriodicWork(requireContext())
            } else {
                WorkManagerHelper.stopPeriodicWork(requireContext())
            }
        }

        switchGeofencing.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("geofence_global_enabled", isChecked).apply()
            if (isChecked) {
                checkNotificationPermission()
                Toast.makeText(requireContext(), "Geofencing attivato! Assicurati di avere la posizione sempre consentita.", Toast.LENGTH_LONG).show()
            }
        }

        // --- 4. GESTIONE CLICK SUI PULSANTI DELLE TEMPISTICHE ---
        btnFixedTimeBefore.setOnClickListener {
            val options = arrayOf("5 minuti prima", "15 minuti prima", "30 minuti prima", "1 ora prima")
            val values = intArrayOf(5, 15, 30, 60)
            val currentIndex = values.indexOf(prefs.getInt("fixed_minutes", 15)).takeIf { it >= 0 } ?: 1

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Preavviso Scadenza")
                .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                    val selectedMinutes = values[which]
                    prefs.edit().putInt("fixed_minutes", selectedMinutes).apply()
                    btnFixedTimeBefore.text = options[which]
                    dialog.dismiss()
                    // Si applicherà automaticamente al prossimo parcheggio creato
                }
                .show()
        }

        btnPeriodicInterval.setOnClickListener {
            val options = arrayOf("Ogni 15 minuti", "Ogni 30 minuti", "Ogni 1 ora", "Ogni 2 ore")
            val values = intArrayOf(15, 30, 60, 120)
            val currentIndex = values.indexOf(prefs.getInt("periodic_minutes", 60)).takeIf { it >= 0 } ?: 2

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Frequenza Promemoria")
                .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                    val selectedMinutes = values[which]
                    prefs.edit().putInt("periodic_minutes", selectedMinutes).apply()
                    btnPeriodicInterval.text = options[which]
                    dialog.dismiss()

                    // AGGIORNIAMO IL WORKMANAGER IN TEMPO REALE CON LA NUOVA VELOCITÀ!
                    WorkManagerHelper.startOrUpdatePeriodicWork(requireContext())
                }
                .show()
        }
    }

    // Funzione per chiedere il permesso delle notifiche (solo per Android 13 o superiore)
    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Funzioni per tradurre i numeri in testo per i bottoni
    private fun getFixedTimeText(minutes: Int): String {
        return when (minutes) {
            5 -> "5 min prima"
            15 -> "15 min prima"
            30 -> "30 min prima"
            60 -> "1 ora prima"
            else -> "$minutes min prima"
        }
    }

    private fun getPeriodicTimeText(minutes: Int): String {
        return when (minutes) {
            15 -> "Ogni 15 min"
            30 -> "Ogni 30 min"
            60 -> "Ogni 1 ora"
            120 -> "Ogni 2 ore"
            else -> "Ogni $minutes min"
        }
    }
}