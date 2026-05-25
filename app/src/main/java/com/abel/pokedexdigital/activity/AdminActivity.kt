package com.abel.pokedexdigital.activity

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
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

class AdminActivity : AppCompatActivity() {

    private val db   = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var service: PokeApiService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin)

        // Pongo la barra de estado en rojo igual que el resto de la app
        window.statusBarColor = android.graphics.Color.parseColor("#CC0000")

        // Ajusto el padding para la barra del sistema
        val barraRoja = findViewById<LinearLayout>(R.id.barraRojaAdmin)
        ViewCompat.setOnApplyWindowInsetsListener(barraRoja) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, systemBars.top, 0, 0)
            insets
        }

        // El botón volver cierra este panel
        findViewById<ImageButton>(R.id.btnVolverAdmin).setOnClickListener { finish() }
        // Al pulsar el botón de cuenta cierro sesión y vuelvo al login
        // El FLAG_ACTIVITY_CLEAR_TASK elimina el histórico para que no pueda volver atrás
        findViewById<ImageButton>(R.id.btnLogout).setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }

        // Verifico que el usuario actual tiene rol admin antes de cargar datos
        // (segunda comprobación de seguridad además de la del login)
        val uid = auth.currentUser?.uid
        if (uid == null) { finish(); return }

        comprobarRolAdmin(uid)

        // Configuro Retrofit para obtener la lista de pokémon
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

        // Cargo las estadísticas de Firestore y la lista de pokémon
        cargarEstadisticas()
        cargarListaPokemon()
    }

    // Compruebo en Firestore que el usuario que abre esta pantalla es realmente admin
    private fun comprobarRolAdmin(uid: String) {
        db.collection("usuarios").document(uid)
            .get()
            .addOnSuccessListener { doc ->
                val rol = doc.getString("rol") ?: "usuario"
                if (rol != "admin") {
                    // Si no es admin lo expulso inmediatamente
                    Toast.makeText(this, "Acceso denegado", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .addOnFailureListener { finish() }
    }

    // Cuento el total de usuarios y el total de equipos en Firestore
    private fun cargarEstadisticas() {
        val tvUsuarios = findViewById<TextView>(R.id.tvNumUsuarios)
        val tvEquipos  = findViewById<TextView>(R.id.tvNumEquipos)

        // Cuento todos los documentos de la colección "usuarios"
        db.collection("usuarios")
            .get()
            .addOnSuccessListener { resultado ->
                // Muestro el número de usuarios registrados
                tvUsuarios.text = resultado.size().toString()

                // Para cada usuario cuento sus equipos y sumo el total
                var totalEquipos       = 0
                var usuariosProcesados = 0
                val totalUsuarios      = resultado.size()

                if (totalUsuarios == 0) {
                    tvEquipos.text = "0"
                    return@addOnSuccessListener
                }

                resultado.documents.forEach { doc ->
                    db.collection("usuarios").document(doc.id)
                        .collection("equipos")
                        .get()
                        .addOnSuccessListener { equipos ->
                            totalEquipos += equipos.size()
                            usuariosProcesados++
                            // Cuando haya procesado todos los usuarios muestro el total
                            if (usuariosProcesados == totalUsuarios) {
                                tvEquipos.text = totalEquipos.toString()
                            }
                        }
                        .addOnFailureListener {
                            usuariosProcesados++
                            if (usuariosProcesados == totalUsuarios) {
                                tvEquipos.text = totalEquipos.toString()
                            }
                        }
                }
            }
            .addOnFailureListener {
                tvUsuarios.text = "—"
                tvEquipos.text  = "—"
            }
    }

    // Descargo los primeros 151 pokémon y los muestro en el RecyclerView
    private fun cargarListaPokemon() {
        lifecycleScope.launch {
            try {
                val respuesta = service.getPokemonList(limit = 151)
                val recycler  = findViewById<RecyclerView>(R.id.recyclerAdminPokemon)
                recycler.layoutManager = LinearLayoutManager(this@AdminActivity)
                recycler.adapter       = AdminPokemonAdapter(respuesta.results)
            } catch (e: Exception) {
                Toast.makeText(this@AdminActivity,
                    "Error al cargar pokémon: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

// ── Adaptador interno para la lista de pokémon del panel admin ────────────────
class AdminPokemonAdapter(
    private val lista: List<Pokemon>
) : RecyclerView.Adapter<AdminPokemonAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivSprite : ImageView = itemView.findViewById(R.id.ivSpriteAdmin)
        val tvNombre : TextView  = itemView.findViewById(R.id.tvNombreAdmin)
        val tvId     : TextView  = itemView.findViewById(R.id.tvIdAdmin)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // Inflo el layout del item de pokémon para el panel admin
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pokemon_admin, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val pokemon = lista[position]

        // Extraigo el ID numérico desde la URL del pokémon
        val id = pokemon.url.trimEnd('/').split("/").last()

        holder.tvNombre.text = "#$id  ${pokemon.name.replaceFirstChar { it.uppercase() }}"
        holder.tvId.text     = "ID: $id"

        // Cargo el sprite usando el ID extraído
        val urlSprite = "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/$id.png"
        Glide.with(holder.itemView.context)
            .load(urlSprite)
            .error(android.R.drawable.ic_menu_gallery)
            .into(holder.ivSprite)

        // Al pulsar el pokémon abro la pantalla de detalle
        holder.itemView.setOnClickListener {
            val intent = Intent(holder.itemView.context, DetallePokemonActivity::class.java)
            // Paso el nombre del pokémon para que la pantalla de detalle lo cargue
            intent.putExtra("NOMBRE_POKEMON", pokemon.name)
            holder.itemView.context.startActivity(intent)
        }

        // Al mantener pulsado muestro el diálogo para editar la descripción
        holder.itemView.setOnLongClickListener {
            val db      = FirebaseFirestore.getInstance()
            val context = holder.itemView.context

            // Primero leo si ya tiene descripción guardada en Firestore
            db.collection("pokemonAdmin").document(pokemon.name)
                .get()
                .addOnSuccessListener { doc ->
                    val descripcionActual = doc.getString("descripcion") ?: ""

                    // Creo un campo de texto con la descripción actual para que el admin la edite
                    val input = android.widget.EditText(context).apply {
                        setText(descripcionActual)
                        hint = "Descripción del pokémon..."
                        setPadding(40, 20, 40, 20)
                    }

                    // Muestro el diálogo con el campo de texto
                    android.app.AlertDialog.Builder(context)
                        .setTitle("✏️ ${pokemon.name.replaceFirstChar { it.uppercase() }}")
                        .setView(input)
                        .setPositiveButton("Guardar") { _, _ ->
                            val nuevaDesc = input.text.toString().trim()
                            // Guardo la descripción en la colección "pokemonAdmin" de Firestore
                            db.collection("pokemonAdmin").document(pokemon.name)
                                .set(mapOf("descripcion" to nuevaDesc))
                                .addOnSuccessListener {
                                    android.widget.Toast.makeText(
                                        context, "Guardado", android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                        }
                        .setNegativeButton("Cancelar", null)
                        .show()
                }
            // Consumo el evento para que no se dispare también el click normal
            true
        }
    }

    override fun getItemCount() = lista.size
}