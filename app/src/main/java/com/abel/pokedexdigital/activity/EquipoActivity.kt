package com.abel.pokedexdigital.activity

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.abel.pokedexdigital.R
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

// Estructura de datos de cada equipo guardado en Firebase
data class Equipo(
    val id: String = "",
    val nombre: String = "",
    val pokemon: List<String> = emptyList(),
    val ids: List<Long> = emptyList() // guardo los IDs numéricos para cargar los sprites
)

class EquipoActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val listaEquipos = mutableListOf<Equipo>()
    private lateinit var adapter: EquiposAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_equipo)

        // Pongo la barra de estado en rojo
        window.statusBarColor = android.graphics.Color.parseColor("#CC0000")

        // Ajusto el padding para que no tape la barra del sistema
        val barraRoja = findViewById<LinearLayout>(R.id.barraRojaEquipo)
        ViewCompat.setOnApplyWindowInsetsListener(barraRoja) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, systemBars.top, 0, 0)
            insets
        }

        // El botón volver cierra esta pantalla
        findViewById<ImageButton>(R.id.btnVolverEquipo).setOnClickListener { finish() }

        val recycler = findViewById<RecyclerView>(R.id.recyclerEquipos)
        recycler.layoutManager = LinearLayoutManager(this)

        adapter = EquiposAdapter(
            listaEquipos,
            onItemClick = { equipo ->
                // Al pulsar un equipo abro la pantalla de edición
                val intent = Intent(this, EditarEquipoActivity::class.java)
                intent.putExtra("EQUIPO_ID", equipo.id)
                intent.putExtra("EQUIPO_NOMBRE", equipo.nombre)
                startActivity(intent)
            },
            onEliminar = { equipo -> eliminarEquipo(equipo) }
        )
        recycler.adapter = adapter

        // Al pulsar el botón muestro el diálogo para crear un equipo nuevo
        findViewById<Button>(R.id.btnNuevoEquipo).setOnClickListener {
            mostrarDialogoNuevoEquipo()
        }

        // Escucho cambios en tiempo real para que la lista se refresque automáticamente
        cargarEquipos()
    }

    // Muestro un diálogo para que el usuario escriba el nombre del equipo
    private fun mostrarDialogoNuevoEquipo() {
        val input = EditText(this)
        input.hint = "Nombre del equipo"

        AlertDialog.Builder(this)
            .setTitle("Nuevo equipo")
            .setView(input)
            .setPositiveButton("Crear") { _, _ ->
                val nombre = input.text.toString().trim()
                if (nombre.isNotEmpty()) {
                    crearEquipo(nombre)
                } else {
                    Toast.makeText(this, "Escribe un nombre", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // Guardo el nuevo equipo vacío en Firebase con el nombre elegido
    private fun crearEquipo(nombre: String) {
        val uid = auth.currentUser?.uid ?: return
        val ref = db.collection("usuarios").document(uid)
            .collection("equipos").document()

        val equipo = mapOf(
            "nombre" to nombre,
            "pokemon" to emptyList<String>(),
            "ids" to emptyList<Long>()
        )

        ref.set(equipo).addOnSuccessListener {
            Toast.makeText(this, "✅ Equipo \"$nombre\" creado", Toast.LENGTH_SHORT).show()
        }
    }

    // Uso un listener en tiempo real para que la lista se actualice sin delay
    private fun cargarEquipos() {
        val uid = auth.currentUser?.uid ?: return
        val tvSinEquipos = findViewById<TextView>(R.id.tvSinEquipos)

        db.collection("usuarios").document(uid)
            .collection("equipos")
            .addSnapshotListener { docs, error ->
                if (error != null || docs == null) return@addSnapshotListener
                listaEquipos.clear()
                for (doc in docs) {
                    val nombre = doc.getString("nombre") ?: ""
                    val pokemon = doc.get("pokemon") as? List<String> ?: emptyList()
                    val ids = doc.get("ids") as? List<Long> ?: emptyList()
                    listaEquipos.add(Equipo(doc.id, nombre, pokemon, ids))
                }
                adapter.notifyDataSetChanged()
                tvSinEquipos.visibility =
                    if (listaEquipos.isEmpty()) View.VISIBLE else View.GONE
            }
    }

    // Pido confirmación y elimino el equipo de Firebase
    private fun eliminarEquipo(equipo: Equipo) {
        val uid = auth.currentUser?.uid ?: return

        AlertDialog.Builder(this)
            .setTitle("Eliminar equipo")
            .setMessage("¿Seguro que quieres eliminar \"${equipo.nombre}\"?")
            .setPositiveButton("Eliminar") { _, _ ->
                db.collection("usuarios").document(uid)
                    .collection("equipos").document(equipo.id)
                    .delete()
                    .addOnSuccessListener {
                        Toast.makeText(this, "❌ Equipo eliminado", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}

// Adaptador que dibuja cada tarjeta de equipo en la lista
class EquiposAdapter(
    private val lista: List<Equipo>,
    private val onItemClick: (Equipo) -> Unit,
    private val onEliminar: (Equipo) -> Unit
) : RecyclerView.Adapter<EquiposAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvNombre: TextView = itemView.findViewById(R.id.tvNombreEquipo)
        val layoutSprites: LinearLayout = itemView.findViewById(R.id.layoutSpritesEquipo)
        val btnEliminar: ImageButton = itemView.findViewById(R.id.btnEliminarEquipo)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_equipo, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val equipo = lista[position]

        // Muestro el nombre del equipo
        holder.tvNombre.text = equipo.nombre

        // Limpio los sprites anteriores
        holder.layoutSprites.removeAllViews()
        val densidad = holder.itemView.resources.displayMetrics.density
        val tamano = (50 * densidad).toInt()

        // Dibujo los sprites usando el ID numérico para que la URL funcione correctamente
        for (i in 0 until 6) {
            val img = ImageView(holder.itemView.context).apply {
                layoutParams = LinearLayout.LayoutParams(tamano, tamano).apply {
                    setMargins(4, 0, 4, 0)
                }
                scaleType = ImageView.ScaleType.FIT_CENTER
                setBackgroundColor(android.graphics.Color.parseColor("#EEEEEE"))
            }

            if (i < equipo.ids.size) {
                // Uso el ID numérico guardado para construir la URL del sprite
                val id = equipo.ids[i]
                val urlSprite = "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/$id.png"
                Glide.with(holder.itemView.context)
                    .load(urlSprite)
                    .error(android.R.drawable.ic_menu_gallery)
                    .into(img)
            }

            holder.layoutSprites.addView(img)
        }

        // Al pulsar la tarjeta abro la edición, al pulsar la papelera elimino
        holder.itemView.setOnClickListener { onItemClick(equipo) }
        holder.btnEliminar.setOnClickListener { onEliminar(equipo) }
    }

    override fun getItemCount() = lista.size
}