package com.example.parkingmate.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.parkingmate.R
import com.example.parkingmate.data.Vehicle

class VehicleAdapter(
    private var vehicles: List<Vehicle>,
    private val onItemClick: (Vehicle) -> Unit, // <--- Per il click sulla Card
    private val onEditClick: (Vehicle) -> Unit   // <--- Per il click sulla Matita
) : RecyclerView.Adapter<VehicleAdapter.VehicleViewHolder>() {

    class VehicleViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvVehicleName)
        val tvType: TextView = view.findViewById(R.id.tvVehicleType)
        val btnEdit: ImageButton = view.findViewById(R.id.btnEdit) // Assicurati che l'id nel layout sia btnEdit
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VehicleViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_vehicle, parent, false)
        return VehicleViewHolder(view)
    }

    override fun onBindViewHolder(holder: VehicleViewHolder, position: Int) {
        val vehicle = vehicles[position]
        holder.tvName.text = vehicle.name
        holder.tvType.text = vehicle.type

        // Click sulla MATITA (Modifica)
        holder.btnEdit.setOnClickListener { onEditClick(vehicle) }

        // Click sull'INTERA CARD (Parcheggia)
        holder.itemView.setOnClickListener { onItemClick(vehicle) }
    }

    override fun getItemCount() = vehicles.size

    fun updateData(newList: List<Vehicle>) {
        this.vehicles = newList
        notifyDataSetChanged()
    }
}