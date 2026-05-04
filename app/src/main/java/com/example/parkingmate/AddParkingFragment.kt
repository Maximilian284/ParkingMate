package com.example.parkingmate

import android.Manifest
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.location.Location
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.parkingmate.data.AppDatabase
import com.example.parkingmate.data.Vehicle
import com.example.parkingmate.viewmodel.ParkMateViewModel
import com.example.parkingmate.viewmodel.ParkMateViewModelFactory
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import java.io.File

class AddParkingFragment : DialogFragment() {

    private val viewModel: ParkMateViewModel by activityViewModels {
        val db = AppDatabase.getDatabase(requireContext().applicationContext)
        ParkMateViewModelFactory(db.appDao())
    }

    private lateinit var mapView: MapView
    private var googleMap: GoogleMap? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var selectedLatitude: Double = 0.0
    private var selectedLongitude: Double = 0.0
    private var currentVehicles: List<Vehicle> = emptyList() // Per ricordare i veicoli
    private var savedPhotoPath: String? = null // Per ricordare la foto scattata

    private var editingLocationId: Int? = null

    // --- 1. MOTORE DELLA FOTOCAMERA ---
    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
        if (bitmap != null) {
            // Salva la foto nella memoria interna del telefono
            val file = File(requireContext().filesDir, "park_photo_${System.currentTimeMillis()}.jpg")
            file.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
            }
            savedPhotoPath = file.absolutePath
            Toast.makeText(requireContext(), "Foto salvata con successo!", Toast.LENGTH_SHORT).show()
            view?.findViewById<Button>(R.id.btnAddPhoto)?.text = "Foto Aggiunta ✓"
        }
    }

    private val locationPermissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false)) {
            getCurrentLocation()
        } else {
            Toast.makeText(requireContext(), "Permesso GPS negato.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_add_parking, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<MaterialToolbar>(R.id.toolbarAddParking).setNavigationOnClickListener { dismiss() }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        mapView = view.findViewById(R.id.mapViewMini)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { map ->
            googleMap = map

            // Se abbiamo già delle coordinate (stiamo modificando), centra la mappa lì!
            if (selectedLatitude != 0.0 && selectedLongitude != 0.0) {
                val latLng = LatLng(selectedLatitude, selectedLongitude)
                googleMap?.addMarker(MarkerOptions().position(latLng).title("Luogo salvato"))
                googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
            }

            googleMap?.setOnMapClickListener { latLng ->
                googleMap?.clear()
                googleMap?.addMarker(MarkerOptions().position(latLng).title("Parcheggio qui"))
                selectedLatitude = latLng.latitude
                selectedLongitude = latLng.longitude
            }
        }

        view.findViewById<Button>(R.id.btnGetLocation).setOnClickListener {
            locationPermissionRequest.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }

        // --- BOTTONE FOTOCAMERA ---
        view.findViewById<Button>(R.id.btnAddPhoto).setOnClickListener {
            takePictureLauncher.launch(null) // Apre la fotocamera!
        }

        val toggleGroup = view.findViewById<MaterialButtonToggleGroup>(R.id.toggleGroupAction)
        val layoutVehicle = view.findViewById<LinearLayout>(R.id.layoutVehicleSelection)
        val cbHeart = view.findViewById<CheckBox>(R.id.cbSaveAsFavorite)
        val layoutHourlyCosts = view.findViewById<LinearLayout>(R.id.layoutHourlyCosts)

        toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val isParking = (checkedId == R.id.btnParkVehicle)
                layoutVehicle.visibility = if (isParking) View.VISIBLE else View.GONE
                cbHeart.visibility = if (isParking) View.VISIBLE else View.GONE
            }
        }

        val actvParkingType = view.findViewById<AutoCompleteTextView>(R.id.actvParkingType)
        val parkingTypes = arrayOf("Gratis", "All'ora", "Già Pagato / Fisso")
        actvParkingType.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, parkingTypes))
        actvParkingType.setOnItemClickListener { _, _, position, _ ->
            layoutHourlyCosts.visibility = if (parkingTypes[position] == "All'ora") View.VISIBLE else View.GONE
        }

        val actvVehicle = view.findViewById<AutoCompleteTextView>(R.id.actvSelectVehicle)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.vehiclesList.collect { vehicles ->
                    currentVehicles = vehicles // Salviamo la lista per usarla nel salvataggio
                    val vehicleNames = vehicles.map { "${it.name} (${it.type})" }.toMutableList()
                    actvVehicle.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, vehicleNames))
                }
            }
        }

        // --- IL GRANDE BOTTONE SALVA ---
        view.findViewById<Button>(R.id.btnSaveEverything).setOnClickListener {
            val isParkMode = toggleGroup.checkedButtonId == R.id.btnParkVehicle
            val name = view.findViewById<TextInputEditText>(R.id.etLocationName).text.toString()
            val type = actvParkingType.text.toString()
            val notes = view.findViewById<TextInputEditText>(R.id.etNotes).text.toString()

            // Validazione base
            if (name.isEmpty() || type.isEmpty()) {
                Toast.makeText(requireContext(), "Compila almeno Nome e Tipo di Tariffa!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (selectedLatitude == 0.0) {
                Toast.makeText(requireContext(), "Seleziona una posizione sulla mappa!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (isParkMode) {
                // PARCHEGGIA VEICOLO
                val selectedVehicleName = actvVehicle.text.toString()
                // Troviamo il veicolo corrispondente nella lista
                val vehicle = currentVehicles.find { "${it.name} (${it.type})" == selectedVehicleName }

                if (vehicle == null) {
                    Toast.makeText(requireContext(), "Seleziona un veicolo!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                // Leggiamo il costo iniziale (se c'è)
                val costStr = view.findViewById<TextInputEditText>(R.id.etCostInitial).text.toString()
                val initialCost = if (costStr.isNotEmpty()) costStr.toDouble() else 0.0

                // 1. Salva nel database (Parcheggi attivi)
                viewModel.addParkingSession(vehicle.id, type, selectedLatitude, selectedLongitude, notes, savedPhotoPath, initialCost)

                // 2. Se il cuoricino è premuto, salvalo anche nei luoghi!
                if (cbHeart.isChecked) {
                    viewModel.addSavedLocation(name, selectedLatitude, selectedLongitude, type, notes)
                }
                Toast.makeText(requireContext(), "Parcheggio Avviato!", Toast.LENGTH_SHORT).show()

            }  else {
                if (editingLocationId != null) {
                    viewModel.updateSavedLocation(editingLocationId!!, name, selectedLatitude, selectedLongitude, type, notes)
                    Toast.makeText(requireContext(), "Luogo Modificato!", Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.addSavedLocation(name, selectedLatitude, selectedLongitude, type, notes)
                    Toast.makeText(requireContext(), "Luogo Salvato!", Toast.LENGTH_SHORT).show()
                }
            }

            dismiss() // Chiude la pagina dopo aver salvato!
        }

        // --- AUTO-COMPILAZIONE SE STIAMO MODIFICANDO ---
        arguments?.let { bundle ->
            editingLocationId = bundle.getInt("location_id")
            val name = bundle.getString("name", "")
            val type = bundle.getString("type", "")
            val notes = bundle.getString("notes", "")
            selectedLatitude = bundle.getDouble("lat", 0.0)
            selectedLongitude = bundle.getDouble("lng", 0.0)

            // Riempiamo i campi
            view.findViewById<TextInputEditText>(R.id.etLocationName).setText(name)
            view.findViewById<TextInputEditText>(R.id.etNotes).setText(notes)
            actvParkingType.setText(type, false)

            if (type == "All'ora") {
                layoutHourlyCosts.visibility = View.VISIBLE
            }

            // Scegliamo di default "Parcheggia Veicolo" così l'utente è pronto a parcheggiare
            toggleGroup.check(R.id.btnParkVehicle)
        }

        val btnDeleteLoc = view.findViewById<Button>(R.id.btnDeleteLocation)
        val isVehicleLocked = arguments?.getBoolean("is_vehicle_locked") ?: false
        val isLocationLocked = arguments?.containsKey("location_id") ?: false && !arguments?.getBoolean("is_edit_mode", false)!!

        // Se stiamo modificando un luogo (non in modalità parcheggio rapido), mostriamo Elimina
        if (arguments?.getBoolean("is_edit_mode") == true) {
            btnDeleteLoc.visibility = View.VISIBLE
            btnDeleteLoc.setOnClickListener {
                editingLocationId?.let { id ->
                    // Recupera l'oggetto e chiama viewModel.removeSavedLocation
                    dismiss()
                }
            }
        }

        // Se il veicolo è bloccato (arriviamo dal Tab Veicoli)
        if (isVehicleLocked) {
            val vehicleName = arguments?.getString("preselected_vehicle_name")
            actvVehicle.setText(vehicleName, false)
            actvVehicle.isEnabled = false // BLOCCHIAMO IL CAMPO
            toggleGroup.check(R.id.btnParkVehicle)
        }

        // Se il luogo è bloccato (arriviamo dal click sulla card Luoghi)
        if (isLocationLocked) {
            view.findViewById<TextInputEditText>(R.id.etLocationName).isEnabled = false
            actvParkingType.isEnabled = false
            view.findViewById<Button>(R.id.btnGetLocation).visibility = View.GONE
            // Blocca anche la mappa se vuoi
        }
    }

    @SuppressLint("MissingPermission")
    private fun getCurrentLocation() {
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                val currentLatLng = LatLng(location.latitude, location.longitude)
                googleMap?.clear()
                googleMap?.addMarker(MarkerOptions().position(currentLatLng).title("Sei qui"))
                googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))
                selectedLatitude = location.latitude
                selectedLongitude = location.longitude
            }
        }
    }

    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { super.onPause(); mapView.onPause() }
    override fun onStop() { super.onStop(); mapView.onStop() }
    override fun onDestroyView() { super.onDestroyView(); mapView.onDestroy() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
}