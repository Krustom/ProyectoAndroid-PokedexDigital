package com.abel.pokedexdigital.activity

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.abel.pokedexdigital.MainActivity
import com.abel.pokedexdigital.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class RegistroActivity : AppCompatActivity() {

    // Instancia de Firebase Auth para gestionar la autenticación
    private lateinit var auth: FirebaseAuth

    // Controlo si la contraseña y la confirmación son visibles o no
    private var passVisible = false
    private var confirmarVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        // Inicializo Firebase Auth
        auth = FirebaseAuth.getInstance()

        // Obtengo referencias a todos los elementos del layout
        val etEmail              = findViewById<EditText>(R.id.etEmail)
        val etPassword           = findViewById<EditText>(R.id.etPassword)
        val etConfirmarPassword  = findViewById<EditText>(R.id.etConfirmarPassword)
        val btnRegistro          = findViewById<Button>(R.id.btnRegistro)
        val tvIrLogin            = findViewById<TextView>(R.id.tvIrLogin)
        val btnVolverLogin       = findViewById<TextView>(R.id.btnVolverLogin)
        val btnMostrarPassword   = findViewById<TextView>(R.id.btnMostrarPassword)
        val btnMostrarConfirmar  = findViewById<TextView>(R.id.btnMostrarConfirmar)

        // Al pulsar "Volver" o "Ir al login" cierro esta pantalla y vuelvo a la anterior
        btnVolverLogin.setOnClickListener { finish() }
        tvIrLogin.setOnClickListener { finish() }

        // Alterna entre mostrar y ocultar la contraseña principal
        btnMostrarPassword.setOnClickListener {
            passVisible = !passVisible
            etPassword.inputType = if (passVisible)
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            else
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            // Muevo el cursor al final para que no salte al principio
            etPassword.setSelection(etPassword.text.length)
        }

        // Alterna entre mostrar y ocultar el campo de confirmación de contraseña
        btnMostrarConfirmar.setOnClickListener {
            confirmarVisible = !confirmarVisible
            etConfirmarPassword.inputType = if (confirmarVisible)
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            else
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            etConfirmarPassword.setSelection(etConfirmarPassword.text.length)
        }

        // Cuando pulso el botón de registro valido los campos y creo la cuenta
        btnRegistro.setOnClickListener {
            val email     = etEmail.text.toString().trim()
            val password  = etPassword.text.toString().trim()
            val confirmar = etConfirmarPassword.text.toString().trim()

            // Compruebo que ningún campo esté vacío
            if (email.isEmpty() || password.isEmpty() || confirmar.isEmpty()) {
                Toast.makeText(this, "Rellena todos los campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Compruebo que las dos contraseñas coinciden
            if (password != confirmar) {
                Toast.makeText(this, "Las contraseñas no coinciden", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Exijo un mínimo de 6 caracteres por seguridad
            if (password.length < 6) {
                Toast.makeText(this, "La contraseña debe tener al menos 6 caracteres", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Creo la cuenta en Firebase Authentication
            auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener { resultado ->

                    // Obtengo el UID único que Firebase asigna al nuevo usuario
                    val uid = resultado.user?.uid ?: return@addOnSuccessListener

                    // Creo el documento del usuario en Firestore con su email y rol por defecto
                    // El rol "usuario" es el estándar; solo el administrador puede cambiarlo a "admin"
                    // desde la consola de Firebase directamente
                    val db = FirebaseFirestore.getInstance()
                    val datosUsuario = hashMapOf(
                        "email" to email,
                        "rol"   to "usuario"
                    )

                    db.collection("usuarios").document(uid)
                        .set(datosUsuario)
                        .addOnSuccessListener {
                            // Todo ha ido bien: informo al usuario y navego al MainActivity
                            Toast.makeText(this, "¡Cuenta creada con éxito!", Toast.LENGTH_SHORT).show()
                            startActivity(Intent(this, MainActivity::class.java))
                            finish()
                        }
                        .addOnFailureListener {
                            // La cuenta de Auth se creó, pero falló guardar el documento en Firestore
                            // Aun así dejo al usuario entrar a la app; el documento se puede crear después
                            Toast.makeText(this, "¡Cuenta creada con éxito!", Toast.LENGTH_SHORT).show()
                            startActivity(Intent(this, MainActivity::class.java))
                            finish()
                        }
                }
                .addOnFailureListener {
                    // El registro en Firebase Auth ha fallado (email ya en uso, sin conexión, etc.)
                    Toast.makeText(this, "Error: ${it.message}", Toast.LENGTH_LONG).show()
                }
        }
    }
}