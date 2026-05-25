package com.abel.pokedexdigital.activity

import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.abel.pokedexdigital.R
import com.abel.pokedexdigital.network.ChainLink
import com.abel.pokedexdigital.network.EvolutionDetail
import com.abel.pokedexdigital.network.PokeApiService
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class DetallePokemonActivity : AppCompatActivity() {

    // Uso este servicio para llamar a la PokeAPI
    private lateinit var service: PokeApiService

    // Aquí dibujo la cadena de evolución
    private lateinit var layoutEvoluciones: LinearLayout

    // Botón para añadir o quitar de favoritos
    private lateinit var btnFavorito: Button

    // Conecto con Firestore para guardar los favoritos
    private val db = FirebaseFirestore.getInstance()

    // Necesito saber qué usuario está logueado
    private val auth = FirebaseAuth.getInstance()

    // Guardo si el pokémon actual ya es favorito
    private var esFavorito = false

    // Guardo el nombre del pokémon que estoy mostrando
    private var nombrePokemon = ""
    // Reproductor de audio para el grito del pokémon
    private var mediaPlayer: android.media.MediaPlayer? = null

    // Traduzco los nombres de las piedras evolutivas al español
    private val piedras = mapOf(
        "fire-stone"    to "Piedra fuego",
        "water-stone"   to "Piedra agua",
        "thunder-stone" to "Piedra trueno",
        "leaf-stone"    to "Piedra hoja",
        "moon-stone"    to "Piedra lunar"
    )

    // Devuelvo el requisito de evolución en texto legible (nivel, piedra o intercambio)
    private fun obtenerRequisito(detalles: List<EvolutionDetail>): String {
        val d = detalles.firstOrNull() ?: return "?"
        return when {
            d.item != null      -> piedras[d.item.name] ?: d.item.name
            d.min_level != null -> "Nv. ${d.min_level}"
            else                -> "Intercambio"
        }
    }

    // Recorro la cadena de evolución y devuelvo todos los eslabones en orden
    private fun recopilarEslabones(link: ChainLink): List<ChainLink> {
        val lista = mutableListOf<ChainLink>()
        var actual: ChainLink? = link
        while (actual != null) {
            lista.add(actual)
            actual = actual.evolves_to.firstOrNull()
        }
        return lista
    }

    // Separo la cadena en ramas (por si hay evoluciones múltiples como Eevee)
    private fun recopilarRamas(link: ChainLink): List<List<ChainLink>> {
        return if (link.evolves_to.isEmpty()) {
            // El pokémon no evoluciona
            listOf(listOf(link))
        } else if (link.evolves_to.size == 1) {
            // Cadena lineal normal, ej: Bulbasaur → Ivysaur → Venusaur
            listOf(recopilarEslabones(link))
        } else {
            // Varias evoluciones posibles, ej: Eevee → Vaporeon / Jolteon / etc.
            link.evolves_to.map { listOf(link, it) }
        }
    }

    // Creo el bloque visual de un pokémon en la cadena: imagen arriba y nombre abajo
    private suspend fun crearItemPokemon(nombre: String): LinearLayout {

        // Uso peso 1f para que cada pokémon ocupe el mismo espacio en pantalla
        val contenedor = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        // Calculo el tamaño de la imagen en píxeles según la densidad del móvil (80dp)
        val densidad = resources.displayMetrics.density
        val tamanoImg = (80 * densidad).toInt()

        // ImageView donde cargo el sprite del pokémon
        val img = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(tamanoImg, tamanoImg).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        // Texto con el nombre del pokémon debajo de su imagen
        val txt = TextView(this).apply {
            text = nombre.replaceFirstChar { it.uppercase() }
            textSize = 14f
            gravity = Gravity.CENTER
            maxLines = 2 // Máximo 2 líneas para que no se corte en móviles pequeños
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setTextColor(android.graphics.Color.parseColor("#212121"))
        }

        // Cargo el sprite desde la API con Glide
        try {
            val d = service.getPokemonDetalle(nombre)
            Glide.with(this).load(d.sprites.front_default).into(img)
        } catch (e: Exception) {}

        contenedor.addView(img)
        contenedor.addView(txt)
        return contenedor
    }

    // Creo la flecha con el requisito que aparece entre dos pokémon de la cadena
    private fun crearFlecha(requisito: String): LinearLayout {
        val contenedor = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(4, 0, 4, 0) }
        }

        // Muestro el requisito encima de la flecha (ej: "Nv. 16")
        val txtReq = TextView(this).apply {
            text = requisito
            textSize = 10f
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.parseColor("#757575"))
        }

        // Flecha roja →
        val txtFlecha = TextView(this).apply {
            text = "→"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.parseColor("#CC0000"))
        }

        contenedor.addView(txtReq)
        contenedor.addView(txtFlecha)
        return contenedor
    }

    // Dibujo una fila completa de evoluciones en el layout
    private suspend fun dibujarCadena(rama: List<ChainLink>) {
        val fila = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 20 }
        }

        // Por cada eslabón añado su bloque visual y, si no es el último, la flecha
        rama.forEachIndexed { index, eslabon ->
            fila.addView(crearItemPokemon(eslabon.species.name))
            if (index < rama.size - 1) {
                val siguiente = rama[index + 1]
                val requisito = obtenerRequisito(siguiente.evolution_details)
                fila.addView(crearFlecha(requisito))
            }
        }
        layoutEvoluciones.addView(fila)
    }

    // Consulto Firestore para saber si este pokémon ya está en mis favoritos
    private fun comprobarFavorito() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("usuarios").document(uid)
            .collection("favoritos").document(nombrePokemon)
            .get()
            .addOnSuccessListener { doc ->
                esFavorito = doc.exists()
                actualizarBotonFavorito()
            }
    }

    // Actualizo el texto y color del botón según el estado del favorito
    private fun actualizarBotonFavorito() {
        if (esFavorito) {
            btnFavorito.text = "♥ Quitar de favoritos"
            btnFavorito.backgroundTintList =
                android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.parseColor("#757575") // Gris cuando ya es favorito
                )
        } else {
            btnFavorito.text = "♡ Añadir a favoritos"
            btnFavorito.backgroundTintList =
                android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.parseColor("#CC0000") // Rojo cuando no es favorito
                )
        }
    }

    // Añado o elimino el pokémon de favoritos en Firestore
    private fun toggleFavorito() {
        val uid = auth.currentUser?.uid ?: return
        val ref = db.collection("usuarios").document(uid)
            .collection("favoritos").document(nombrePokemon)

        if (esFavorito) {
            // Si ya es favorito lo elimino
            ref.delete().addOnSuccessListener {
                esFavorito = false
                actualizarBotonFavorito()
                Toast.makeText(this, "Eliminado de favoritos", Toast.LENGTH_SHORT).show()
            }
        } else {
            // Si no es favorito lo guardo con nombre, número e imagen
            val infoPokedex = findViewById<TextView>(R.id.tvNumeroNombre).text.toString()
            val numero = infoPokedex.split(" ").firstOrNull() ?: "#???"
            val datosParaGuardar = mapOf(
                "nombre"    to nombrePokemon,
                "numero"    to numero,
                "urlImagen" to "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/${numero.replace("#", "").toIntOrNull() ?: 1}.png"
            )
            ref.set(datosParaGuardar).addOnSuccessListener {
                esFavorito = true
                actualizarBotonFavorito()
                Toast.makeText(this, "Añadido a favoritos", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detalle_pokemon)

        // Pongo la barra de estado en rojo
        window.statusBarColor = android.graphics.Color.parseColor("#CC0000")

        // Ajusto el padding de la barra roja para que no tape la barra del sistema
        val barraRoja = findViewById<LinearLayout>(R.id.barraRojaDetalle)
        ViewCompat.setOnApplyWindowInsetsListener(barraRoja) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, systemBars.top, 0, 0)
            insets
        }

        // Enlazo los elementos del layout
        layoutEvoluciones = findViewById(R.id.layoutEvoluciones)
        btnFavorito = findViewById(R.id.btnFavorito)

        // Enlazo el botón volver correctamente como ImageButton
        findViewById<ImageButton>(R.id.btnVolver).setOnClickListener { finish() }

        // Recibo el nombre del pokémon que me manda MainActivity
        nombrePokemon = intent.getStringExtra("NOMBRE_POKEMON") ?: "desconocido"

        comprobarFavorito()
        btnFavorito.setOnClickListener { toggleFavorito() }

        // Configuro Retrofit para conectar con la PokeAPI
        val retrofit = Retrofit.Builder()
            .baseUrl("https://pokeapi.co/api/v2/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(
                okhttp3.OkHttpClient.Builder()
                    .hostnameVerifier { _, _ -> true }
                    .build()
            )
            .build()

        service = retrofit.create(PokeApiService::class.java)

        // Lanzo una corrutina para hacer las llamadas a la API sin bloquear la pantalla
        lifecycleScope.launch {
            try {
                // 1. Obtengo los datos básicos del pokémon
                val detalle = service.getPokemonDetalle(nombrePokemon)

                // Reproduzco el grito del pokémon usando su ID numérico
                // La URL sigue el formato: https://pokemoncries.com/cries/{id}.ogg
                val urlSonido = "https://play.pokemonshowdown.com/audio/cries/${nombrePokemon.lowercase()}.mp3"
                try {
                    mediaPlayer?.release() // Libero el reproductor anterior si hubiera uno
                    mediaPlayer = android.media.MediaPlayer().apply {
                        setDataSource(urlSonido)
                        setVolume(1.0f, 1.0f)  // Fuerzo el volumen al máximo
                        prepareAsync() // Cargo el audio en segundo plano sin bloquear la UI
                        setOnPreparedListener { start() } // Cuando esté listo, lo reproduzco
                    }
                } catch (e: Exception) {
                    println("Error al reproducir sonido: ${e.message}")
                }

                // Muestro el número formateado (#001) y el nombre
                findViewById<TextView>(R.id.tvNumeroNombre).text =
                    "#${detalle.id.toString().padStart(3, '0')} ${detalle.name.replaceFirstChar { it.uppercase() }}"

                // Traduzco los tipos al español
                val traducciones = mapOf(
                    "normal"   to "Normal",    "fire"     to "Fuego",
                    "water"    to "Agua",      "electric" to "Eléctrico",
                    "grass"    to "Planta",    "ice"      to "Hielo",
                    "fighting" to "Lucha",     "poison"   to "Veneno",
                    "ground"   to "Tierra",    "flying"   to "Volador",
                    "psychic"  to "Psíquico",  "bug"      to "Bicho",
                    "rock"     to "Roca",      "ghost"    to "Fantasma",
                    "dragon"   to "Dragón",    "dark"     to "Siniestro",
                    "steel"    to "Acero",     "fairy"    to "Hada"
                )

                // Uno los tipos con " / " (ej: "Fuego / Volador")
                val tipos = detalle.types.joinToString(" / ") {
                    traducciones[it.type.name] ?: it.type.name.replaceFirstChar { c -> c.uppercase() }
                }
                findViewById<TextView>(R.id.tvTipos).text = "Tipo: $tipos"

                // Traduzco los nombres de las estadísticas al español
                val traducccionesStats = mapOf(
                    "hp"              to "PS",
                    "attack"          to "Ataque",
                    "defense"         to "Defensa",
                    "special-attack"  to "Ataque Esp.",
                    "special-defense" to "Defensa Esp.",
                    "speed"           to "Velocidad"
                )

                // Muestro cada estadística en una línea distinta (\n = salto de línea real)
                val stats = detalle.stats.joinToString("\n") {
                    val nombreStat = traducccionesStats[it.stat.name]
                        ?: it.stat.name.replaceFirstChar { c -> c.uppercase() }
                    "$nombreStat: ${it.base_stat}"
                }
                findViewById<TextView>(R.id.tvEstadisticas).text = stats

                // Cargo el sprite principal con Glide
                Glide.with(this@DetallePokemonActivity)
                    .load(detalle.sprites.front_default)
                    .into(findViewById(R.id.ivPokemon))

                // 2. Obtengo los datos de la especie (descripción y cadena de evolución)
                val especie = service.getEspecie(detalle.species.url)

                // Busco la descripción en español; si no existe, uso la inglesa
                val descripcion = especie.flavor_text_entries
                    .firstOrNull { it.language.name == "es" }
                    ?.flavor_text?.replace("\n", " ")
                    ?: especie.flavor_text_entries
                        .firstOrNull { it.language.name == "en" }
                        ?.flavor_text?.replace("\n", " ")
                    ?: "Sin descripción disponible"

                findViewById<TextView>(R.id.tvDescripcion).text = descripcion

                // 3. Obtengo y dibujo la cadena de evolución
                val cadena = service.getEvolutionChain(especie.evolution_chain.url)
                layoutEvoluciones.removeAllViews() // Limpio por si se recarga la pantalla

                val ramas = recopilarRamas(cadena.chain)
                ramas.forEach { rama -> dibujarCadena(rama) }

            } catch (e: Exception) {
                println("Error al cargar detalle: ${e.message}")
            }
        }
    }
    // Cuando el usuario vuelve atrás libero el MediaPlayer para no desperdiciar memoria
    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}