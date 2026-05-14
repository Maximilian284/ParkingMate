package com.example.parkingmate

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.parkingmate.adapter.SavedLocationAdapter
import com.example.parkingmate.data.AppDatabase
import com.example.parkingmate.viewmodel.ParkMateViewModel
import com.example.parkingmate.viewmodel.ParkMateViewModelFactory
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.launch

class SavedLocationsFragment : Fragment(R.layout.fragment_saved_locations) {

    // 1. Inizializza il ViewModel condiviso
    private val viewModel: ParkMateViewModel by activityViewModels {
        val db = AppDatabase.getDatabase(requireContext().applicationContext)
        ParkMateViewModelFactory(requireActivity().application, db.appDao())
    }

    private lateinit var adapter: SavedLocationAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 2. Configura la Toolbar e il tasto "+"
        val toolbar = view.findViewById<MaterialToolbar>(R.id.topAppBarLocations)
        toolbar.inflateMenu(R.menu.menu_vehicles)

        toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_add -> {
                    val addParkingForm = AddParkingFragment()
                    addParkingForm.show(childFragmentManager, "AddParkingDialog")
                    true
                }
                else -> false
            }
        }

        // 3. Configura la RecyclerView
        val rv = view.findViewById<RecyclerView>(R.id.rvLocations)
        adapter = SavedLocationAdapter(
            locations = emptyList(),
            onItemClick = { location ->
                val form = AddParkingFragment()
                val b = Bundle()
                b.putInt("location_id", location.id)
                b.putString("name", location.name)
                b.putDouble("lat", location.latitude)
                b.putDouble("lng", location.longitude)
                b.putString("type", location.defaultType)
                b.putBoolean("is_location_locked", true)
                b.putDouble("cost", location.defaultCost)
                b.putDouble("initialCost", location.initialCost)
                b.putDouble("maxCost", location.maxCost)
                form.arguments = b
                form.show(childFragmentManager, "AddParkingDialog")
            },
            onEditClick = { location ->
                val form = AddParkingFragment()
                val b = Bundle()
                b.putInt("location_id", location.id)
                b.putString("name", location.name)
                b.putDouble("lat", location.latitude)
                b.putDouble("lng", location.longitude)
                b.putString("type", location.defaultType)
                b.putString("notes", location.notes)
                b.putBoolean("is_edit_mode", true)
                b.putDouble("cost", location.defaultCost)
                b.putDouble("initialCost", location.initialCost)
                b.putDouble("maxCost", location.maxCost)
                form.arguments = b
                form.show(childFragmentManager, "AddParkingDialog")
            },
            // --- NUOVA PARTE: CLICK SULL'INTERRUTTORE GEOFENCE ---
            onGeofenceToggle = { location, isChecked ->
                val prefs = requireContext().getSharedPreferences("ParkingMatePrefs", android.content.Context.MODE_PRIVATE)
                val isGlobalEnabled = prefs.getBoolean("geofence_global_enabled", false)

                if (!isGlobalEnabled && isChecked) {
                    android.widget.Toast.makeText(requireContext(), "Attiva prima il Geofencing Globale nelle Impostazioni!", android.widget.Toast.LENGTH_LONG).show()
                    adapter.notifyDataSetChanged() // Riporta l'interruttore su OFF visivamente
                } else {
                    // Salviamo il nuovo stato (acceso/spento) nel Database!
                    viewModel.updateSavedLocation(location.id, location.name, location.latitude, location.longitude, location.defaultType, location.notes, isChecked, location.defaultCost)

                    // CHIAMIAMO IL MOTORE GEOFENCE
                    if (isChecked) {
                        GeofenceHelper.addGeofence(requireContext(), location)
                    } else {
                        GeofenceHelper.removeGeofence(requireContext(), location.id)
                    }
                }
            }
        )
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        // 4. Osserva i dati dal Database in tempo reale!
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.savedLocationsList.collect { list ->
                    adapter.updateData(list) // Aggiorna l'interfaccia istantaneamente
                }
            }
        }
    }
}