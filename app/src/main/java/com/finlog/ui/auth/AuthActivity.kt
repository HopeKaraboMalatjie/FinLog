package com.finlog.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.finlog.FinLogApp
import com.finlog.R
import com.finlog.databinding.ActivityAuthBinding
import com.finlog.databinding.FragmentLoginBinding
import com.finlog.databinding.FragmentRegisterBinding
import com.finlog.ui.MainActivity
import kotlinx.coroutines.launch

class AuthActivity : AppCompatActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(ActivityAuthBinding.inflate(layoutInflater).root)
    }
}

class LoginFragment : Fragment() {
    private var _b: FragmentLoginBinding? = null
    private val b get() = _b!!

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentLoginBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, s: Bundle?) {
        super.onViewCreated(view, s)
        b.btnLogin.setOnClickListener {
            val email = b.etEmail.text.toString().trim()
            val pass  = b.etPassword.text.toString()
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) { b.tilEmail.error = "Invalid email"; return@setOnClickListener }
            if (pass.length < 8) { b.tilPassword.error = "Min 8 characters"; return@setOnClickListener }
            b.tilEmail.error = null; b.tilPassword.error = null
            val prefs = requireContext().getSharedPreferences("finlog_prefs", 0)
            if (prefs.getString("email", null) == null) { b.tilEmail.error = "No account. Please register."; return@setOnClickListener }
            if (email != prefs.getString("email","") || pass != prefs.getString("password","")) {
                Toast.makeText(requireContext(), "Incorrect email or password", Toast.LENGTH_LONG).show(); return@setOnClickListener
            }
            lifecycleScope.launch {
                (requireActivity().application as FinLogApp).repo.seedDefaultCategories()
                prefs.edit().putBoolean("logged_in", true).apply()
                startActivity(Intent(requireActivity(), MainActivity::class.java))
                requireActivity().finish()
            }
        }
        b.tvRegister.setOnClickListener { findNavController().navigate(R.id.action_login_to_register) }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}

class RegisterFragment : Fragment() {
    private var _b: FragmentRegisterBinding? = null
    private val b get() = _b!!

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentRegisterBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, s: Bundle?) {
        super.onViewCreated(view, s)
        b.btnRegister.setOnClickListener {
            val name = b.etName.text.toString().trim()
            val email = b.etEmail.text.toString().trim()
            val pass = b.etPassword.text.toString()
            val confirm = b.etConfirmPassword.text.toString()
            var ok = true
            if (name.isEmpty()) { b.tilName.error = "Required"; ok = false }
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) { b.tilEmail.error = "Invalid email"; ok = false }
            if (pass.length < 8) { b.tilPassword.error = "Min 8 chars"; ok = false }
            else if (!pass.any { it.isUpperCase() }) { b.tilPassword.error = "Need 1 uppercase"; ok = false }
            else if (!pass.any { it.isDigit() }) { b.tilPassword.error = "Need 1 digit"; ok = false }
            if (pass != confirm) { b.tilConfirmPassword.error = "Passwords do not match"; ok = false }
            if (!ok) return@setOnClickListener
            lifecycleScope.launch {
                requireContext().getSharedPreferences("finlog_prefs", 0).edit()
                    .putString("display_name", name).putString("email", email)
                    .putString("password", pass).putBoolean("logged_in", true).apply()
                (requireActivity().application as FinLogApp).repo.seedDefaultCategories()
                startActivity(Intent(requireActivity(), MainActivity::class.java))
                requireActivity().finish()
            }
        }
        b.tvLogin.setOnClickListener { findNavController().navigateUp() }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
