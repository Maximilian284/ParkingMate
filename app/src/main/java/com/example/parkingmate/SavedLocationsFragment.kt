package com.example.parkingmate

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.MaterialToolbar

class SavedLocationsFragment : Fragment(R.layout.fragment_saved_locations) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<MaterialToolbar>(R.id.topAppBarLocations)

        // Ricicliamo il menu dei veicoli visto che ha il "+" (poi potrai fare un menu separato se serve)
        toolbar.inflateMenu(R.menu.menu_vehicles)

        toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_add -> {
                    // MODO CORRETTO E SICURO PER APRIRE IL FULL-SCREEN:
                    val addParkingForm = AddParkingFragment()
                    addParkingForm.show(childFragmentManager, "AddParkingDialog")
                    true
                }
                else -> false
            }
        }
    }
}