package com.abel.pokedexdigital.network

import com.abel.pokedexdigital.model.Pokemon
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

// Respuesta de la lista general
data class PokeApiResponse(
    val results: List<Pokemon>
)

// Estadísticas
data class Stat(val base_stat: Int, val stat: StatNombre)
data class StatNombre(val name: String)

// Tipos
data class TipoPokemon(val type: TipoNombre)
data class TipoNombre(val name: String)

// Sprites (imágenes)
data class Sprites(val front_default: String?)

// Detalle completo del Pokémon
data class PokemonDetalle(
    val id: Int,
    val name: String,
    val sprites: Sprites,
    val types: List<TipoPokemon>,
    val stats: List<Stat>,
    val species: EspecieRef
)

// Referencia a la especie (para obtener evolución y descripción)
data class EspecieRef(val url: String)

// Respuesta de la especie
data class EspecieRespuesta(
    val flavor_text_entries: List<FlavorText>,
    val evolution_chain: EvolutionChainRef
)
data class FlavorText(val flavor_text: String, val language: Idioma)
data class Idioma(val name: String)
data class EvolutionChainRef(val url: String)

// Cadena de evolución
data class EvolutionChainRespuesta(val chain: ChainLink)
data class ChainLink(
    val species: EspecieSimple,
    val evolves_to: List<ChainLink>,
    val evolution_details: List<EvolutionDetail>
)
data class EspecieSimple(val name: String)
data class EvolutionDetail(
    val min_level: Int?,
    val item: NamedResource?
)
data class NamedResource(val name: String)

// Filtro por tipo
data class TipoRespuesta(val pokemon: List<TipoEntrada>)
data class TipoEntrada(val pokemon: PokemonSimple)
data class PokemonSimple(val name: String)

interface PokeApiService {

    @GET("pokemon")
    suspend fun getPokemonList(
        @Query("limit") limit: Int = 151,
        @Query("offset") offset: Int = 0
    ): PokeApiResponse

    @GET("pokemon/{nombre}")
    suspend fun getPokemonDetalle(
        @Path("nombre") nombre: String
    ): PokemonDetalle

    @GET
    suspend fun getEspecie(
        @retrofit2.http.Url url: String
    ): EspecieRespuesta

    @GET
    suspend fun getEvolutionChain(
        @retrofit2.http.Url url: String
    ): EvolutionChainRespuesta

    @GET("type/{tipo}")
    suspend fun getPokemonPorTipo(
        @Path("tipo") tipo: String
    ): TipoRespuesta
}