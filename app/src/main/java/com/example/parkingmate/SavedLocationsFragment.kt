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
                b.putDouble("cost", location.defaultCost)
                b.putString("notes", location.notes)
                b.putString("photoPath", location.photoPath)
                b.putDouble("initialCost", location.initialCost)
                b.putDouble("maxCost", location.maxCost)
                b.putBoolean("is_location_locked", true)
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
                b.putString("photoPath", location.photoPath)
                b.putDouble("cost", location.defaultCost)
                b.putDouble("initialCost", location.initialCost)
                b.putDouble("maxCost", location.maxCost)
                b.putBoolean("is_geofence_enabled", location.isGeofenceEnabled) // <--- NUOVO: Passiamo lo stato!
                b.putBoolean("is_edit_mode", true)
                form.arguments = b
                form.show(childFragmentManager, "AddParkingDialog")
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