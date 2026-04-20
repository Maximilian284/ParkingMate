package com.example.parkingmate

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.parkingmate.data.AppDatabase
import com.example.parkingmate.viewmodel.ParkMateViewModel
import com.example.parkingmate.viewmodel.ParkMateViewModelFactory
import kotlinx.coroutines.launch

class VehiclesFragment : Fragment() {

    // 1. Inizializziamo il nostro ViewModel "Condiviso"
    // Usiamo 'activityViewModels' così i dati sono uguali per tutti i Fragment dell'app
    private val viewModel: ParkMateViewModel by activityViewModels {
        // Creiamo la connessione al Database per passarla alla Factory
        val db = AppDatabase.getDatabase(requireContext().applicationContext)
        ParkMateViewModelFactory(db.appDao())
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Collega l'XML del layout
        return inflater.inflate(R.layout.fragment_vehicles, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 2. OSSERVIAMO I DATI DAL VIEWMODEL
        // Questo è il modo moderno (2026) per leggere i dati: si aggiornano da soli!
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                // Mettiamoci in ascolto della lista dei veicoli
                viewModel.vehiclesList.collect { veicoli ->
                    Log.d("ParkMateTest", "Numero di veicoli nel DB: ${veicoli.size}")
                    veicoli.forEach { veicolo ->
                        Log.d("ParkMateTest", "Ho trovato: ${veicolo.name} (${veicolo.type})")
                    }
                }
            }
        }

        // 3. TESTIAMO L'INSERIMENTO (Solo per prova, poi lo collegheremo a un bottone)
        // De-commenta la riga sotto per provare ad aggiungere un veicolo ogni volta che apri la schermata
        // viewModel.addVehicle("La mia Bici", "Bicycle")
    }
}