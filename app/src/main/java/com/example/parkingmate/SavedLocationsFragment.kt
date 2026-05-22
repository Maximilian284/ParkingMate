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
import com.example.parkingmate.data.SavedLocation
import com.example.parkingmate.viewmodel.ParkMateViewModel
import com.example.parkingmate.viewmodel.ParkMateViewModelFactory
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class SavedLocationsFragment : Fragment(R.layout.fragment_saved_locations) {

    private val viewModel: ParkMateViewModel by activityViewModels {
        val db = AppDatabase.getDatabase(requireContext().applicationContext)
        ParkMateViewModelFactory(requireActivity().application, db.appDao())
    }

    private lateinit var adapter: SavedLocationAdapter
    private var allLocations: List<SavedLocation> = emptyList()
    private var currentFilter = "Tutte"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<MaterialToolbar>(R.id.topAppBarLocations)
        toolbar.inflateMenu(R.menu.menu_vehicles)

        toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_add -> {
                    val addParkingForm = AddParkingFragment()
                    addParkingForm.show(childFragmentManager, "AddParkingDialog")
                    true
                }
                R.id.action_filter -> {
                    showFilterDialog()
                    true
                }
                else -> false
            }
        }

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
                b.putString("notes", location.notes)
                b.putDouble("cost", location.defaultCost)
                b.putDouble("initialCost", location.initialCost)
                b.putDouble("maxCost", location.maxCost)
                b.putString("photoPath", location.photoPath) // <-- FIX SALVA DATI!
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
                b.putDouble("cost", location.defaultCost)
                b.putDouble("initialCost", location.initialCost)
                b.putDouble("maxCost", location.maxCost)
                b.putString("photoPath", location.photoPath) // <-- FIX SALVA DATI!
                b.putBoolean("is_geofence_enabled", location.isGeofenceEnabled)
                b.putBoolean("is_edit_mode", true)
                form.arguments = b
                form.show(childFragmentManager, "AddParkingDialog")
            }
        )
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.savedLocationsList.collect { list ->
                    allLocations = list
                    applyFilter()
                }
            }
        }
    }

    private fun showFilterDialog() {
        val opzioni = arrayOf("Tutte", "Gratis", "All'ora", "Costo Fisso")
        val currentIndex = opzioni.indexOf(currentFilter).takeIf { it >= 0 } ?: 0

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Filtra per Tariffa")
            .setSingleChoiceItems(opzioni, currentIndex) { dialog, which ->
                currentFilter = opzioni[which]
                applyFilter()
                dialog.dismiss()
            }
            .show()
    }

    private fun applyFilter() {
        val filteredList = if (currentFilter == "Tutte") {
            allLocations
        } else {
            allLocations.filter {
                it.defaultType == currentFilter || (currentFilter == "Costo Fisso" && it.defaultType == "Già Pagato")
            }
        }
        adapter.updateData(filteredList)
    }
}