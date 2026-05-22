package com.example.parkingmate

import android.Manifest
import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.parkingmate.data.AppDatabase
import com.example.parkingmate.data.SavedLocation
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
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.AutocompleteActivity
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar

class AddParkingFragment : DialogFragment() {

    private val viewModel: ParkMateViewModel by activityViewModels {
        val db = AppDatabase.getDatabase(requireContext().applicationContext)
        ParkMateViewModelFactory(requireActivity().application, db.appDao())
    }

    private lateinit var mapView: MapView
    private var googleMap: GoogleMap? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var selectedLatitude: Double = 0.0
    private var selectedLongitude: Double = 0.0
    private var currentVehicles: List<Vehicle> = emptyList()
    private var savedPhotoPath: String? = null
    private var editingLocationId: Int? = null
    private var fixedEndTimeMillis: Long? = null

    private val autocompleteLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.let { data ->
                val place = Autocomplete.getPlaceFromIntent(data)
                place.latLng?.let { latLng ->
                    googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
                    googleMap?.clear()
                    googleMap?.addMarker(MarkerOptions().position(latLng).title(place.name))
                    selectedLatitude = latLng.latitude
                    selectedLongitude = latLng.longitude
                    view?.findViewById<TextView>(R.id.tvSearchMap)?.text = " ${place.name}"
                }
            }
        }
    }

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
        if (bitmap != null) {
            val file = File(requireContext().filesDir, "park_photo_${System.currentTimeMillis()}.jpg")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it) }
            savedPhotoPath = file.absolutePath

            view?.findViewById<ImageView>(R.id.ivPhotoPreview)?.apply {
                setImageBitmap(bitmap)
                visibility = View.VISIBLE
            }
            view?.findViewById<Button>(R.id.btnChangePhoto)?.text = "Cambia Foto ✓"
        }
    }

    private val locationPermissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false)) getCurrentLocation()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_add_parking, container, false)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<MaterialToolbar>(R.id.toolbarAddParking).setNavigationOnClickListener { dismiss() }

        if (!Places.isInitialized()) {
            try {
                val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    requireContext().packageManager.getApplicationInfo(requireContext().packageName, PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong()))
                } else {
                    @Suppress("DEPRECATION")
                    requireContext().packageManager.getApplicationInfo(requireContext().packageName, PackageManager.GET_META_DATA)
                }
                val apiKey = appInfo.metaData.getString("com.google.android.geo.API_KEY")
                if (apiKey != null && apiKey.startsWith("AIza")) {
                    Places.initialize(requireContext().applicationContext, apiKey)
                }
            } catch (e: Exception) { e.printStackTrace() }
        }

        val isVehicleLocked = arguments?.getBoolean("is_vehicle_locked", false) ?: false
        val isLocationLocked = arguments?.containsKey("location_id") == true && !(arguments?.getBoolean("is_edit_mode", false) ?: false)
        val isEditMode = arguments?.getBoolean("is_edit_mode", false) ?: false
        editingLocationId = arguments?.getInt("location_id", -1)?.takeIf { it != -1 }

        val toggleGroup = view.findViewById<MaterialButtonToggleGroup>(R.id.toggleGroupAction)
        val layoutVehicleSelection = view.findViewById<LinearLayout>(R.id.layoutVehicleSelection)
        val cbHeart = view.findViewById<CheckBox>(R.id.cbSaveAsFavorite)
        val actvParkingType = view.findViewById<AutoCompleteTextView>(R.id.actvParkingType)
        val layoutHourlyCosts = view.findViewById<LinearLayout>(R.id.layoutHourlyCosts)
        val layoutFixedCosts = view.findViewById<LinearLayout>(R.id.layoutFixedCosts)
        val btnFixedEndTime = view.findViewById<Button>(R.id.btnFixedEndTime)
        val scrollView = view.findViewById<ScrollView>(R.id.scrollViewAddParking)

        val switchLocalGeofence = view.findViewById<MaterialSwitch>(R.id.switchLocalGeofence)
        val prefs = requireContext().getSharedPreferences("ParkingMatePrefs", Context.MODE_PRIVATE)
        val isGlobalGeofenceOn = prefs.getBoolean("geofence_global_enabled", false)

        // --- FUNZIONE PER MOSTRARE LA FOTO CON BITMAP FACTORY ---
        fun displayPhotoSafely(path: String?, isLocked: Boolean) {
            val ivPreview = view.findViewById<ImageView>(R.id.ivPhotoPreview)
            val btnAddPhoto = view.findViewById<Button>(R.id.btnChangePhoto)

            if (!path.isNullOrBlank()) {
                try {
                    val file = File(path)
                    if (file.exists()) {
                        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                        ivPreview.setImageBitmap(bitmap)
                        ivPreview.visibility = View.VISIBLE
                        savedPhotoPath = path

                        if (isLocked) {
                            btnAddPhoto.text = "Foto Inclusa ✓"
                            btnAddPhoto.isEnabled = false
                        } else {
                            btnAddPhoto.text = "Cambia Foto ✓"
                        }
                    } else {
                        ivPreview.visibility = View.GONE
                        savedPhotoPath = null
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    ivPreview.visibility = View.GONE
                }
            } else {
                ivPreview.visibility = View.GONE
                savedPhotoPath = null
            }
        }

        fun setupGeofenceSwitchVisibility() {
            val isParkingModeNow = (toggleGroup.checkedButtonId == R.id.btnParkVehicle)
            val shouldShow = isEditMode || !isParkingModeNow || (isParkingModeNow && cbHeart.isChecked)

            switchLocalGeofence.visibility = if (shouldShow) View.VISIBLE else View.GONE

            if (!isGlobalGeofenceOn) {
                switchLocalGeofence.isEnabled = false
                switchLocalGeofence.isChecked = false
                switchLocalGeofence.text = "Geofence (Disattivato in Impostazioni)"
            } else {
                switchLocalGeofence.isEnabled = true
                switchLocalGeofence.text = "Attiva Geofence (Notifiche Area)"
            }
        }

        if (isEditMode) {
            switchLocalGeofence.isChecked = arguments?.getBoolean("is_geofence_enabled", false) ?: false
        } else {
            switchLocalGeofence.isChecked = true
        }
        setupGeofenceSwitchVisibility()

        cbHeart.setOnCheckedChangeListener { _, _ -> setupGeofenceSwitchVisibility() }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        mapView = view.findViewById(R.id.mapViewMini)
        mapView.onCreate(savedInstanceState)

        selectedLatitude = arguments?.getDouble("lat", 0.0) ?: 0.0
        selectedLongitude = arguments?.getDouble("lng", 0.0) ?: 0.0

        mapView.getMapAsync { map ->
            googleMap = map
            if (selectedLatitude != 0.0 && selectedLongitude != 0.0) {
                val latLng = LatLng(selectedLatitude, selectedLongitude)
                googleMap?.addMarker(MarkerOptions().position(latLng).title("Luogo"))
                googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
            }
            if (!isLocationLocked && !isEditMode) {
                googleMap?.setOnMapClickListener { latLng ->
                    googleMap?.clear()
                    googleMap?.addMarker(MarkerOptions().position(latLng).title("Selezionato"))
                    selectedLatitude = latLng.latitude
                    selectedLongitude = latLng.longitude
                }
            } else {
                googleMap?.uiSettings?.setAllGesturesEnabled(false)
            }
        }

        val mapOverlay = view.findViewById<View>(R.id.mapTransparentOverlay)
        mapOverlay?.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> scrollView.requestDisallowInterceptTouchEvent(true)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> scrollView.requestDisallowInterceptTouchEvent(false)
            }
            mapView.dispatchTouchEvent(event)
            true
        }

        view.findViewById<Button>(R.id.btnGetLocation).setOnClickListener {
            locationPermissionRequest.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
        view.findViewById<Button>(R.id.btnChangePhoto).setOnClickListener { takePictureLauncher.launch(null) }

        val tvSearchMap = view.findViewById<TextView>(R.id.tvSearchMap)
        tvSearchMap.setOnClickListener {
            if (Places.isInitialized()) {
                val fields = listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG)
                val intent = Autocomplete.IntentBuilder(AutocompleteActivityMode.OVERLAY, fields).build(requireContext())
                autocompleteLauncher.launch(intent)
            }
        }

        var isMapExpanded = false
        val cardMap = view.findViewById<View>(R.id.cardMap)
        val btnExpandMap = view.findViewById<android.widget.ImageButton>(R.id.btnExpandMap)

        if (isLocationLocked || isEditMode) btnExpandMap.visibility = View.GONE

        btnExpandMap.setOnClickListener {
            isMapExpanded = !isMapExpanded
            val params = cardMap.layoutParams
            params.height = if (isMapExpanded) (500 * resources.displayMetrics.density).toInt() else (200 * resources.displayMetrics.density).toInt()
            cardMap.layoutParams = params
            tvSearchMap.visibility = if (isMapExpanded) View.VISIBLE else View.GONE
            btnExpandMap.setImageResource(if (isMapExpanded) android.R.drawable.ic_menu_close_clear_cancel else android.R.drawable.ic_menu_zoom)
        }

        fun updateParkingOptions(isParkingMode: Boolean) {
            val types = arrayOf("Gratis", "All'ora", "Costo Fisso")
            actvParkingType.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, types))

            var currentText = actvParkingType.text.toString()
            if (currentText == "Già Pagato") {
                currentText = "Costo Fisso"
                actvParkingType.setText(currentText, false)
            }

            if (!types.contains(currentText)) {
                actvParkingType.setText(types[0], false)
                currentText = types[0]
            }

            layoutHourlyCosts.visibility = if (currentText == "All'ora") View.VISIBLE else View.GONE
            layoutFixedCosts.visibility = if (currentText == "Costo Fisso") View.VISIBLE else View.GONE
            btnFixedEndTime.visibility = if (currentText == "Costo Fisso" && isParkingMode) View.VISIBLE else View.GONE
        }

        actvParkingType.setOnItemClickListener { _, _, _, _ ->
            val isParking = (toggleGroup.checkedButtonId == R.id.btnParkVehicle) || isVehicleLocked || isLocationLocked
            updateParkingOptions(isParking)
        }

        btnFixedEndTime.setOnClickListener {
            pickDateTime(System.currentTimeMillis()) { millis ->
                fixedEndTimeMillis = millis
                val format = java.text.SimpleDateFormat("dd MMM, HH:mm", java.util.Locale.getDefault())
                btnFixedEndTime.text = format.format(java.util.Date(millis))
            }
        }

        toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked && !isVehicleLocked && !isLocationLocked) {
                val isParkingModeNow = (checkedId == R.id.btnParkVehicle)
                layoutVehicleSelection.visibility = if (isParkingModeNow) View.VISIBLE else View.GONE
                cbHeart.visibility = if (isParkingModeNow) View.VISIBLE else View.GONE
                updateParkingOptions(isParkingModeNow)
                setupGeofenceSwitchVisibility()
            }
        }

        if (!isVehicleLocked && !isLocationLocked) {
            val isParkingModeNow = (toggleGroup.checkedButtonId == R.id.btnParkVehicle)
            layoutVehicleSelection.visibility = if (isParkingModeNow) View.VISIBLE else View.GONE
            cbHeart.visibility = if (isParkingModeNow) View.VISIBLE else View.GONE
            updateParkingOptions(isParkingModeNow)
        }

        if (isVehicleLocked || isLocationLocked) {
            toggleGroup.visibility = View.GONE
            cbHeart.visibility = View.GONE
            updateParkingOptions(true)
        }

        if (isVehicleLocked) {
            layoutVehicleSelection.visibility = View.GONE
            view.findViewById<TextView>(R.id.tvDisplayVehicle).apply { visibility = View.VISIBLE; text = "Veicolo: " + arguments?.getString("preselected_vehicle_name") }
            view.findViewById<View>(R.id.tilLocationName).visibility = View.GONE
            view.findViewById<View>(R.id.tilSelectExistingLocation).visibility = View.VISIBLE
            cardMap.visibility = View.GONE

            val tilParkingType = view.findViewById<TextInputLayout>(R.id.tilParkingType)
            val tilNotes = view.findViewById<TextInputLayout>(R.id.tilNotes)
            val etNotes = view.findViewById<TextInputEditText>(R.id.etNotes)
            val btnSaveEverything = view.findViewById<View>(R.id.btnSaveEverything)

            tilParkingType.visibility = View.GONE
            tilNotes.visibility = View.GONE
            view.findViewById<Button>(R.id.btnChangePhoto).visibility = View.GONE
            btnSaveEverything.visibility = View.GONE

            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.savedLocationsList.collect { locations ->
                        view.findViewById<AutoCompleteTextView>(R.id.actvSelectLocation).apply {
                            setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, locations.map { it.name }))
                            setOnItemClickListener { _, _, pos, _ ->
                                val loc = locations[pos]
                                selectedLatitude = loc.latitude
                                selectedLongitude = loc.longitude
                                actvParkingType.setText(loc.defaultType, false)
                                updateParkingOptions(true)

                                tilParkingType.isEnabled = false
                                etNotes.setText(loc.notes ?: "")
                                tilNotes.isEnabled = false

                                // --- MOSTRA FOTO DALLA TENDINA (BLOCCATA) ---
                                view.findViewById<Button>(R.id.btnChangePhoto).visibility = View.VISIBLE
                                displayPhotoSafely(loc.photoPath, isLocked = true)

                                val etHourly = view.findViewById<TextInputEditText>(R.id.etCostHourly)
                                val etInitial = view.findViewById<TextInputEditText>(R.id.etCostInitial)
                                val etMax = view.findViewById<TextInputEditText>(R.id.etCostMax)
                                val etFixed = view.findViewById<TextInputEditText>(R.id.etFixedCost)

                                etHourly.text?.clear()
                                etInitial.text?.clear()
                                etMax.text?.clear()
                                etFixed.text?.clear()
                                etHourly.isEnabled = true
                                etInitial.isEnabled = true
                                etMax.isEnabled = true
                                etFixed.isEnabled = true

                                if (loc.defaultType == "All'ora") {
                                    if (loc.defaultCost > 0) etHourly.setText(loc.defaultCost.toString())
                                    if (loc.initialCost > 0) etInitial.setText(loc.initialCost.toString())
                                    if (loc.maxCost > 0) etMax.setText(loc.maxCost.toString())
                                    etHourly.isEnabled = false
                                    etInitial.isEnabled = false
                                    etMax.isEnabled = false
                                } else if (loc.defaultType == "Costo Fisso" || loc.defaultType == "Già Pagato") {
                                    if (loc.defaultCost > 0) etFixed.setText(loc.defaultCost.toString())
                                    etFixed.isEnabled = false
                                }

                                tilParkingType.visibility = View.VISIBLE
                                tilNotes.visibility = View.VISIBLE
                                btnSaveEverything.visibility = View.VISIBLE
                            }
                        }
                    }
                }
            }
        }

        if (isLocationLocked) {
            view.findViewById<View>(R.id.tilLocationName).visibility = View.GONE
            view.findViewById<TextView>(R.id.tvDisplayLocationName).apply { visibility = View.VISIBLE; text = "Luogo: " + arguments?.getString("name") }
            view.findViewById<Button>(R.id.btnGetLocation).visibility = View.GONE
            layoutVehicleSelection.visibility = View.VISIBLE

            val tType = arguments?.getString("type") ?: "Gratis"
            view.findViewById<View>(R.id.tilParkingType).visibility = View.GONE
            view.findViewById<TextView>(R.id.tvDisplayParkingType).apply { visibility = View.VISIBLE; text = "Tariffa: $tType"; setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.parking_blue_light)) }
            actvParkingType.setText(tType, false)
            updateParkingOptions(true)

            val tCost = arguments?.getDouble("cost", 0.0) ?: 0.0
            val tInitial = arguments?.getDouble("initialCost", 0.0) ?: 0.0
            val tMax = arguments?.getDouble("maxCost", 0.0) ?: 0.0

            if (tType == "All'ora") {
                val etHourly = view.findViewById<TextInputEditText>(R.id.etCostHourly)
                val etInitial = view.findViewById<TextInputEditText>(R.id.etCostInitial)
                val etMax = view.findViewById<TextInputEditText>(R.id.etCostMax)

                if (tCost > 0) etHourly.setText(tCost.toString())
                if (tInitial > 0) etInitial.setText(tInitial.toString())
                if (tMax > 0) etMax.setText(tMax.toString())

                etHourly.isEnabled = false
                etInitial.isEnabled = false
                etMax.isEnabled = false
            } else if (tType == "Costo Fisso" || tType == "Già Pagato") {
                val etFixed = view.findViewById<TextInputEditText>(R.id.etFixedCost)
                if (tCost > 0) etFixed.setText(tCost.toString())
                etFixed.isEnabled = false
            }

            val tNotes = arguments?.getString("notes")
            if (!tNotes.isNullOrBlank()) {
                val etNotes = view.findViewById<TextInputEditText>(R.id.etNotes)
                etNotes.setText(tNotes)
                view.findViewById<TextInputLayout>(R.id.tilNotes).isEnabled = false
            }

            // --- MOSTRA FOTO DALLA CARD LUOGHI (BLOCCATA) ---
            val tPhoto = arguments?.getString("photoPath")
            displayPhotoSafely(tPhoto, isLocked = true)
        }

        if (isEditMode) {
            view.findViewById<Button>(R.id.btnDeleteLocation).apply {
                visibility = View.VISIBLE
                setOnClickListener {
                    editingLocationId?.let { id ->
                        viewModel.removeSavedLocation(com.example.parkingmate.data.SavedLocation(id = id, name = "", latitude = 0.0, longitude = 0.0))
                        GeofenceHelper.removeGeofence(requireContext(), id)
                        Toast.makeText(requireContext(), "Luogo Eliminato", Toast.LENGTH_SHORT).show()
                        dismiss()
                    }
                }
            }
            view.findViewById<TextInputEditText>(R.id.etLocationName).setText(arguments?.getString("name", ""))
            view.findViewById<TextInputEditText>(R.id.etNotes).setText(arguments?.getString("notes", ""))

            // --- MOSTRA FOTO IN MODIFICA (SBLOCCATA) ---
            val tPhoto = arguments?.getString("photoPath")
            displayPhotoSafely(tPhoto, isLocked = false)

            actvParkingType.setText(arguments?.getString("type", ""), false)
            updateParkingOptions(false)
            toggleGroup.check(R.id.btnSaveFavoriteOnly)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.vehiclesList.collect { vehicles ->
                    currentVehicles = vehicles
                    view.findViewById<AutoCompleteTextView>(R.id.actvSelectVehicle).setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, vehicles.map { "${it.name} (${it.type})" }))
                }
            }
        }

        view.findViewById<Button>(R.id.btnSaveEverything).setOnClickListener {
            val isParkMode = isVehicleLocked || isLocationLocked || toggleGroup.checkedButtonId == R.id.btnParkVehicle
            val name = if (isVehicleLocked) view.findViewById<AutoCompleteTextView>(R.id.actvSelectLocation).text.toString() else if (isLocationLocked) arguments?.getString("name") ?: "" else view.findViewById<TextInputEditText>(R.id.etLocationName).text.toString()
            val type = actvParkingType.text.toString()
            val notes = view.findViewById<TextInputEditText>(R.id.etNotes).text.toString()

            if (name.isEmpty() || type.isEmpty()) { Toast.makeText(requireContext(), "Compila Nome e Tipo!", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            if (selectedLatitude == 0.0 && !isVehicleLocked) { Toast.makeText(requireContext(), "Seleziona posizione!", Toast.LENGTH_SHORT).show(); return@setOnClickListener }

            val isHourly = (type == "All'ora")
            val costStrHourly = view.findViewById<TextInputEditText>(R.id.etCostHourly).text.toString().replace(",", ".")
            val costStrInitial = view.findViewById<TextInputEditText>(R.id.etCostInitial).text.toString().replace(",", ".")
            val costStrMax = view.findViewById<TextInputEditText>(R.id.etCostMax).text.toString().replace(",", ".")
            val costStrFixed = view.findViewById<TextInputEditText>(R.id.etFixedCost).text.toString().replace(",", ".")

            var finalCost = 0.0
            var finalInitial = 0.0
            var finalMax = 0.0

            if (isHourly) {
                finalCost = costStrHourly.toDoubleOrNull() ?: 0.0
                finalInitial = costStrInitial.toDoubleOrNull() ?: 0.0
                finalMax = costStrMax.toDoubleOrNull() ?: 0.0
            } else if (type == "Costo Fisso" || type == "Già Pagato") {
                finalCost = costStrFixed.toDoubleOrNull() ?: 0.0
            }

            val isGeoChecked = switchLocalGeofence.isChecked
            val appContext = requireContext().applicationContext

            if (isParkMode) {
                val selectedVehicleName = if (isVehicleLocked) arguments?.getString("preselected_vehicle_name") else view.findViewById<AutoCompleteTextView>(R.id.actvSelectVehicle).text.toString()
                val vehicle = currentVehicles.find { "${it.name} (${it.type})" == selectedVehicleName }
                if (vehicle == null) { Toast.makeText(requireContext(), "Seleziona un veicolo!", Toast.LENGTH_SHORT).show(); return@setOnClickListener }

                val saveAndTrackLogic = { shouldTrack: Boolean, feedbackMsg: String ->
                    viewModel.addParkingSession(vehicle.id, vehicle.name, name, type, selectedLatitude, selectedLongitude, notes, savedPhotoPath, finalCost, finalInitial, finalMax, fixedEndTimeMillis) { newId ->

                        if (shouldTrack) {
                            startWalkingService(newId)
                            Toast.makeText(appContext, "Parcheggio Avviato! Tracciamento camminata in corso...", Toast.LENGTH_LONG).show()
                        } else {
                            if (feedbackMsg.isNotEmpty()) Toast.makeText(appContext, feedbackMsg, Toast.LENGTH_LONG).show()
                        }

                        if (cbHeart.isChecked && cbHeart.visibility == View.VISIBLE) {
                            viewModel.addSavedLocation(name, selectedLatitude, selectedLongitude, type, notes, savedPhotoPath, finalCost, finalInitial, finalMax, isGeoChecked) { locId ->
                                if (isGeoChecked) {
                                    val loc = SavedLocation(locId, name, selectedLatitude, selectedLongitude, type, notes, savedPhotoPath, isGeoChecked, finalCost, finalInitial, finalMax)
                                    GeofenceHelper.addGeofence(appContext, loc)
                                }
                            }
                        }
                        dismiss()
                    }
                }

                val isEffortGloballyEnabled = prefs.getBoolean("effort_global_enabled", false)
                if (!isEffortGloballyEnabled) {
                    saveAndTrackLogic(false, "Parcheggio salvato! (Tracciamento sforzo disabilitato in Impostazioni)")
                } else if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    fusedLocationClient.lastLocation.addOnCompleteListener { task ->
                        val currentLocation = task.result
                        if (currentLocation != null) {
                            val distanceResults = FloatArray(1)
                            Location.distanceBetween(currentLocation.latitude, currentLocation.longitude, selectedLatitude, selectedLongitude, distanceResults)
                            if (distanceResults[0] <= 250f) saveAndTrackLogic(true, "") else saveAndTrackLogic(false, "Parcheggio salvato! (Sei troppo lontano dall'auto per il tracciamento)")
                        } else {
                            saveAndTrackLogic(false, "Parcheggio salvato! (GPS debole, sforzo non tracciato)")
                        }
                    }
                } else {
                    saveAndTrackLogic(false, "Parcheggio salvato! (Permessi GPS mancanti)")
                }

            } else {
                if (isEditMode && editingLocationId != null) {
                    viewModel.updateSavedLocation(editingLocationId!!, name, selectedLatitude, selectedLongitude, type, notes, savedPhotoPath, isGeoChecked, finalCost, finalInitial, finalMax)
                    val loc = SavedLocation(editingLocationId!!, name, selectedLatitude, selectedLongitude, type, notes, savedPhotoPath, isGeoChecked, finalCost, finalInitial, finalMax)
                    if (isGeoChecked) GeofenceHelper.addGeofence(appContext, loc) else GeofenceHelper.removeGeofence(appContext, loc.id)
                } else {
                    viewModel.addSavedLocation(name, selectedLatitude, selectedLongitude, type, notes, savedPhotoPath, finalCost, finalInitial, finalMax, isGeoChecked) { locId ->
                        if (isGeoChecked) {
                            val loc = SavedLocation(locId, name, selectedLatitude, selectedLongitude, type, notes, savedPhotoPath, isGeoChecked, finalCost, finalInitial, finalMax)
                            GeofenceHelper.addGeofence(appContext, loc)
                        }
                    }
                }
                Toast.makeText(appContext, "Luogo Salvato!", Toast.LENGTH_SHORT).show()
                dismiss()
            }
        }
    }

    private fun pickDateTime(currentMillis: Long, onResult: (Long) -> Unit) {
        val cal = Calendar.getInstance().apply { timeInMillis = currentMillis }
        DatePickerDialog(requireContext(), { _, year, month, day ->
            TimePickerDialog(requireContext(), { _, hour, minute ->
                val newCal = Calendar.getInstance()
                newCal.set(year, month, day, hour, minute)
                onResult(newCal.timeInMillis)
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    @SuppressLint("MissingPermission")
    private fun getCurrentLocation() {
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                val latLng = LatLng(location.latitude, location.longitude)
                googleMap?.clear()
                googleMap?.addMarker(MarkerOptions().position(latLng))
                googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                selectedLatitude = location.latitude
                selectedLongitude = location.longitude
            }
        }
    }

    private fun startWalkingService(sessionId: Int) {
        val ctx = context ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACTIVITY_RECOGNITION, Manifest.permission.ACCESS_FINE_LOCATION), 101)
        }

        val intent = Intent(ctx, WalkingTrackerService::class.java).apply {
            action = "START"
            putExtra("SESSION_ID", sessionId)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(intent) else ctx.startService(intent)
    }

    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { super.onPause(); mapView.onPause() }
    override fun onStop() { super.onStop(); mapView.onStop() }
    override fun onDestroyView() { super.onDestroyView(); mapView.onDestroy() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
}