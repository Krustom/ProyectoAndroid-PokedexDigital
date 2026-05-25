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
import com.google.firebase.firestore.FirebaseFirestore  // Necesito Firestore para leer el rol

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private var passwordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()

        // Si ya tengo sesión iniciada, compruebo el rol y redirijo directamente
        if (auth.currentUser != null) {
            navegarSegunRol(auth.currentUser!!.uid)
            return
        }

        val etEmail           = findViewById<EditText>(R.id.etEmail)
        val etPassword        = findViewById<EditText>(R.id.etPassword)
        val btnLogin          = findViewById<Button>(R.id.btnLogin)
        val tvIrRegistro      = findViewById<TextView>(R.id.tvIrRegistro)
        val btnMostrarPassword = findViewById<TextView>(R.id.btnMostrarPassword)
        val tvOlvidePassword  = findViewById<TextView>(R.id.tvOlvidePassword)

        // Alterna entre mostrar y ocultar la contraseña
        btnMostrarPassword.setOnClickListener {
            passwordVisible = !passwordVisible
            etPassword.inputType = if (passwordVisible)
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            else
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            etPassword.setSelection(etPassword.text.length)
        }

        // Envío un email de recuperación si el usuario olvidó su contraseña
        tvOlvidePassword.setOnClickListener {
            val email = etEmail.text.toString().trim()
            if (email.isEmpty()) {
                Toast.makeText(this, "Escribe tu email primero", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            auth.sendPasswordResetEmail(email)
                .addOnSuccessListener {
                    Toast.makeText(this, "Email de recuperación enviado", Toast.LENGTH_LONG).show()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Error: ${it.message}", Toast.LENGTH_LONG).show()
                }
        }

        // Al pulsar "Entrar" inicio sesión y leo el rol para saber a dónde navegar
        btnLogin.setOnClickListener {
            val email    = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Rellena todos los campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener { resultado ->
                    val uid = resultado.user?.uid ?: return@addOnSuccessListener
                    Toast.makeText(this, "¡Bienvenido!", Toast.LENGTH_SHORT).show()
                    // Una vez autenticado, consulto el rol en Firestore antes de navegar
                    navegarSegunRol(uid)
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Email o contraseña incorrectos", Toast.LENGTH_SHORT).show()
                }
        }

        // Al pulsar "Regístrate" abro la pantalla de registro
        tvIrRegistro.setOnClickListener {
            startActivity(Intent(this, RegistroActivity::class.java))
        }
    }

    /**
     * Leo el campo "rol" del documento del usuario en Firestore.
     * Si es "admin" lo llevo a AdminActivity; si no, al MainActivity normal.
     * En caso de error de red navego al MainActivity para no bloquear al usuario.
     */
    private fun navegarSegunRol(uid: String) {
        val db = FirebaseFirestore.getInstance()

        db.collection("usuarios").document(uid)
            .get()
            .addOnSuccessListener { documento ->
                val rol = documento.getString("rol") ?: "usuario"

                if (rol == "admin") {
                    // Es administrador: lo llevo al panel de administración
                    startActivity(Intent(this, AdminActivity::class.java))
                } else {
                    // Es un usuario normal: acceso a la app principal
                    startActivity(Intent(this, MainActivity::class.java))
                }
                finish()
            }
            .addOnFailureListener {
                // Si no puedo leer el rol (sin conexión, etc.) navego al MainActivity
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
    }
}