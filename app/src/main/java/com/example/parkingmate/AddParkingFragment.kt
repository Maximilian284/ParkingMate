package com.example.parkingmate

import android.Manifest
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.location.Location
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
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

        // --- 1. RECUPERO STATI E BUNDLE ---
        val isVehicleLocked = arguments?.getBoolean("is_vehicle_locked", false) ?: false
        val isEditMode = arguments?.getBoolean("is_edit_mode", false) ?: false
        // Se c'è un location_id e NON siamo in modalità modifica, significa che stiamo parcheggiando da un Luogo (è bloccato)
        val isLocationLocked = arguments?.containsKey("location_id") == true && !isEditMode
        editingLocationId = arguments?.getInt("location_id", -1)?.takeIf { it != -1 }

        // --- 2. COLLEGAMENTO UI ---
        val toggleGroup = view.findViewById<MaterialButtonToggleGroup>(R.id.toggleGroupAction)
        val cbHeart = view.findViewById<CheckBox>(R.id.cbSaveAsFavorite)
        val layoutHourlyCosts = view.findViewById<LinearLayout>(R.id.layoutHourlyCosts)

        // Campi Luogo
        val tilLocationInput = view.findViewById<View>(R.id.tilLocationName)
        val etLocationName = view.findViewById<TextInputEditText>(R.id.etLocationName)
        val tvDisplayLocation = view.findViewById<TextView>(R.id.tvDisplayLocationName)
        val tilSelectExistingLocation = view.findViewById<View>(R.id.tilSelectExistingLocation)
        val actvSelectLocation = view.findViewById<AutoCompleteTextView>(R.id.actvSelectLocation)

        // Campi Veicolo
        val layoutVehicle = view.findViewById<LinearLayout>(R.id.layoutVehicleSelection)
        val tilSelectVehicle = view.findViewById<View>(R.id.tilSelectVehicle)
        val actvVehicle = view.findViewById<AutoCompleteTextView>(R.id.actvSelectVehicle)
        val tvDisplayVehicle = view.findViewById<TextView>(R.id.tvDisplayVehicle)

        // Campi Tipo
        val tilParkingType = view.findViewById<View>(R.id.tilParkingType)
        val actvParkingType = view.findViewById<AutoCompleteTextView>(R.id.actvParkingType)
        val tvDisplayParkingType = view.findViewById<TextView>(R.id.tvDisplayParkingType)

        // Bottoni vari
        val btnGetLocation = view.findViewById<Button>(R.id.btnGetLocation)
        val btnDeleteLoc = view.findViewById<Button>(R.id.btnDeleteLocation)
        val btnSaveEverything = view.findViewById<Button>(R.id.btnSaveEverything)
        val cardMap = view.findViewById<View>(R.id.cardMap)

        // --- 3. INIZIALIZZAZIONE MAPPA E GPS ---
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        mapView = view.findViewById(R.id.mapViewMini)
        mapView.onCreate(savedInstanceState)

        selectedLatitude = arguments?.getDouble("lat", 0.0) ?: 0.0
        selectedLongitude = arguments?.getDouble("lng", 0.0) ?: 0.0

        mapView.getMapAsync { map ->
            googleMap = map
            if (selectedLatitude != 0.0 && selectedLongitude != 0.0) {
                val latLng = LatLng(selectedLatitude, selectedLongitude)
                googleMap?.addMarker(MarkerOptions().position(latLng).title("Luogo salvato"))
                googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
            }

            if (!isLocationLocked) {
                googleMap?.setOnMapClickListener { latLng ->
                    googleMap?.clear()
                    googleMap?.addMarker(MarkerOptions().position(latLng).title("Parcheggio qui"))
                    selectedLatitude = latLng.latitude
                    selectedLongitude = latLng.longitude
                }
            } else {
                googleMap?.uiSettings?.setAllGesturesEnabled(false) // Blocca tocco se il luogo è fisso
            }
        }

        btnGetLocation.setOnClickListener {
            locationPermissionRequest.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }

        view.findViewById<Button>(R.id.btnAddPhoto).setOnClickListener {
            takePictureLauncher.launch(null)
        }

        // --- 4. LOGICA BASE MENU A TENDINA E TOGGLE ---
        val parkingTypes = arrayOf("Gratis", "All'ora", "Già Pagato / Fisso")
        actvParkingType.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, parkingTypes))
        actvParkingType.setOnItemClickListener { _, _, position, _ ->
            layoutHourlyCosts.visibility = if (parkingTypes[position] == "All'ora") View.VISIBLE else View.GONE
        }

        toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked && !isVehicleLocked && !isLocationLocked) {
                val isParking = (checkedId == R.id.btnParkVehicle)
                layoutVehicle.visibility = if (isParking) View.VISIBLE else View.GONE
                cbHeart.visibility = if (isParking) View.VISIBLE else View.GONE
            }
        }

        // --- 5. LA MAGIA: GESTIONE DEGLI STATI DINAMICI (SENZA CAMPI GRIGI) ---

        // Referenze per nascondere i campi
        val tilNotes = view.findViewById<View>(R.id.tilNotes)
        val btnAddPhoto = view.findViewById<Button>(R.id.btnAddPhoto)

        if (isVehicleLocked || isLocationLocked || isEditMode) {
            // Niente opzioni "Solo preferito/Parcheggia", stiamo facendo un'azione specifica.
            toggleGroup.visibility = View.GONE
            cbHeart.visibility = View.GONE
            if (!isEditMode) layoutVehicle.visibility = View.VISIBLE
        }

        if (isEditMode) {
            // MODALITÀ MODIFICA LUOGO -> Niente Toggle, mostro Elimina
            btnDeleteLoc.visibility = View.VISIBLE
            btnDeleteLoc.setOnClickListener {
                editingLocationId?.let { id ->
                    viewModel.removeSavedLocation(com.example.parkingmate.data.SavedLocation(id = id, name = "", latitude = 0.0, longitude = 0.0))
                    Toast.makeText(requireContext(), "Luogo Eliminato", Toast.LENGTH_SHORT).show()
                    dismiss()
                }
            }

            // Pre-compilazione campi
            etLocationName.setText(arguments?.getString("name", ""))
            view.findViewById<TextInputEditText>(R.id.etNotes).setText(arguments?.getString("notes", ""))
            actvParkingType.setText(arguments?.getString("type", ""), false)
            if (arguments?.getString("type") == "All'ora") layoutHourlyCosts.visibility = View.VISIBLE
        }

        if (isVehicleLocked) {
            // ARRIVO DA TAB VEICOLI -> ASSOCIAZIONE LUOGO
            tilSelectVehicle.visibility = View.GONE
            tvDisplayVehicle.visibility = View.VISIBLE
            tvDisplayVehicle.text = "Veicolo: " + arguments?.getString("preselected_vehicle_name")

            tilLocationInput.visibility = View.GONE
            tilSelectExistingLocation.visibility = View.VISIBLE
            cardMap.visibility = View.GONE

            // INIZIALMENTE NASCONDIAMO TUTTO FINCHÉ NON SCEGLIE IL LUOGO
            tilParkingType.visibility = View.GONE
            tilNotes.visibility = View.GONE
            btnAddPhoto.visibility = View.GONE
            btnSaveEverything.visibility = View.GONE // Nascondiamo anche Salva!

            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.savedLocationsList.collect { locations ->
                        val names = locations.map { it.name }
                        actvSelectLocation.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, names))

                        actvSelectLocation.setOnItemClickListener { _, _, pos, _ ->
                            val loc = locations[pos]
                            selectedLatitude = loc.latitude
                            selectedLongitude = loc.longitude

                            // AUTO-COMPILAZIONE DEI DATI DEL LUOGO SCELTO
                            actvParkingType.setText(loc.defaultType, false)
                            view.findViewById<TextInputEditText>(R.id.etNotes).setText(loc.notes ?: "")

                            // ORA FACCIAMO APPARIRE I CAMPI PER FARLI VEDERE/MODIFICARE
                            tilParkingType.visibility = View.VISIBLE
                            tilNotes.visibility = View.VISIBLE
                            btnAddPhoto.visibility = View.VISIBLE
                            btnSaveEverything.visibility = View.VISIBLE

                            layoutHourlyCosts.visibility = if (loc.defaultType == "All'ora") View.VISIBLE else View.GONE
                        }
                    }
                }
            }
        }

        if (isLocationLocked) {
            // ARRIVO DA TAB LUOGHI -> ASSOCIAZIONE VEICOLO
            tilLocationInput.visibility = View.GONE
            tvDisplayLocation.visibility = View.VISIBLE
            tvDisplayLocation.text = "Luogo: " + arguments?.getString("name")
            btnGetLocation.visibility = View.GONE

            tilParkingType.visibility = View.GONE
            tvDisplayParkingType.visibility = View.VISIBLE
            val tType = arguments?.getString("type") ?: "Gratis"
            tvDisplayParkingType.text = "Tariffa: $tType"
            if (tType == "All'ora") layoutHourlyCosts.visibility = View.VISIBLE
        }

        // --- 6. CARICAMENTO VEICOLI ---
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.vehiclesList.collect { vehicles ->
                    currentVehicles = vehicles
                    val vehicleNames = vehicles.map { "${it.name} (${it.type})" }.toMutableList()
                    actvVehicle.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, vehicleNames))
                }
            }
        }

        // --- 7. TASTO SALVA FINALE ---
        btnSaveEverything.setOnClickListener {
            // Se non c'è il toggle (perché nascosto), stiamo parcheggiando tranne nel caso della modifica!
            val isParkMode = isVehicleLocked || isLocationLocked || (toggleGroup.visibility == View.VISIBLE && toggleGroup.checkedButtonId == R.id.btnParkVehicle)

            val name = when {
                isVehicleLocked -> actvSelectLocation.text.toString()
                isLocationLocked -> arguments?.getString("name") ?: ""
                else -> etLocationName.text.toString()
            }

            val type = actvParkingType.text.toString().takeIf { it.isNotEmpty() } ?: arguments?.getString("type") ?: "Gratis"
            val notes = view.findViewById<TextInputEditText>(R.id.etNotes).text.toString()

            if (name.isEmpty() || type.isEmpty()) {
                Toast.makeText(requireContext(), "Compila Nome e Tipo di Tariffa!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (selectedLatitude == 0.0 && !isVehicleLocked) {
                Toast.makeText(requireContext(), "Seleziona una posizione sulla mappa!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (isParkMode) {
                // PARCHEGGIO
                val selectedVehicleName = if (isVehicleLocked) arguments?.getString("preselected_vehicle_name") else actvVehicle.text.toString()
                val vehicle = currentVehicles.find { "${it.name} (${it.type})" == selectedVehicleName }

                if (vehicle == null) {
                    Toast.makeText(requireContext(), "Seleziona un veicolo!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val costStr = view.findViewById<TextInputEditText>(R.id.etCostInitial).text.toString()
                val initialCost = if (costStr.isNotEmpty()) costStr.toDouble() else 0.0

                viewModel.addParkingSession(vehicle.id, type, selectedLatitude, selectedLongitude, notes, savedPhotoPath, initialCost)

                if (cbHeart.isChecked && cbHeart.visibility == View.VISIBLE) {
                    viewModel.addSavedLocation(name, selectedLatitude, selectedLongitude, type, notes)
                }
                Toast.makeText(requireContext(), "Parcheggio Avviato!", Toast.LENGTH_SHORT).show()

            } else {
                // SALVATAGGIO / MODIFICA LUOGO
                if (isEditMode && editingLocationId != null) {
                    viewModel.updateSavedLocation(editingLocationId!!, name, selectedLatitude, selectedLongitude, type, notes)
                    Toast.makeText(requireContext(), "Luogo Modificato!", Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.addSavedLocation(name, selectedLatitude, selectedLongitude, type, notes)
                    Toast.makeText(requireContext(), "Luogo Salvato!", Toast.LENGTH_SHORT).show()
                }
            }
            dismiss()
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