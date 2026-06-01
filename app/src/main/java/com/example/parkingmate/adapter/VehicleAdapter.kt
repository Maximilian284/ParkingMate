package com.example.parkingmate.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.parkingmate.R
import com.example.parkingmate.data.Vehicle

// Adapter del RecyclerView responsabile della visualizzazione dei veicoli registrati.
// Collega i dati del modello Vehicle alle card mostrate nell'interfaccia e gestisce gli eventi di selezione e modifica dei singoli elementi.
class VehicleAdapter(
    private var vehicles: List<Vehicle>,
    private val onItemClick: (Vehicle) -> Unit,
    private val onEditClick: (Vehicle) -> Unit
) : RecyclerView.Adapter<VehicleAdapter.VehicleViewHolder>() {

    // Mantiene i riferimenti alle view della singola card per evitarne
    // il recupero ripetuto durante le operazioni di binding.
    class VehicleViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvVehicleName)
        val tvType: TextView = view.findViewById(R.id.tvVehicleType)
        val btnEdit: ImageButton = view.findViewById(R.id.btnEdit)
    }

    // Crea una nuova card a partire dal layout XML item_vehicle.
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VehicleViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_vehicle, parent, false)
        return VehicleViewHolder(view)
    }

    // Associa i dati del veicolo corrente agli elementi grafici della card.
    override fun onBindViewHolder(holder: VehicleViewHolder, position: Int) {
        // Recupera il veicolo corrispondente alla posizione richiesta.
        val vehicle = vehicles[position]
        holder.tvName.text = vehicle.name
        holder.tvType.text = vehicle.type

        // Modifica il veicolo selezionato.
        holder.btnEdit.setOnClickListener { onEditClick(vehicle) }

        // Apre la pagina per parcheggiare il veicolo selezionato.
        holder.itemView.setOnClickListener { onItemClick(vehicle) }
    }

    override fun getItemCount() = vehicles.size

    fun updateData(newList: List<Vehicle>) {
        this.vehicles = newList
        notifyDataSetChanged()
    }
}