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
import com.example.parkingmate.adapter.VehicleAdapter
import com.example.parkingmate.data.AppDatabase
import com.example.parkingmate.viewmodel.ParkMateViewModel
import com.example.parkingmate.viewmodel.ParkMateViewModelFactory
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch

class VehiclesFragment : Fragment(R.layout.fragment_vehicles) {

    private val viewModel: ParkMateViewModel by activityViewModels {
        val db = AppDatabase.getDatabase(requireContext().applicationContext)
        ParkMateViewModelFactory(db.appDao())
    }

    private lateinit var adapter: VehicleAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Inizializza la lista (RecyclerView)
        val rv = view.findViewById<RecyclerView>(R.id.rvVehicles)
        adapter = VehicleAdapter(emptyList()) { vehicle ->
            viewModel.removeVehicle(vehicle) // Funzione per cancellare quando clicchi il cestino
        }
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        // 2. Osserva i dati dal ViewModel e aggiorna l'interfaccia
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.vehiclesList.collect { list ->
                    adapter.updateData(list)
                }
            }
        }

        // 3. Bottone per aggiungere (per ora aggiunge un veicolo fisso per testare)
        view.findViewById<FloatingActionButton>(R.id.fabAddVehicle).setOnClickListener {
            // Qui poi metteremo un popup, per ora testiamo se il database risponde
            viewModel.addVehicle("Nuova Auto ${adapter.itemCount + 1}", "Car")
        }
    }
}