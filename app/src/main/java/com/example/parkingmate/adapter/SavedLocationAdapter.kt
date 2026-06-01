package com.example.parkingmate.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.parkingmate.R
import com.example.parkingmate.data.SavedLocation

// Adapter utilizzato dal RecyclerView per mostrare l'elenco delle posizioni salvate.
// Gestisce il collegamento tra i dati presenti nella lista e le card visualizzate a schermo,
// inoltrando inoltre gli eventi di selezione e modifica al componente chiamante.
class SavedLocationAdapter(
    private var locations: List<SavedLocation>,
    private val onItemClick: (SavedLocation) -> Unit,
    private val onEditClick: (SavedLocation) -> Unit
) : RecyclerView.Adapter<SavedLocationAdapter.LocationViewHolder>() {

    // Riferimenti alle view presenti nel layout della singola posizione salvata.
    // In questo modo le view vengono recuperate una sola volta e riutilizzate durante lo scrolling.
    class LocationViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvLocationName)
        val tvType: TextView = view.findViewById(R.id.tvLocationType)
        val btnEdit: ImageButton = view.findViewById(R.id.btnEditLocation)
    }

    // Crea una nuova istanza della card partendo dal layout XML item_saved_location.
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LocationViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_saved_location, parent, false)
        return LocationViewHolder(view)
    }

    // Popola la card con i dati della posizione salvata corrispondente alla riga corrente.
    override fun onBindViewHolder(holder: LocationViewHolder, position: Int) {
        // Estrae dalla lista la posizione da visualizzare in questa card.
        val location = locations[position]
        holder.tvName.text = location.name
        holder.tvType.text = "Tariffa: ${location.defaultType}"

        // Notifica la selezione della posizione quando l'utente preme sulla card.
        holder.itemView.setOnClickListener { onItemClick(location) }
        // Avvia la procedura di modifica della posizione selezionata.
        holder.btnEdit.setOnClickListener { onEditClick(location) }
    }

    override fun getItemCount() = locations.size

    fun updateData(newList: List<SavedLocation>) {
        this.locations = newList
        notifyDataSetChanged()
    }
}