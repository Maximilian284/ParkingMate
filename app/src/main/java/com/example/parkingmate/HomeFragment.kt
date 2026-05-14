package com.example.parkingmate

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.parkingmate.adapter.ParkingAdapter
import com.example.parkingmate.data.AppDatabase
import com.example.parkingmate.data.ParkingSession
import com.example.parkingmate.data.SessionWithVehicle
import com.example.parkingmate.viewmodel.ParkMateViewModel
import com.example.parkingmate.viewmodel.ParkMateViewModelFactory
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class HomeFragment : Fragment(R.layout.fragment_home) {

    private val viewModel: ParkMateViewModel by activityViewModels {
        val db = AppDatabase.getDatabase(requireContext().applicationContext)
        ParkMateViewModelFactory(requireActivity().application, db.appDao())
    }

    private var isMapMode = false
    private var isHistoryMode = false
    private var activeList: List<SessionWithVehicle> = emptyList()
    private var historyList: List<SessionWithVehicle> = emptyList()

    private lateinit var adapter: ParkingAdapter
    private lateinit var rvHomeParkings: RecyclerView
    private lateinit var layoutMapContainer: RelativeLayout
    private lateinit var layoutHistoryControls: LinearLayout
    private lateinit var mapView: MapView
    private var googleMap: GoogleMap? = null
    private lateinit var toolbar: MaterialToolbar

    private lateinit var sliderTime: Slider
    private lateinit var tvSliderDate: TextView
    private lateinit var btnSelectMonth: Button
    private val currentCalendar = Calendar.getInstance()

    private var isPlaying = false
    private var playJob: kotlinx.coroutines.Job? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rvHomeParkings = view.findViewById(R.id.rvHomeParkings)
        layoutMapContainer = view.findViewById(R.id.layoutMapContainer)
        layoutHistoryControls = view.findViewById(R.id.layoutHistoryControls)
        sliderTime = view.findViewById(R.id.sliderTime)
        tvSliderDate = view.findViewById(R.id.tvSliderDate)
        btnSelectMonth = view.findViewById(R.id.btnSelectMonth)
        toolbar = view.findViewById(R.id.toolbarHome)

        toolbar.menu.clear()
        toolbar.inflateMenu(R.menu.menu_home)
        toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_toggle_view -> {
                    isMapMode = !isMapMode
                    menuItem.setIcon(if (isMapMode) android.R.drawable.ic_dialog_map else android.R.drawable.ic_menu_sort_by_size)
                    updateUI()
                    true
                }
                R.id.action_toggle_history -> {
                    isHistoryMode = !isHistoryMode
                    menuItem.setIcon(if (isHistoryMode) android.R.drawable.ic_menu_recent_history else android.R.drawable.ic_menu_info_details)
                    toolbar.title = if (isHistoryMode) "Storico Parcheggi" else "Parcheggi Attivi"
                    updateUI()
                    true
                }
                else -> false
            }
        }

        // --- CREAZIONE ADAPTER (CON TUTTI E 4 I PARAMETRI) ---
        adapter = ParkingAdapter(
            parkings = emptyList(),
            onCardClick = { item ->
                showParkingDetailsDialog(item)
            },
            onEndClick = { sessionToEnd ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Termina Parcheggio")
                    .setMessage("Vuoi terminare questa sosta?")
                    .setPositiveButton("Termina") { _, _ ->
                        viewModel.finishParking(sessionToEnd)
                        Toast.makeText(requireContext(), "Sosta Terminata!", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Annulla", null)
                    .show()
            },
            onDeleteClick = { sessionToDelete ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Elimina Parcheggio")
                    .setMessage("Vuoi eliminare definitivamente questo record?")
                    .setPositiveButton("Elimina") { _, _ ->
                        viewModel.deleteParkingSession(sessionToDelete)
                        Toast.makeText(requireContext(), "Eliminato!", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Annulla", null)
                    .show()
            }
        )
        rvHomeParkings.layoutManager = LinearLayoutManager(requireContext())
        rvHomeParkings.adapter = adapter

        mapView = view.findViewById(R.id.mapViewHome)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { map ->
            googleMap = map
            updateMapMarkers(moveCamera = true)
        }

        setupSliderControls()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.activeParkings.collect { list ->
                        activeList = list
                        if (!isHistoryMode) updateUI()
                    }
                }
                launch {
                    viewModel.historyParkings.collect { list ->
                        historyList = list
                        if (isHistoryMode) updateUI()
                    }
                }
            }
        }

        val btnPlay = view.findViewById<ImageButton>(R.id.btnPlayHistory)
        btnPlay.setOnClickListener {
            if (isPlaying) {
                isPlaying = false
                playJob?.cancel()
                btnPlay.setImageResource(android.R.drawable.ic_media_play)
            } else {
                isPlaying = true
                btnPlay.setImageResource(android.R.drawable.ic_media_pause)
                playJob = viewLifecycleOwner.lifecycleScope.launch {
                    var currentVal = sliderTime.value
                    if (currentVal >= 1440f) currentVal = 0f
                    while (isPlaying && currentVal < 1440f) {
                        currentVal += 30f
                        sliderTime.value = currentVal
                        val hours = (currentVal / 60).toInt()
                        val minutes = (currentVal % 60).toInt()
                        currentCalendar.set(Calendar.HOUR_OF_DAY, hours)
                        currentCalendar.set(Calendar.MINUTE, minutes)
                        updateSliderText()
                        updateMapMarkers(moveCamera = false)
                        kotlinx.coroutines.delay(350)
                    }
                    isPlaying = false
                    btnPlay.setImageResource(android.R.drawable.ic_media_play)
                }
            }
        }
    }

    private fun showParkingDetailsDialog(item: SessionWithVehicle) {
        val session = item.session
        val vehicle = item.vehicle
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_parking_details, null)

        val tvTitle = dialogView.findViewById<TextView>(R.id.tvDetailTitle)
        val tvLocation = dialogView.findViewById<TextView>(R.id.tvDetailLocation)
        val tvVehicle = dialogView.findViewById<TextView>(R.id.tvDetailVehicle)
        val tvStartDate = dialogView.findViewById<TextView>(R.id.tvDetailStartDate)
        val tvTariff = dialogView.findViewById<TextView>(R.id.tvDetailTariff)
        val tvNotes = dialogView.findViewById<TextView>(R.id.tvDetailNotes)
        val ivPhoto = dialogView.findViewById<ImageView>(R.id.ivDetailPhoto)

        val customTitle = "${session.locationName?.takeIf { it.isNotBlank() } ?: session.note?.takeIf {it.isNotBlank()} ?: "Informazioni Parcheggio"}"
        tvTitle.text = customTitle
        tvVehicle.text = "Veicolo: ${vehicle.name} (${vehicle.type})"
        val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        tvStartDate.text = "Inizio: ${dateFormat.format(session.startTime)}"

        val tariffTranslated = when(session.type) {
            "Free" -> "Sosta Libera"
            "Hourly" -> "A Ore"
            "Fixed" -> "Ticket Fisso"
            else -> session.type
        }
        tvTariff.text = "Tariffa: $tariffTranslated"

        if (!session.note.isNullOrBlank()) {
            tvNotes.visibility = View.VISIBLE
            tvNotes.text = "Note: ${session.note}"
        }
        if (!session.photoPath.isNullOrBlank()) {
            ivPhoto.visibility = View.VISIBLE
            ivPhoto.setImageURI(Uri.parse(session.photoPath))
        }

        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Chiudi", null)
            .setNeutralButton("Modifica Ora") { _, _ -> showEditTimeDialog(session) }
            .show()

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(requireContext(), Locale.getDefault())
                val addresses = geocoder.getFromLocation(session.latitude, session.longitude, 1)
                val locationName = if (!addresses.isNullOrEmpty()) addresses[0].getAddressLine(0) else "Lat: ${session.latitude}, Lon: ${session.longitude}"
                withContext(Dispatchers.Main) { tvLocation.text = locationName }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { tvLocation.text = "Posizione non disponibile" }
            }
        }
    }

    private fun showEditTimeDialog(session: ParkingSession) {
        val options = if (session.isActive) arrayOf("Modifica Inizio") else arrayOf("Modifica Inizio", "Modifica Fine")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Modifica Orario")
            .setItems(options) { _, which ->
                val timeToEdit = if (which == 0) session.startTime else session.endTime ?: System.currentTimeMillis()
                pickDateTime(timeToEdit) { newTimeMillis ->
                    val updated = if (which == 0) session.copy(startTime = newTimeMillis) else session.copy(endTime = newTimeMillis)
                    viewModel.updateParkingSession(updated)
                }
            }
            .show()
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

    private fun setupSliderControls() {
        btnSelectMonth.text = "Scegli Data"
        sliderTime.valueFrom = 0f
        sliderTime.valueTo = 1440f
        sliderTime.stepSize = 30f
        sliderTime.setLabelFormatter { value ->
            var h = (value / 60).toInt(); var m = (value % 60).toInt()
            if (h == 24) { h = 23; m = 59 }
            String.format(Locale.getDefault(), "%02d:%02d", h, m)
        }
        val currentMinutes = (currentCalendar.get(Calendar.HOUR_OF_DAY) * 60) + currentCalendar.get(Calendar.MINUTE)
        sliderTime.value = ((currentMinutes / 30) * 30).toFloat()
        updateSliderText()
        sliderTime.addOnChangeListener { _, value, _ ->
            var h = (value / 60).toInt(); var m = (value % 60).toInt()
            currentCalendar.set(Calendar.HOUR_OF_DAY, h); currentCalendar.set(Calendar.MINUTE, m)
            updateSliderText()
        }
        sliderTime.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) { updateMapMarkers(false) }
        })
        btnSelectMonth.setOnClickListener {
            DatePickerDialog(requireContext(), { _, y, m, d ->
                currentCalendar.set(y, m, d); updateSliderText(); updateMapMarkers(true)
            }, currentCalendar.get(Calendar.YEAR), currentCalendar.get(Calendar.MONTH), currentCalendar.get(Calendar.DAY_OF_MONTH)).show()
        }
    }

    private fun updateSliderText() {
        val format = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        tvSliderDate.text = format.format(currentCalendar.time)
    }

    private fun updateUI() {
        if (isMapMode) {
            rvHomeParkings.visibility = View.GONE
            layoutMapContainer.visibility = View.VISIBLE
            layoutHistoryControls.visibility = if (isHistoryMode) View.VISIBLE else View.GONE
            updateMapMarkers(true)
        } else {
            layoutMapContainer.visibility = View.GONE
            rvHomeParkings.visibility = View.VISIBLE
            adapter.updateData(if (isHistoryMode) historyList else activeList)
        }
    }

    private fun updateMapMarkers(moveCamera: Boolean) {
        val map = googleMap ?: return
        map.clear()
        var lastPos: LatLng? = null
        if (!isHistoryMode) {
            for (item in activeList) {
                val pos = LatLng(item.session.latitude, item.session.longitude)
                map.addMarker(MarkerOptions().position(pos).title(item.vehicle.name).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)))
                lastPos = pos
            }
        } else {
            val selected = currentCalendar.timeInMillis
            for (item in historyList) {
                val start = item.session.startTime; val end = item.session.endTime ?: start
                if (selected in start..end) {
                    val pos = LatLng(item.session.latitude, item.session.longitude)
                    map.addMarker(MarkerOptions().position(pos).title(item.vehicle.name).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)))
                    lastPos = pos
                }
            }
        }
        if (moveCamera) {
            val target = lastPos ?: LatLng(44.4949, 11.3426)
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(target, if (lastPos != null) 14f else 12f))
        }
    }

    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { super.onPause(); mapView.onPause() }
    override fun onStop() { super.onStop(); mapView.onStop() }
    override fun onDestroyView() { super.onDestroyView(); mapView.onDestroy(); googleMap = null }
}