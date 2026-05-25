package com.abel.pokedexdigital.activity

import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.abel.pokedexdigital.R
import com.abel.pokedexdigital.model.Pokemon
import com.abel.pokedexdigital.network.PokeApiService
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class EditarEquipoActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var service: PokeApiService

    // ID y nombre del equipo que estoy editando
    private var equipoId = ""
    private var equipoNombre = ""

    // Lista de nombres e IDs de los pokémon actuales en el equipo
    private val pokemonEquipo = mutableListOf<String>()
    private val idsEquipo = mutableListOf<Long>()

    // Lista completa de pokémon para el selector
    private val listaPokemonDisponibles = mutableListOf<Pokemon>()

    // Copia completa de la lista original, nunca se modifica
    private val listaTodosDisponibles = mutableListOf<Pokemon>()

    // Tipo activo seleccionado con los chips (null = mostrar todos)
    private var tipoActivo: String? = null

    private lateinit var adapterDisponibles: PokemonSelectorAdapter

    private lateinit var layoutHuecos: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editar_equipo)

        // Pongo la barra de estado en rojo
        window.statusBarColor = android.graphics.Color.parseColor("#CC0000")

        // Ajusto el padding para que no tape la barra del sistema
        val barraRoja = findViewById<LinearLayout>(R.id.barraRojaEditarEquipo)
        ViewCompat.setOnApplyWindowInsetsListener(barraRoja) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, systemBars.top, 0, 0)
            insets
        }

        // Recibo el ID y nombre del equipo desde EquipoActivity
        equipoId = intent.getStringExtra("EQUIPO_ID") ?: ""
        equipoNombre = intent.getStringExtra("EQUIPO_NOMBRE") ?: "Mi Equipo"

        // Muestro el nombre del equipo en la barra superior
        findViewById<TextView>(R.id.tvTituloEquipo).text = equipoNombre

        // El botón volver cierra esta pantalla
        findViewById<ImageButton>(R.id.btnVolverEditarEquipo).setOnClickListener { finish() }

        layoutHuecos = findViewById(R.id.layoutHuecosEquipo)

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

        // Configuro el RecyclerView con la lista de pokémon disponibles para añadir
        val recyclerDisponibles = findViewById<RecyclerView>(R.id.recyclerPokemonDisponibles)
        recyclerDisponibles.layoutManager = LinearLayoutManager(this)
        adapterDisponibles = PokemonSelectorAdapter(listaPokemonDisponibles) { pokemon ->
            // Al pulsar un pokémon de la lista lo busco en la API y lo añado
            buscarPokemon(pokemon.name)
        }
        recyclerDisponibles.adapter = adapterDisponibles

        // Al pulsar "Buscar" llamo a la API con el nombre escrito
        val etBuscar = findViewById<EditText>(R.id.etBuscarPokemonEquipo)
        findViewById<Button>(R.id.btnBuscarPokemon).setOnClickListener {
            val nombre = etBuscar.text.toString().trim().lowercase()
            if (nombre.isNotEmpty()) {
                buscarPokemon(nombre)
            } else {
                Toast.makeText(this, "Escribe un nombre", Toast.LENGTH_SHORT).show()
            }
        }

        // Filtro la lista de disponibles al escribir en el buscador
        etBuscar.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filtrarDisponibles(s.toString())
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        cargarEquipo()
        cargarListaPokemon()
        inflarChipsTipos()
    }

    // Cargo el equipo desde Firebase y dibujo los huecos
    private fun cargarEquipo() {
        val uid = auth.currentUser?.uid ?: return

        db.collection("usuarios").document(uid)
            .collection("equipos").document(equipoId)
            .get()
            .addOnSuccessListener { doc ->
                pokemonEquipo.clear()
                idsEquipo.clear()
                pokemonEquipo.addAll(doc.get("pokemon") as? List<String> ?: emptyList())
                idsEquipo.addAll(doc.get("ids") as? List<Long> ?: emptyList())
                dibujarHuecos()
            }
    }

    // Cargo la lista completa de pokémon para el selector
    private fun cargarListaPokemon() {
        lifecycleScope.launch {
            try {
                val respuesta = service.getPokemonList()
                listaTodosDisponibles.clear()
                listaTodosDisponibles.addAll(respuesta.results)
                listaPokemonDisponibles.clear()
                listaPokemonDisponibles.addAll(listaTodosDisponibles)
                adapterDisponibles.notifyDataSetChanged()
            } catch (e: Exception) {
                println("Error al cargar lista: ${e.message}")
            }
        }
    }

    // Filtro la lista de disponibles según el texto escrito en el buscador
    private fun filtrarDisponibles(texto: String) {
        // Si hay tipo activo filtra sobre la lista ya filtrada por tipo,
        // si no, filtra sobre los 151 completos
        val base = if (tipoActivo != null) listaPokemonDisponibles else listaTodosDisponibles
        val listaFiltrada = if (texto.isEmpty()) base else base.filter { it.name.contains(texto.lowercase()) }
        adapterDisponibles.actualizarLista(listaFiltrada)
    }

    // Filtra la lista según el tipo pulsado. Si tipo es null, restaura todos
    private fun filtrarPorTipo(tipo: String?) {
        tipoActivo = tipo
        val textoBusqueda = findViewById<EditText>(R.id.etBuscarPokemonEquipo).text.toString()

        if (tipo == null) {
            // Chip "Todos" → restaurar lista completa
            listaPokemonDisponibles.clear()
            listaPokemonDisponibles.addAll(listaTodosDisponibles)
            filtrarDisponibles(textoBusqueda)
            return
        }

        lifecycleScope.launch {
            try {
                val respuesta = service.getPokemonPorTipo(tipo)
                val pokemonDelTipo = respuesta.pokemon.mapNotNull { entrada ->
                    listaTodosDisponibles.find { it.name == entrada.pokemon.name }
                }
                listaPokemonDisponibles.clear()
                listaPokemonDisponibles.addAll(pokemonDelTipo)
                filtrarDisponibles(textoBusqueda)
            } catch (e: Exception) {
                Toast.makeText(this@EditarEquipoActivity, "Error cargando tipo $tipo", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Crea los chips de tipos dinámicamente en el HorizontalScrollView
    private fun inflarChipsTipos() {
        val contenedor = findViewById<LinearLayout>(R.id.layoutChipsTipos)
        val dp = resources.displayMetrics.density

        val tipos = listOf(
            "Todos" to "#CC0000",
            "Fuego" to "#F08030",    "Agua" to "#6890F0",   "Planta" to "#78C850",
            "Eléctrico" to "#F8D030","Psíquico" to "#F85888",  "Hielo" to "#98D8D8",
            "Dragón" to "#7038F8",  "Siniestro" to "#705848",  "Hada" to "#EE99AC",
            "Normal" to "#A8A878",  "Luchador" to "#C03028", "Volador" to "#A890F0",
            "Veneno" to "#A040A0",  "Tierra" to "#E0C068",  "Roca" to "#B8A038",
            "Bicho" to "#A8B820",   "Fantasma" to "#705898",  "Acero" to "#B8B8D0"
        )
        val traduccion = mapOf(
            "Fuego" to "fire", "Agua" to "water", "Planta" to "grass",
            "Eléctrico" to "electric", "Psíquico" to "psychic", "Hielo" to "ice",
            "Dragón" to "dragon", "Siniestro" to "dark", "Hada" to "fairy",
            "Normal" to "normal", "Luchador" to "fighting", "Volador" to "flying",
            "Veneno" to "poison", "Tierra" to "ground", "Roca" to "rock",
            "Bicho" to "bug", "Fantasma" to "ghost", "Acero" to "steel"
        )


        tipos.forEach { (nombre, color) ->
            val chip = TextView(this).apply {
                text = nombre.replaceFirstChar { it.uppercase() }
                textSize = 12f
                setTextColor(android.graphics.Color.WHITE)
                val bg = android.graphics.drawable.GradientDrawable()
                bg.cornerRadius = 16 * dp
                bg.setColor(android.graphics.Color.parseColor(color))
                background = bg
                setPadding((12 * dp).toInt(), (6 * dp).toInt(), (12 * dp).toInt(), (6 * dp).toInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins((4 * dp).toInt(), 0, (4 * dp).toInt(), 0) }
                setOnClickListener {
                    filtrarPorTipo(if (nombre == "Todos") null else traduccion[nombre] ?: nombre.lowercase())
                }
            }
            contenedor.addView(chip)
        }
    }

    // Dibujo los 6 huecos del equipo con sprites o vacíos
    private fun dibujarHuecos() {
        layoutHuecos.removeAllViews()
        val densidad = resources.displayMetrics.density
        val tamano = (56 * densidad).toInt()

        for (i in 0 until 6) {
            val contenedor = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(tamano + 8, tamano + 32).apply {
                    setMargins(6, 0, 6, 0)
                }
            }

            val img = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(tamano, tamano)
                scaleType = ImageView.ScaleType.FIT_CENTER
                setBackgroundColor(android.graphics.Color.parseColor("#EEEEEE"))
            }

            if (i < pokemonEquipo.size && i < idsEquipo.size) {
                // Uso el ID numérico para cargar el sprite correctamente
                val id = idsEquipo[i]
                val nombre = pokemonEquipo[i]
                val urlSprite = "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/$id.png"
                Glide.with(this).load(urlSprite).into(img)
                img.setOnClickListener { mostrarOpcionesHueco(nombre) }
            }

            contenedor.addView(img)
            layoutHuecos.addView(contenedor)
        }
    }

    // Busco el pokémon en la API y muestro el resultado debajo del buscador
    private fun buscarPokemon(nombre: String) {
        lifecycleScope.launch {
            try {
                val detalle = service.getPokemonDetalle(nombre)
                val layoutResultado = findViewById<LinearLayout>(R.id.layoutResultadoBusqueda)
                val ivResultado = findViewById<ImageView>(R.id.ivResultadoPokemon)
                val tvResultado = findViewById<TextView>(R.id.tvResultadoNombre)
                val btnAnadir = findViewById<Button>(R.id.btnAnadirAlEquipo)

                // Muestro imagen y nombre del pokémon encontrado
                Glide.with(this@EditarEquipoActivity)
                    .load(detalle.sprites.front_default)
                    .into(ivResultado)
                tvResultado.text = detalle.name.replaceFirstChar { it.uppercase() }
                layoutResultado.visibility = View.VISIBLE

                // Al pulsar "Añadir" lo agrego al equipo si hay hueco libre
                btnAnadir.setOnClickListener {
                    when {
                        pokemonEquipo.size >= 6 ->
                            Toast.makeText(this@EditarEquipoActivity,
                                "El equipo ya está completo (máx. 6)", Toast.LENGTH_SHORT).show()
                        pokemonEquipo.contains(detalle.name) ->
                            Toast.makeText(this@EditarEquipoActivity,
                                "Este Pokémon ya está en el equipo", Toast.LENGTH_SHORT).show()
                        else -> {
                            // Guardo el nombre y el ID numérico para los sprites
                            anadirAlEquipo(detalle.name, detalle.id.toLong())
                            layoutResultado.visibility = View.GONE
                        }
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@EditarEquipoActivity,
                    "Pokémon no encontrado", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Muestro un diálogo para confirmar si quitar el pokémon del equipo
    private fun mostrarOpcionesHueco(nombre: String) {
        AlertDialog.Builder(this)
            .setTitle(nombre.replaceFirstChar { it.uppercase() })
            .setMessage("¿Quieres quitar este Pokémon del equipo?")
            .setPositiveButton("Quitar") { _, _ -> quitarDelEquipo(nombre) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // Añado el pokémon y su ID a las listas y guardo en Firebase
    private fun anadirAlEquipo(nombre: String, id: Long) {
        pokemonEquipo.add(nombre)
        idsEquipo.add(id)
        guardarEquipo()
        dibujarHuecos()
        Toast.makeText(this,
            "✅ ${nombre.replaceFirstChar { it.uppercase() }} añadido",
            Toast.LENGTH_SHORT).show()
    }

    // Quito el pokémon y su ID de las listas y actualizo Firebase
    private fun quitarDelEquipo(nombre: String) {
        val pos = pokemonEquipo.indexOf(nombre)
        if (pos != -1) {
            pokemonEquipo.removeAt(pos)
            if (pos < idsEquipo.size) idsEquipo.removeAt(pos)
        }
        guardarEquipo()
        dibujarHuecos()
        Toast.makeText(this,
            "❌ ${nombre.replaceFirstChar { it.uppercase() }} quitado",
            Toast.LENGTH_SHORT).show()
    }

    // Guardo nombres e IDs actualizados en Firebase
    private fun guardarEquipo() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("usuarios").document(uid)
            .collection("equipos").document(equipoId)
            .update(mapOf(
                "pokemon" to pokemonEquipo,
                "ids" to idsEquipo
            ))
    }
}

// Adaptador para la lista de pokémon disponibles para añadir al equipo
class PokemonSelectorAdapter(
    private var lista: List<Pokemon>,
    private val onItemClick: (Pokemon) -> Unit
) : RecyclerView.Adapter<PokemonSelectorAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivSprite: ImageView = itemView.findViewById(R.id.ivSpritePokemonSelector)
        val tvNombre: TextView = itemView.findViewById(R.id.tvNombrePokemonSelector)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pokemon_selector, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val pokemon = lista[position]

        // Muestro el nombre con la primera letra en mayúscula
        holder.tvNombre.text = pokemon.name.replaceFirstChar { it.uppercase() }

        // Extraigo el ID desde la URL del pokémon para cargar su sprite
        val id = pokemon.url.trimEnd('/').split("/").last()
        val urlSprite = "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/$id.png"
        Glide.with(holder.itemView.context)
            .load(urlSprite)
            .error(android.R.drawable.ic_menu_gallery)
            .into(holder.ivSprite)

        // Al pulsar lo busco en la API para confirmarlo y añadirlo
        holder.itemView.setOnClickListener { onItemClick(pokemon) }
    }

    override fun getItemCount() = lista.size

    // Actualizo la lista cuando el usuario escribe en el buscador
    fun actualizarLista(nuevaLista: List<Pokemon>) {
        lista = nuevaLista
        notifyDataSetChanged()
    }
}