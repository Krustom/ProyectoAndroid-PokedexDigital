package com.abel.pokedexdigital.activity

import android.widget.LinearLayout
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.abel.pokedexdigital.R
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

// Estructura de datos de cada pokémon favorito guardado en Firebase
data class PokemonFavorito(
    val nombre: String = "",
    val numero: String = "",
    val urlImagen: String = ""
)

class FavoritosActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // Lista completa que cargo desde Firebase
    private val listaFavoritos = mutableListOf<PokemonFavorito>()

    // Lista filtrada que muestro en pantalla según lo que escribe el usuario
    private val listaFiltrada = mutableListOf<PokemonFavorito>()

    private lateinit var adapter: FavoritosAdapter
    private lateinit var etBuscar: EditText

    override fun onRestart() {
        super.onRestart()
        // Cuando vuelvo de la pantalla de detalle, recargo por si hubo cambios
        cargarFavoritos()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_favoritos)

        // Pongo la barra de estado en rojo igual que el resto de la app
        window.statusBarColor = android.graphics.Color.parseColor("#CC0000")

        // Ajusto el padding de la barra roja para que no tape la barra del sistema
        val barraRoja = findViewById<LinearLayout>(R.id.barraRojaFavoritos)
        ViewCompat.setOnApplyWindowInsetsListener(barraRoja) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, systemBars.top, 0, 0)
            insets
        }

        // El botón volver cierra esta pantalla
        findViewById<ImageButton>(R.id.btnVolver).setOnClickListener { finish() }

        // Enlazo el buscador
        etBuscar = findViewById(R.id.etBuscarFavorito)

        val recycler = findViewById<RecyclerView>(R.id.recyclerFavoritos)
        recycler.layoutManager = LinearLayoutManager(this)

        // Paso la listaFiltrada al adaptador, que es la que se muestra en pantalla
        adapter = FavoritosAdapter(listaFiltrada,
            onItemClick = { pokemon ->
                val intent = Intent(this, DetallePokemonActivity::class.java)
                intent.putExtra("NOMBRE_POKEMON", pokemon.nombre)
                startActivity(intent)
            },
            onEliminar = { pokemon ->
                miFuncionEliminar(pokemon)
            }
        )
        recycler.adapter = adapter

        // Cada vez que escribo en el buscador filtro la lista de favoritos
        etBuscar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filtrarFavoritos(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        cargarFavoritos()
    }

    // Cargo todos los favoritos del usuario desde Firebase
    private fun cargarFavoritos() {
        val uid = auth.currentUser?.uid ?: return
        val tvSinFavoritos = findViewById<TextView>(R.id.tvSinFavoritos)

        db.collection("usuarios").document(uid)
            .collection("favoritos")
            .get()
            .addOnSuccessListener { docs ->
                listaFavoritos.clear()
                for (doc in docs) {
                    val fav = doc.toObject(PokemonFavorito::class.java)
                    listaFavoritos.add(fav)
                }
                // Después de cargar aplico el filtro por si había texto escrito
                filtrarFavoritos(etBuscar.text.toString())

                // Muestro el aviso si no hay ningún favorito guardado
                tvSinFavoritos.visibility =
                    if (listaFavoritos.isEmpty()) View.VISIBLE else View.GONE
            }
    }

    // Filtro la lista según el texto escrito en el buscador
    private fun filtrarFavoritos(texto: String) {
        listaFiltrada.clear()
        if (texto.isEmpty()) {
            // Si no hay texto muestro todos los favoritos
            listaFiltrada.addAll(listaFavoritos)
        } else {
            // Si hay texto solo muestro los que coincidan con el nombre
            listaFiltrada.addAll(listaFavoritos.filter {
                it.nombre.contains(texto.lowercase())
            })
        }
        adapter.notifyDataSetChanged()
    }

    // Elimino el favorito visualmente primero y luego en Firebase
    private fun miFuncionEliminar(pokemon: PokemonFavorito) {
        val uid = auth.currentUser?.uid ?: return

        // Lo quito de ambas listas para que desaparezca de pantalla al instante
        val posicionFiltrada = listaFiltrada.indexOf(pokemon)
        if (posicionFiltrada != -1) {
            listaFiltrada.removeAt(posicionFiltrada)
            adapter.notifyItemRemoved(posicionFiltrada)
        }
        listaFavoritos.remove(pokemon)

        // Luego lo borro de Firebase
        db.collection("usuarios").document(uid)
            .collection("favoritos").document(pokemon.nombre)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(
                    applicationContext,
                    "❌ ${pokemon.nombre.uppercase()} eliminado",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }
}

// Adaptador que dibuja cada tarjeta de favorito en la lista
class FavoritosAdapter(
    private val lista: List<PokemonFavorito>,
    private val onItemClick: (PokemonFavorito) -> Unit,
    private val onEliminar: (PokemonFavorito) -> Unit
) : RecyclerView.Adapter<FavoritosAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivPokemon: ImageView = itemView.findViewById(R.id.ivPokemonFavorito)
        val tvNombre: TextView = itemView.findViewById(R.id.tvNombreFavorito)
        val tvNumero: TextView = itemView.findViewById(R.id.tvNumeroFavorito)
        val btnEliminar: ImageButton = itemView.findViewById(R.id.btnEliminarFavorito)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_favorito, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val pokemon = lista[position]

        // Muestro el nombre con la primera letra en mayúscula y el número
        holder.tvNombre.text = pokemon.nombre.replaceFirstChar { it.uppercase() }
        holder.tvNumero.text = pokemon.numero

        // Cargo la imagen del pokémon con Glide
        Glide.with(holder.itemView.context)
            .load(pokemon.urlImagen)
            .placeholder(android.R.drawable.ic_menu_gallery)
            .into(holder.ivPokemon)

        // Al pulsar la tarjeta abro el detalle, al pulsar la papelera lo elimino
        holder.itemView.setOnClickListener { onItemClick(pokemon) }
        holder.btnEliminar.setOnClickListener { onEliminar(pokemon) }
    }

    override fun getItemCount() = lista.size
}