package com.abel.pokedexdigital

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.abel.pokedexdigital.activity.DetallePokemonActivity
import com.abel.pokedexdigital.activity.FavoritosActivity
import com.abel.pokedexdigital.activity.LoginActivity
import com.abel.pokedexdigital.activity.EquipoActivity
import com.abel.pokedexdigital.adapter.PokemonAdapter
import com.abel.pokedexdigital.model.Pokemon
import com.abel.pokedexdigital.network.PokeApiService
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: PokemonAdapter
    private lateinit var etBuscar: EditText

    // Guardo todos los pokémon cargados de la API
    private var listaTodos = listOf<Pokemon>()

    // Lista que muestro en pantalla, puede estar filtrada
    private val listaFiltrada = mutableListOf<Pokemon>()

    // Tipo seleccionado actualmente ("todos" por defecto)
    private var tipoSeleccionado = "todos"

    // Texto escrito en el buscador
    private var textoBusqueda = ""

    // Lista de tipos con su clave en inglés y su nombre en español
    private val tipos = listOf(
        "todos"    to "Todos",
        "normal"   to "Normal",
        "fire"     to "Fuego",
        "water"    to "Agua",
        "grass"    to "Planta",
        "electric" to "Eléctrico",
        "ice"      to "Hielo",
        "fighting" to "Lucha",
        "psychic"  to "Psíquico",
        "dragon"   to "Dragón",
        "dark"     to "Siniestro",
        "fairy"    to "Hada",
        "poison"   to "Veneno",
        "ground"   to "Tierra",
        "flying"   to "Volador",
        "rock"     to "Roca",
        "bug"      to "Bicho",
        "ghost"    to "Fantasma",
        "steel"    to "Acero",

    )

    // Color de fondo para cada chip de tipo
    private val coloresTipos = mapOf(
        "todos"    to "#CC0000",
        "normal"   to "#A8A878",
        "fire"     to "#F08030",
        "water"    to "#6890F0",
        "grass"    to "#78C850",
        "electric" to "#F8D030",
        "ice"      to "#98D8D8",
        "fighting" to "#C03028",
        "psychic"  to "#F85888",
        "dragon"   to "#7038F8",
        "dark"     to "#705848",
        "fairy"    to "#EE99AC",
        "poison"   to "#A040A0",
        "ground"   to "#E0C068",
        "flying"   to "#A890F0",
        "rock"     to "#B8A038",
        "bug"      to "#A8B820",
        "ghost"    to "#705898",
        "steel"    to "#B8B8D0",
    )

    private lateinit var service: PokeApiService

    // Guardo los chips de tipo para poder cambiar su opacidad al seleccionar uno
    private var botonesChip = mutableListOf<TextView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Pongo la barra de estado en rojo
        window.statusBarColor = Color.parseColor("#CC0000")

        // Ajusto el padding de la barra roja para que no tape la barra del sistema
        val barraRoja = findViewById<LinearLayout>(R.id.barraRoja)
        ViewCompat.setOnApplyWindowInsetsListener(barraRoja) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, systemBars.top, 0, 0)
            insets
        }

        // Enlazo los elementos del layout
        etBuscar = findViewById(R.id.etBuscar)
        recyclerView = findViewById(R.id.recyclerViewPokemon)
        recyclerView.layoutManager = LinearLayoutManager(this)

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

        // Al pulsar el botón de cuenta muestro un menú con dos opciones:
        // "Mi Equipo" para ir a la pantalla de equipos
        // "Cerrar sesión" para salir de la cuenta
        val btnCuenta = findViewById<ImageButton>(R.id.btnLogout)
        btnCuenta.setOnClickListener {
            val popup = PopupMenu(this, btnCuenta)
            popup.menu.add(0, 1, 0, "⚔️  Mi Equipo")
            popup.menu.add(0, 2, 0, "🚪  Cerrar sesión")
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> startActivity(Intent(this, EquipoActivity::class.java))
                    2 -> {
                        FirebaseAuth.getInstance().signOut()
                        startActivity(Intent(this, LoginActivity::class.java))
                        finish()
                    }
                }
                true
            }
            popup.show()
        }

        // Al pulsar el corazón abro la pantalla de favoritos
        val btnIrFavoritos = findViewById<ImageButton>(R.id.btnIrFavoritos)
        btnIrFavoritos.setOnClickListener {
            startActivity(Intent(this, FavoritosActivity::class.java))
        }

        // Al pulsar el filtro muestro un menú para ordenar la lista
        val btnFiltro = findViewById<ImageButton>(R.id.btnFiltro)
        btnFiltro.setOnClickListener {
            val popup = PopupMenu(this, btnFiltro)
            popup.menu.add(0, 1, 0, "Nº Pokédex")
            popup.menu.add(0, 2, 0, "Alfabético")
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> listaFiltrada.sortBy { obtenerNumero(it.url) }
                    2 -> listaFiltrada.sortBy { it.name }
                }
                adapter.notifyDataSetChanged()
                true
            }
            popup.show()
        }

        // Creo el adaptador que conecta la lista con el RecyclerView
        adapter = PokemonAdapter(listaFiltrada) { pokemon ->
            val intent = Intent(this, DetallePokemonActivity::class.java)
            intent.putExtra("NOMBRE_POKEMON", pokemon.name)
            startActivity(intent)
        }
        recyclerView.adapter = adapter

        // Cada vez que escribo en el buscador aplico los filtros
        etBuscar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                textoBusqueda = s.toString()
                aplicarFiltros()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Creo los chips de tipo y cargo la lista de pokémon
        crearChipsTipos()
        cargarPokemon()
    }

    // Genero los botones de filtro por tipo en la barra horizontal
    private fun crearChipsTipos() {
        val layoutTipos = findViewById<LinearLayout>(R.id.layoutTipos)
        layoutTipos.removeAllViews()
        botonesChip.clear()

        tipos.forEach { (clave, nombre) ->
            val chip = TextView(this).apply {
                text = nombre
                textSize = 13f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(24, 8, 24, 8)

                // Fondo redondeado con el color del tipo
                val color = coloresTipos[clave] ?: "#CC0000"
                val bg = android.graphics.drawable.GradientDrawable()
                bg.setColor(Color.parseColor(color))
                bg.cornerRadius = 50f
                background = bg

                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.setMargins(8, 0, 8, 0)
                layoutParams = params

                // El chip "Todos" aparece al 100% de opacidad, el resto al 50%
                alpha = if (clave == "todos") 1f else 0.5f
            }

            // Al pulsar un chip selecciono ese tipo y filtro la lista
            chip.setOnClickListener {
                tipoSeleccionado = clave
                botonesChip.forEach { it.alpha = 0.5f }
                chip.alpha = 1f
                filtrarPorTipo(clave)
            }

            botonesChip.add(chip)
            layoutTipos.addView(chip)
        }
    }

    // Filtro la lista por tipo llamando a la API
    private fun filtrarPorTipo(tipo: String) {
        if (tipo == "todos") {
            aplicarFiltros()
            return
        }
        lifecycleScope.launch {
            try {
                val resultado = service.getPokemonPorTipo(tipo)
                val nombresTipo = resultado.pokemon.map { it.pokemon.name }.toSet()
                listaFiltrada.clear()
                listaFiltrada.addAll(listaTodos.filter {
                    it.name in nombresTipo &&
                            (textoBusqueda.isEmpty() || it.name.contains(textoBusqueda.lowercase()))
                })
                adapter.notifyDataSetChanged()
            } catch (e: Exception) {
                println("Error al filtrar por tipo: ${e.message}")
            }
        }
    }

    // Aplico el filtro de texto sobre la lista completa o la del tipo activo
    private fun aplicarFiltros() {
        if (tipoSeleccionado == "todos") {
            listaFiltrada.clear()
            if (textoBusqueda.isEmpty()) {
                listaFiltrada.addAll(listaTodos)
            } else {
                listaFiltrada.addAll(listaTodos.filter {
                    it.name.contains(textoBusqueda.lowercase())
                })
            }
            adapter.notifyDataSetChanged()
        } else {
            filtrarPorTipo(tipoSeleccionado)
        }
    }

    // Extraigo el número del pokémon desde su URL para ordenar por Pokédex
    private fun obtenerNumero(url: String): Int {
        return url.trimEnd('/').split("/").last().toIntOrNull() ?: 0
    }

    // Cargo la lista completa de pokémon desde la API al iniciar la pantalla
    private fun cargarPokemon() {
        lifecycleScope.launch {
            try {
                val respuesta = service.getPokemonList()
                listaTodos = respuesta.results
                listaFiltrada.clear()
                listaFiltrada.addAll(listaTodos)
                adapter.notifyDataSetChanged()
            } catch (e: Exception) {
                println("Error al cargar Pokémon: ${e.message}")
            }
        }
    }
}