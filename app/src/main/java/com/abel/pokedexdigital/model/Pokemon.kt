package com.abel.pokedexdigital.model

// Data class que representa un Pokémon básico recibido desde la PokeAPI
// Contiene el nombre y la URL con los detalles de cada Pokémon
data class Pokemon(
    val name: String,   // Nombre del Pokémon (ej: "bulbasaur")
    val url: String     // URL con los detalles completos del Pokémon
)
