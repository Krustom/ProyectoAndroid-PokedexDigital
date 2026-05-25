package com.abel.pokedexdigital.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.abel.pokedexdigital.R
import com.abel.pokedexdigital.model.Pokemon
import com.bumptech.glide.Glide

class PokemonAdapter(
    private val pokemonList: List<Pokemon>,
    private val onItemClick: (Pokemon) -> Unit
) : RecyclerView.Adapter<PokemonAdapter.PokemonViewHolder>() {

    inner class PokemonViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvNombre: TextView = itemView.findViewById(R.id.tvNombreFavorito)
        val tvNumero: TextView = itemView.findViewById(R.id.tvNumeroFavorito)
        val ivPokemon: ImageView = itemView.findViewById(R.id.ivPokemonFavorito)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PokemonViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pokemon, parent, false)
        return PokemonViewHolder(view)
    }

    override fun onBindViewHolder(holder: PokemonViewHolder, position: Int) {
        val pokemon = pokemonList[position]

        // Formateo el nombre
        holder.tvNombre.text = pokemon.name.replaceFirstChar { it.uppercase() }

        // Saco el número de la URL para mostrarlo y para cargar la imagen
        val numero = pokemon.url.trimEnd('/').split("/").last()
        holder.tvNumero.text = "#${numero.padStart(3, '0')}"

        // Cargo la imagen oficial con Glide
        val urlImagen = "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/$numero.png"
        Glide.with(holder.itemView.context)
            .load(urlImagen)
            .into(holder.ivPokemon)

        holder.itemView.setOnClickListener { onItemClick(pokemon) }
    }

    override fun getItemCount() = pokemonList.size
}
