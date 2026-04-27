package com.example.parkingmate

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.parkingmate.adapter.VehicleAdapter
import com.example.parkingmate.data.AppDatabase
import com.example.parkingmate.data.Vehicle
import com.example.parkingmate.viewmodel.ParkMateViewModel
import com.example.parkingmate.viewmodel.ParkMateViewModelFactory
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class VehiclesFragment : Fragment(R.layout.fragment_vehicles) {

    private val viewModel: ParkMateViewModel by activityViewModels {
        val db = AppDatabase.getDatabase(requireContext().applicationContext)
        ParkMateViewModelFactory(db.appDao())
    }

    private lateinit var adapter: VehicleAdapter

    // I tipi richiesti da te
    private val vehicleTypes = arrayOf("Macchina", "Moto", "Scooter", "Bici", "Monopattino")

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView(view)
        setupToolbar(view)
        observeData()
    }

    private fun setupRecyclerView(view: View) {
        val rv = view.findViewById<RecyclerView>(R.id.rvVehicles)
        adapter = VehicleAdapter(
            vehicles = emptyList(),
            onEditClick = { vehicle -> showVehicleDialog(vehicle) }, // Modifica
            onDeleteClick = { vehicle -> viewModel.removeVehicle(vehicle) } // Elimina
        )
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter
    }

    private fun setupToolbar(view: View) {
        val toolbar = view.findViewById<MaterialToolbar>(R.id.topAppBar)

        // 1. FORZIAMO IL CARICAMENTO DELLE ICONE (+ e Filtro)
        toolbar.inflateMenu(R.menu.menu_vehicles)

        // 2. GESTIAMO I CLICK
        toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_add -> {
                    showVehicleDialog(null) // Apre il popup vuoto per creare
                    true
                }
                R.id.action_filter -> {
                    showFilterDialog()      // Apre il popup dei filtri
                    true
                }
                else -> false
            }
        }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Legge i dati dal DB
                viewModel.vehiclesList.collect { list ->
                    adapter.updateData(list)
                }
            }
        }
    }

    // --- POPUP AGGIUNGI / MODIFICA ---
    private fun showVehicleDialog(vehicleToEdit: Vehicle?) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_vehicle, null)
        val etName = dialogView.findViewById<TextInputEditText>(R.id.etVehicleName)
        val actvType = dialogView.findViewById<AutoCompleteTextView>(R.id.actvVehicleType)

        // Imposta il menu a tendina
        actvType.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, vehicleTypes))

        // Se sto modificando un veicolo esistente, pre-compilo i campi!
        if (vehicleToEdit != null) {
            etName.setText(vehicleToEdit.name)
            actvType.setText(vehicleToEdit.type, false)
        }

        val title = if (vehicleToEdit == null) "Aggiungi Veicolo" else "Modifica Veicolo"

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setView(dialogView)
            .setPositiveButton("Salva") { _, _ ->
                val name = etName.text.toString().trim()
                val type = actvType.text.toString()

                if (name.isNotEmpty() && type.isNotEmpty()) {
                    if (vehicleToEdit == null) {
                        viewModel.addVehicle(name, type) // Crea nuovo
                    } else {
                        // Modifica esistente (Dovrai aggiungere updateVehicle nel ViewModel!)
                        val updatedVehicle = vehicleToEdit.copy(name = name, type = type)
                        viewModel.updateVehicle(updatedVehicle)
                    }
                }
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    // --- POPUP FILTRI ---
    private fun showFilterDialog() {
        val opzioni = arrayOf("Tutti", "Solo Macchine", "Solo Moto", "Ordine Alfabetico (A-Z)")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Filtra per:")
            .setItems(opzioni) { _, which ->
                // Qui per ora ordiniamo la lista localmente
                val listaAttuale = viewModel.vehiclesList.value
                val listaFiltrata = when (which) {
                    1 -> listaAttuale.filter { it.type == "Macchina" }
                    2 -> listaAttuale.filter { it.type == "Moto" }
                    3 -> listaAttuale.sortedBy { it.name.lowercase() }
                    else -> listaAttuale // Tutti
                }
                adapter.updateData(listaFiltrata)
            }
            .show()
    }
}