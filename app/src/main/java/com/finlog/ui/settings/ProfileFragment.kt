package com.finlog.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.finlog.databinding.FragmentProfileBinding
import com.finlog.ui.auth.AuthActivity

class ProfileFragment : Fragment() {
    private var _b: FragmentProfileBinding? = null
    private val b get() = _b!!

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View { _b = FragmentProfileBinding.inflate(i, c, false); return b.root }

    override fun onViewCreated(view: View, s: Bundle?) {
        super.onViewCreated(view, s)
        val prefs = requireContext().getSharedPreferences("finlog_prefs", 0)
        b.tvName.text  = prefs.getString("display_name", "User") ?: "User"
        b.tvEmail.text = prefs.getString("email", "") ?: ""
        b.etMinGoal.setText(prefs.getFloat("min_goal", 0f).toString())
        b.etMaxGoal.setText(prefs.getFloat("max_goal", 0f).toString())
        b.btnSaveGoals.setOnClickListener {
            val min = b.etMinGoal.text.toString().toFloatOrNull()
            val max = b.etMaxGoal.text.toString().toFloatOrNull()
            if (min == null || max == null) { Toast.makeText(requireContext(), "Enter valid amounts", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            if (min > max)                 { Toast.makeText(requireContext(), "Min cannot exceed Max", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            prefs.edit().putFloat("min_goal", min).putFloat("max_goal", max).apply()
            Toast.makeText(requireContext(), "Goals saved!", Toast.LENGTH_SHORT).show()
        }
        b.btnLogout.setOnClickListener {
            prefs.edit().putBoolean("logged_in", false).apply()
            startActivity(Intent(requireActivity(), AuthActivity::class.java))
            requireActivity().finish()
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
