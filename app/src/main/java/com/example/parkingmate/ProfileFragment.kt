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

    // Salviamo i bottoni a livello di classe così l'app li trova sempre
    private var switchGeofencing: MaterialSwitch? = null
    private var switchEffort: MaterialSwitch? = null

    // Launchers: si limitano a risvegliare la schermata, il controllo vero lo fa onResume!
    private val effortPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        checkPermissionsAndFixSwitches()
    }

    private val geofencePermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        checkPermissionsAndFixSwitches()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        prefs = requireContext().getSharedPreferences("ParkingMatePrefs", Context.MODE_PRIVATE)

        val switchFixedTicket = view.findViewById<MaterialSwitch>(R.id.switchFixedTicket)
        val layoutFixedSettings = view.findViewById<LinearLayout>(R.id.layoutFixedSettings)
        val btnFixedTimeBefore = view.findViewById<Button>(R.id.btnFixedTimeBefore)

        val switchPeriodic = view.findViewById<MaterialSwitch>(R.id.switchPeriodic)
        val layoutPeriodicSettings = view.findViewById<LinearLayout>(R.id.layoutPeriodicSettings)
        val btnPeriodicInterval = view.findViewById<Button>(R.id.btnPeriodicInterval)

        switchGeofencing = view.findViewById(R.id.switchGeofencing)
        switchEffort = view.findViewById(R.id.switchEffort)

        // Valori di default
        val isFixedEnabled = prefs.getBoolean("fixed_enabled", true)
        val fixedMinutes = prefs.getInt("fixed_minutes", 15)

        val isPeriodicEnabled = prefs.getBoolean("periodic_enabled", true)
        val periodicMinutes = prefs.getInt("periodic_minutes", 60)

        switchFixedTicket.isChecked = isFixedEnabled
        layoutFixedSettings.alpha = if (isFixedEnabled) 1.0f else 0.5f
        btnFixedTimeBefore.isEnabled = isFixedEnabled
        btnFixedTimeBefore.text = getFixedTimeText(fixedMinutes)

        switchPeriodic.isChecked = isPeriodicEnabled
        layoutPeriodicSettings.alpha = if (isPeriodicEnabled) 1.0f else 0.5f
        btnPeriodicInterval.isEnabled = isPeriodicEnabled
        btnPeriodicInterval.text = getPeriodicTimeText(periodicMinutes)

        switchGeofencing?.isChecked = prefs.getBoolean("geofence_global_enabled", false)
        switchEffort?.isChecked = prefs.getBoolean("effort_global_enabled", false)

        // --- EVENTI CLICK STANDARD ---
        switchFixedTicket.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("fixed_enabled", isChecked).apply()
            layoutFixedSettings.alpha = if (isChecked) 1.0f else 0.5f
            btnFixedTimeBefore.isEnabled = isChecked
        }

        switchPeriodic.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("periodic_enabled", isChecked).apply()
            layoutPeriodicSettings.alpha = if (isChecked) 1.0f else 0.5f
            btnPeriodicInterval.isEnabled = isChecked

            if (isChecked) WorkManagerHelper.startOrUpdatePeriodicWork(requireContext())
            else WorkManagerHelper.stopPeriodicWork(requireContext())
        }

        setupSpecialSwitchesListeners()

        btnFixedTimeBefore.setOnClickListener {
            val options = arrayOf("5 minuti prima", "15 minuti prima", "30 minuti prima", "1 ora prima")
            val values = intArrayOf(5, 15, 30, 60)
            val currentIndex = values.indexOf(prefs.getInt("fixed_minutes", 15)).takeIf { it >= 0 } ?: 1

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Preavviso Scadenza")
                .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                    prefs.edit().putInt("fixed_minutes", values[which]).apply()
                    btnFixedTimeBefore.text = options[which]
                    dialog.dismiss()
                }.show()
        }

        btnPeriodicInterval.setOnClickListener {
            val options = arrayOf("Ogni 15 minuti", "Ogni 30 minuti", "Ogni 1 ora", "Ogni 2 ore")
            val values = intArrayOf(15, 30, 60, 120)
            val currentIndex = values.indexOf(prefs.getInt("periodic_minutes", 60)).takeIf { it >= 0 } ?: 2

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Frequenza Promemoria")
                .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                    prefs.edit().putInt("periodic_minutes", values[which]).apply()
                    btnPeriodicInterval.text = options[which]
                    dialog.dismiss()
                    WorkManagerHelper.startOrUpdatePeriodicWork(requireContext())
                }.show()
        }
    }

    // Viene chiamato automaticamente da Android appena si chiude il popup dei permessi
    override fun onResume() {
        super.onResume()
        checkPermissionsAndFixSwitches()
    }

    private fun setupSpecialSwitchesListeners() {
        switchGeofencing?.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("geofence_global_enabled", isChecked).apply()
            if (isChecked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    geofencePermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
                }
            }
        }

        switchEffort?.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("effort_global_enabled", isChecked).apply()
            if (isChecked) {
                val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) perms.add(Manifest.permission.ACTIVITY_RECOGNITION)

                val missing = perms.filter { ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED }
                if (missing.isNotEmpty()) {
                    effortPermissionLauncher.launch(missing.toTypedArray())
                }
            }
        }
    }

    // IL MOTORE INFALLIBILE: Controlla la realtà dei fatti
    private fun checkPermissionsAndFixSwitches() {
        val context = context ?: return

        // 1. Controllo Effort Score
        val isEffortOn = prefs.getBoolean("effort_global_enabled", false)
        if (isEffortOn) {
            val hasLoc = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val hasAct = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

            if (!hasLoc || !hasAct) {
                forceSwitchOff(switchEffort, "effort_global_enabled", "Permessi negati. Effort Score disattivato.")
            }
        }

        // 2. Controllo Geofencing
        val isGeofenceOn = prefs.getBoolean("geofence_global_enabled", false)
        if (isGeofenceOn && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val hasBgLoc = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (!hasBgLoc) {
                forceSwitchOff(switchGeofencing, "geofence_global_enabled", "Permesso 'Sempre' negato. Geofencing disattivato.")
            }
        }
    }

    private fun forceSwitchOff(switchView: MaterialSwitch?, prefKey: String, message: String) {
        // Evitiamo di mandare Toast continui se è già spento
        if (switchView?.isChecked == false && !prefs.getBoolean(prefKey, false)) return

        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()

        // Salviamo lo stato spento
        prefs.edit().putBoolean(prefKey, false).apply()

        // Sganciamo il listener, spegniamo graficamente e lo riattacchiamo
        switchView?.setOnCheckedChangeListener(null)
        switchView?.isChecked = false
        setupSpecialSwitchesListeners()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        switchGeofencing = null
        switchEffort = null
    }

    private fun getFixedTimeText(minutes: Int) = when (minutes) {
        5 -> "5 min prima"; 15 -> "15 min prima"; 30 -> "30 min prima"; 60 -> "1 ora prima"; else -> "$minutes min prima"
    }

    private fun getPeriodicTimeText(minutes: Int) = when (minutes) {
        15 -> "Ogni 15 min"; 30 -> "Ogni 30 min"; 60 -> "Ogni 1 ora"; 120 -> "Ogni 2 ore"; else -> "Ogni $minutes min"
    }
}