package com.example.parkingmate

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.parkingmate.data.AppDatabase
import com.example.parkingmate.viewmodel.ParkMateViewModel
import com.example.parkingmate.viewmodel.ParkMateViewModelFactory
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButtonToggleGroup
import kotlinx.coroutines.launch

class AddParkingFragment : DialogFragment() {
    private lateinit var mapView: com.google.android.gms.maps.MapView
    // 1. INIZIALIZZIAMO IL VIEWMODEL (Uguale a VehiclesFragment!)
    private val viewModel: ParkMateViewModel by activityViewModels {
        val db = AppDatabase.getDatabase(requireContext().applicationContext)
        ParkMateViewModelFactory(db.appDao())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_add_parking, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mapView = view.findViewById(R.id.mapViewMini)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { googleMap ->
            // Qui dentro (in futuro) metteremo il pin e centreremo la visuale!
            googleMap.uiSettings.isMapToolbarEnabled = false
        }

        view.findViewById<MaterialToolbar>(R.id.toolbarAddParking).setNavigationOnClickListener {
            dismiss()
        }

        val toggleGroup = view.findViewById<MaterialButtonToggleGroup>(R.id.toggleGroupAction)
        val layoutVehicle = view.findViewById<LinearLayout>(R.id.layoutVehicleSelection)
        val cbHeart = view.findViewById<CheckBox>(R.id.cbSaveAsFavorite)
        val layoutHourlyCosts = view.findViewById<LinearLayout>(R.id.layoutHourlyCosts)

        toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                if (checkedId == R.id.btnParkVehicle) {
                    layoutVehicle.visibility = View.VISIBLE
                    cbHeart.visibility = View.VISIBLE
                } else {
                    layoutVehicle.visibility = View.GONE
                    cbHeart.visibility = View.GONE
                    cbHeart.isChecked = false
                }
            }
        }

        val actvParkingType = view.findViewById<AutoCompleteTextView>(R.id.actvParkingType)
        val parkingTypes = arrayOf("Gratis", "All'ora", "Già Pagato / Fisso")
        actvParkingType.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, parkingTypes))

        actvParkingType.setOnItemClickListener { _, _, position, _ ->
            val selectedType = parkingTypes[position]
            layoutHourlyCosts.visibility = if (selectedType == "All'ora") View.VISIBLE else View.GONE
        }

        // --- 2. LOGICA DEI VEICOLI VERI ---
        val actvVehicle = view.findViewById<AutoCompleteTextView>(R.id.actvSelectVehicle)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.vehiclesList.collect { vehicles ->
                    // Trasformiamo gli oggetti "Vehicle" in una lista di Testi da mostrare
                    val vehicleNames = vehicles.map { "${it.name} (${it.type})" }.toMutableList()
                    vehicleNames.add("➕ CREA NUOVO VEICOLO") // Aggiungiamo l'opzione in fondo

                    actvVehicle.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, vehicleNames))

                    actvVehicle.setOnItemClickListener { _, _, position, _ ->
                        val selected = vehicleNames[position]
                        if (selected == "➕ CREA NUOVO VEICOLO") {
                            Toast.makeText(requireContext(), "Vai nel tab Veicoli per crearlo!", Toast.LENGTH_LONG).show()
                            actvVehicle.setText("", false)
                        } else {
                            // Qui abbiamo selezionato un veicolo vero!
                            val selectedVehicle = vehicles[position]
                            // (In futuro useremo selectedVehicle.id per salvarlo nel database)
                        }
                    }
                }
            }
        }
    }

    // --- GESTIONE OBBLIGATORIA DEL CICLO DI VITA DELLA MAPPA ---
    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { super.onPause(); mapView.onPause() }
    override fun onStop() { super.onStop(); mapView.onStop() }
    override fun onDestroyView() { super.onDestroyView(); mapView.onDestroy() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); mapView.onSaveInstanceState(outState) }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
}