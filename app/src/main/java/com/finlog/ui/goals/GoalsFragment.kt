package com.finlog.ui.goals

import android.app.DatePickerDialog
import android.app.NotificationManager
import android.content.Context
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import androidx.recyclerview.widget.*
import com.finlog.FinLogApp
import com.finlog.R
import com.finlog.data.model.Goal
import com.finlog.data.repository.Repository
import com.finlog.databinding.FragmentGoalsBinding
import com.finlog.databinding.ItemGoalBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class GoalsViewModel(private val repo: Repository) : ViewModel() {
    val goals = repo.getGoals().asLiveData()
    fun save(g: Goal)   = viewModelScope.launch { repo.saveGoal(g) }
    fun delete(g: Goal) = viewModelScope.launch { repo.deleteGoal(g) }
    fun contribute(id: String, amount: Double, prev: Double, target: Double, onMs: (Int) -> Unit) = viewModelScope.launch {
        repo.contributeToGoal(id, amount)
        val newPct = ((prev + amount) / target) * 100
        for (ms in listOf(25,50,75,100)) { if ((prev/target)*100 < ms && newPct >= ms) onMs(ms) }
    }
}
class GoalsVMFactory(private val repo: Repository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = GoalsViewModel(repo) as T
}

class GoalsFragment : Fragment() {
    private var _b: FragmentGoalsBinding? = null
    private val b get() = _b!!
    private val vm: GoalsViewModel by viewModels { GoalsVMFactory((requireActivity().application as FinLogApp).repo) }
    private val fmt = NumberFormat.getCurrencyInstance(Locale("en","ZA"))

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentGoalsBinding.inflate(i, c, false)
        return b.root
    }

    override fun onViewCreated(view: View, s: Bundle?) {
        super.onViewCreated(view, s)
        val adapter = GoalAdapter(
            onContribute = { goal -> showContribute(goal) },
            onDelete = { goal ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Delete Goal")
                    .setMessage("Delete \"${goal.name}\"?")
                    .setPositiveButton("Delete") { _, _ -> vm.delete(goal) }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )
        b.rvGoals.layoutManager = LinearLayoutManager(requireContext())
        b.rvGoals.adapter = adapter
        b.fabAdd.setOnClickListener { showAdd() }
        vm.goals.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            b.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun showContribute(goal: Goal) {
        val dv = layoutInflater.inflate(R.layout.dialog_contribute, null)
        val et = dv.findViewById<TextInputEditText>(R.id.etAmount)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Contribute to ${goal.name}")
            .setMessage("${goal.progress.toInt()}% — ${fmt.format(goal.currentAmount)} of ${fmt.format(goal.targetAmount)}")
            .setView(dv)
            .setPositiveButton("Add") { _, _ ->
                val amt = et.text.toString().toDoubleOrNull()
                if (amt == null || amt <= 0) {
                    Toast.makeText(requireContext(), "Invalid amount", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                vm.contribute(goal.id, amt, goal.currentAmount, goal.targetAmount) { ms -> celebrate(goal.name, ms) }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun celebrate(name: String, ms: Int) {
        val emoji = mapOf(25 to "🌱", 50 to "🎯", 75 to "🏅", 100 to "🏆")[ms] ?: "⭐"
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("$emoji Milestone!")
            .setMessage("$ms% reached for \"$name\"!")
            .setPositiveButton("Awesome! 🎉", null)
            .show()

        val mgr = requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(requireContext(), FinLogApp.CH_GOALS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$emoji Goal Milestone!")
            .setContentText("$ms% reached for \"$name\"!")
            .setAutoCancel(true)
            .build()
        mgr.notify(name.hashCode() + ms, notification)
    }

    private fun showAdd() {
        var targetDate = 0L
        val dv = layoutInflater.inflate(R.layout.dialog_add_goal, null)
        val etName = dv.findViewById<TextInputEditText>(R.id.etName)
        val etAmt  = dv.findViewById<TextInputEditText>(R.id.etAmount)
        val btnDt  = dv.findViewById<android.widget.Button>(R.id.btnPickDate)
        val tvDt   = dv.findViewById<android.widget.TextView>(R.id.tvDate)
        val df     = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        btnDt.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(requireContext(), { _, y, m, d ->
                cal.set(y,m,d)
                targetDate = cal.timeInMillis
                tvDt.text = df.format(cal.time)
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("New Goal")
            .setView(dv)
            .setPositiveButton("Create") { _, _ ->
                val name = etName.text.toString().trim()
                val amt  = etAmt.text.toString().toDoubleOrNull()
                if (name.isEmpty() || amt == null || amt <= 0) {
                    Toast.makeText(requireContext(), "Fill all fields", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                vm.save(Goal(name=name, targetAmount=amt, targetDateMs=targetDate))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}

class GoalAdapter(private val onContribute: (Goal)->Unit, private val onDelete: (Goal)->Unit) : ListAdapter<Goal, GoalAdapter.VH>(DIFF) {
    private val fmt = NumberFormat.getCurrencyInstance(Locale("en","ZA"))
    private val df  = SimpleDateFormat("MMM yyyy", Locale.getDefault())

    inner class VH(val b: ItemGoalBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(g: Goal) {
            b.tvEmoji.text = g.emoji
            b.tvName.text = g.name
            val due = if (g.targetDateMs > 0) " · Due ${df.format(Date(g.targetDateMs))}" else " · Ongoing"
            b.tvMeta.text = "${fmt.format(g.currentAmount)} of ${fmt.format(g.targetAmount)}$due"
            b.progress.progress = g.progress.toInt().coerceIn(0,100)
            b.tvSub.text  = when {
                g.isCompleted -> "✅ Completed!"
                g.monthlyRequired() > 0 -> "${g.progress.toInt()}% — needs ${fmt.format(g.monthlyRequired())}/mo"
                else -> "${g.progress.toInt()}%"
            }
            b.tvBadge.text = when {
                g.isCompleted -> "🏆"
                g.progress >= 75 -> "🏅"
                g.progress >= 50 -> "🎯"
                g.progress >= 25 -> "🌱"
                else -> ""
            }
            b.tvBadge.visibility = if (g.progress >= 25 || g.isCompleted) View.VISIBLE else View.GONE
            b.btnContribute.setOnClickListener { onContribute(g) }
            b.root.setOnLongClickListener { onDelete(g); true }
        }
    }

    override fun onCreateViewHolder(p: ViewGroup, t: Int) = VH(ItemGoalBinding.inflate(LayoutInflater.from(p.context), p, false))
    override fun onBindViewHolder(h: VH, pos: Int) = h.bind(getItem(pos))

    companion object DIFF : DiffUtil.ItemCallback<Goal>() {
        override fun areItemsTheSame(a: Goal, b: Goal) = a.id == b.id
        override fun areContentsTheSame(a: Goal, b: Goal) = a == b
    }
}
