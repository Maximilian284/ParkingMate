package com.example.parkingmate

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.ImageButton
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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class HomeFragment : Fragment(R.layout.fragment_home) {

    private val viewModel: ParkMateViewModel by activityViewModels {
        val db = AppDatabase.getDatabase(requireContext().applicationContext)
        ParkMateViewModelFactory(db.appDao())
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

        // 1. TOOLBAR E I DUE PULSANTI
        toolbar.menu.clear()
        toolbar.inflateMenu(R.menu.menu_home)
        toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_toggle_view -> {
                    isMapMode = !isMapMode
                    menuItem.setIcon(if (isMapMode) android.R.drawable.ic_menu_sort_by_size else android.R.drawable.ic_dialog_map)
                    updateUI()
                    true
                }
                R.id.action_toggle_history -> {
                    isHistoryMode = !isHistoryMode
                    menuItem.setIcon(if (isHistoryMode) android.R.drawable.ic_menu_info_details else android.R.drawable.ic_menu_recent_history)
                    toolbar.title = if (isHistoryMode) "Storico Parcheggi" else "Parcheggi Attivi"
                    updateUI()
                    true
                }
                else -> false
            }
        }

        // 2. ADAPTER E LISTA
        adapter = ParkingAdapter(
            parkings = emptyList(),
            onCardClick = { session -> showEditTimeDialog(session) },
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
            }
        )
        rvHomeParkings.layoutManager = LinearLayoutManager(requireContext())
        rvHomeParkings.adapter = adapter

        // 3. MAPPA
        mapView = view.findViewById(R.id.mapViewHome)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { map ->
            googleMap = map
            updateMapMarkers(moveCamera = true)
        }

        setupSliderControls()

        // 4. RACCOLTA DATI
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
                // STOP
                isPlaying = false
                playJob?.cancel()
                btnPlay.setImageResource(android.R.drawable.ic_media_play)
            } else {
                // START
                isPlaying = true
                btnPlay.setImageResource(android.R.drawable.ic_media_pause)

                playJob = viewLifecycleOwner.lifecycleScope.launch {
                    // Partiamo da dove si trova ora lo slider o da 0
                    var currentVal = sliderTime.value
                    if (currentVal >= 1440f) currentVal = 0f

                    while (isPlaying && currentVal < 1440f) {
                        currentVal += 30f // Avanza di mezz'ora alla volta
                        sliderTime.value = currentVal

                        // Aggiorniamo orario e pin
                        val hours = (currentVal / 60).toInt()
                        val minutes = (currentVal % 60).toInt()
                        currentCalendar.set(Calendar.HOUR_OF_DAY, hours)
                        currentCalendar.set(Calendar.MINUTE, minutes)
                        updateSliderText()
                        updateMapMarkers(moveCamera = false)

                        kotlinx.coroutines.delay(800) // Aspetta quasi un secondo tra uno scatto e l'altro
                    }

                    // Fine giornata o stop manuale
                    isPlaying = false
                    btnPlay.setImageResource(android.R.drawable.ic_media_play)
                }
            }
        }
    }

    private fun showEditTimeDialog(session: ParkingSession) {
        val options = if (session.isActive) arrayOf("Modifica Data/Ora Inizio") else arrayOf("Modifica Data/Ora Inizio", "Modifica Data/Ora Fine")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Modifica Parcheggio")
            .setItems(options) { _, which ->
                val timeToEdit = if (which == 0) session.startTime else session.endTime ?: System.currentTimeMillis()
                pickDateTime(timeToEdit) { newTimeMillis ->
                    val updatedSession = if (which == 0) session.copy(startTime = newTimeMillis) else session.copy(endTime = newTimeMillis)
                    viewModel.updateParkingSession(updatedSession)
                    Toast.makeText(requireContext(), "Orario aggiornato!", Toast.LENGTH_SHORT).show()
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
        btnSelectMonth.setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.ic_menu_today, 0, 0, 0)

        sliderTime.valueFrom = 0f
        sliderTime.valueTo = 1440f
        sliderTime.stepSize = 30f

        sliderTime.setLabelFormatter { value ->
            var hours = (value / 60).toInt()
            var minutes = (value % 60).toInt()
            if (hours == 24) { hours = 23; minutes = 59 }
            String.format(Locale.getDefault(), "%02d:%02d", hours, minutes)
        }

        val currentMinutes = (currentCalendar.get(Calendar.HOUR_OF_DAY) * 60) + currentCalendar.get(Calendar.MINUTE)
        val roundedMinutes = (currentMinutes / 30) * 30
        sliderTime.value = roundedMinutes.toFloat()
        updateSliderText()

        // Cambia l'ora scritta, MA NON AGGIORNA LA MAPPA
        sliderTime.addOnChangeListener { _, value, _ ->
            var hours = (value / 60).toInt()
            var minutes = (value % 60).toInt()
            if (hours == 24) { hours = 23; minutes = 59 }

            currentCalendar.set(Calendar.HOUR_OF_DAY, hours)
            currentCalendar.set(Calendar.MINUTE, minutes)
            currentCalendar.set(Calendar.SECOND, 0)
            currentCalendar.set(Calendar.MILLISECOND, 0)
            updateSliderText()
        }

        // AGGIORNA LA MAPPA SOLO QUANDO ALZI IL DITO! (Fine sfarfallio e sovrapposizioni)
        sliderTime.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) {
                updateMapMarkers(moveCamera = false)
            }
        })

        btnSelectMonth.setOnClickListener {
            DatePickerDialog(requireContext(), { _, year, month, day ->
                currentCalendar.set(Calendar.YEAR, year)
                currentCalendar.set(Calendar.MONTH, month)
                currentCalendar.set(Calendar.DAY_OF_MONTH, day)
                updateSliderText()
                updateMapMarkers(moveCamera = true)
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
            updateMapMarkers(moveCamera = true)
        } else {
            layoutMapContainer.visibility = View.GONE
            rvHomeParkings.visibility = View.VISIBLE
            adapter.updateData(if (isHistoryMode) historyList else activeList)
        }
    }

    private fun updateMapMarkers(moveCamera: Boolean) {
        val map = googleMap ?: return
        map.clear() // Pulizia sicura
        var lastKnownPosition: LatLng? = null

        if (!isHistoryMode) {
            for (item in activeList) {
                val pos = LatLng(item.session.latitude, item.session.longitude)
                map.addMarker(MarkerOptions().position(pos).title(item.vehicle.name).snippet("In sosta").icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)))
                lastKnownPosition = pos
            }
        } else {
            // Pulizia esatta della data dello slider
            currentCalendar.set(Calendar.SECOND, 0)
            currentCalendar.set(Calendar.MILLISECOND, 0)
            val selectedTimeMillis = currentCalendar.timeInMillis

            for (item in historyList) {
                val sessionStart = item.session.startTime
                // FIX FANTASMA: Se la fine non c'è, usa l'inizio (così non si spalma per 2 mesi interi)
                val sessionEnd = item.session.endTime ?: item.session.startTime

                if (selectedTimeMillis in sessionStart..sessionEnd) {
                    val pos = LatLng(item.session.latitude, item.session.longitude)
                    map.addMarker(MarkerOptions().position(pos).title(item.vehicle.name).snippet("Parcheggiato").icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)))
                    lastKnownPosition = pos
                }
            }
        }

        if (moveCamera) {
            if (lastKnownPosition != null) {
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(lastKnownPosition, 14f))
            } else {
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(44.4949, 11.3426), 12f))
            }
        }
    }

    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { super.onPause(); mapView.onPause() }
    override fun onStop() { super.onStop(); mapView.onStop() }
    override fun onDestroyView() { super.onDestroyView(); mapView.onDestroy(); googleMap = null }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
}