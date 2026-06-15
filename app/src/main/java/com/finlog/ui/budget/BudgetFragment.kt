package com.finlog.ui.budget

import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
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
import com.finlog.data.model.Budget
import com.finlog.data.model.DEFAULT_CATEGORIES
import com.finlog.data.repository.Repository
import com.finlog.databinding.FragmentBudgetBinding
import com.finlog.databinding.ItemBudgetBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.*

import com.finlog.ui.gamification.GamificationManager

class BudgetViewModel(private val repo: Repository) : ViewModel() {
    private val _month = MutableLiveData(Calendar.getInstance().get(Calendar.MONTH) + 1)
    private val _year  = MutableLiveData(Calendar.getInstance().get(Calendar.YEAR))
    val month: LiveData<Int> = _month; val year: LiveData<Int> = _year
    val budgets: LiveData<List<Budget>> = _month.switchMap { m -> _year.switchMap { y -> repo.getBudgets(m, y).asLiveData() } }
    
    // Reactive: Health score is automatically derived from the budget list
    val healthScore: LiveData<Int> = budgets.map { list ->
        if (list.isEmpty()) 100
        else (100 - list.count { it.isOverBudget } * 20 - list.count { it.isNearLimit && !it.isOverBudget } * 8).coerceIn(0, 100)
    }

    fun navigate(d: Int) {
        val cal = Calendar.getInstance().apply { 
            set(Calendar.MONTH, (_month.value ?: 1) - 1)
            set(Calendar.YEAR, _year.value ?: 2026)
            add(Calendar.MONTH, d) 
        }
        _month.value = cal.get(Calendar.MONTH) + 1
        _year.value = cal.get(Calendar.YEAR)
    }
    fun save(b: Budget)   = viewModelScope.launch { repo.saveBudget(b) }
    fun delete(b: Budget) = viewModelScope.launch { repo.deleteBudget(b) }
}
class BudgetVMFactory(private val repo: Repository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(c: Class<T>): T = BudgetViewModel(repo) as T
}

class BudgetFragment : Fragment() {
    private var _b: FragmentBudgetBinding? = null
    private val b get() = _b!!
    private val vm: BudgetViewModel by viewModels { BudgetVMFactory((requireActivity().application as FinLogApp).repo) }
    private val MON = arrayOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View { _b = FragmentBudgetBinding.inflate(i, c, false); return b.root }

    override fun onViewCreated(view: View, s: Bundle?) {
        super.onViewCreated(view, s)
        val adapter = BudgetAdapter(
            onEdit   = { budget -> showDialog(budget) },
            onDelete = { budget -> MaterialAlertDialogBuilder(requireContext()).setTitle("Delete Budget")
                .setMessage("Delete ${budget.categoryName} budget?")
                .setPositiveButton("Delete") { _, _ -> vm.delete(budget) }.setNegativeButton("Cancel", null).show() }
        )
        b.rvBudgets.layoutManager = LinearLayoutManager(requireContext()); b.rvBudgets.adapter = adapter
        b.btnPrev.setOnClickListener { vm.navigate(-1) }; b.btnNext.setOnClickListener { vm.navigate(1) }
        b.fabAdd.setOnClickListener { showDialog(null) }
        vm.month.observe(viewLifecycleOwner)  { m -> b.tvMonth.text = "${MON[m - 1]} ${vm.year.value}" }
        vm.budgets.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list); b.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            list.filter { it.isNearLimit || it.isOverBudget }.forEach { sendAlert(it) }

            // Gamification
            val allGood = list.isNotEmpty() && list.all { !it.isOverBudget }
            if (allGood) {
                GamificationManager.checkBudgetMaster(requireContext(), true)
                GamificationManager.addPoints(requireContext(), 50)
            }
        }
        vm.healthScore.observe(viewLifecycleOwner) { score -> b.tvScore.text = score.toString(); b.progressScore.progress = score }
    }

    private fun sendAlert(budget: Budget) {
        val mgr = requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.notify(budget.id.hashCode(), NotificationCompat.Builder(requireContext(), FinLogApp.CH_BUDGET)
            .setSmallIcon(R.drawable.ic_notification).setAutoCancel(true)
            .setContentTitle("Budget Alert")
            .setContentText(if (budget.isOverBudget) "⚠️ ${budget.categoryName} exceeded!" else "📊 ${budget.categoryName} at ${budget.percentage.toInt()}%")
            .build())
    }

    private fun showDialog(existing: Budget?) {
        val dv = layoutInflater.inflate(R.layout.dialog_budget, null)
        val etAmt = dv.findViewById<TextInputEditText>(R.id.etAmount)
        val etMin = dv.findViewById<TextInputEditText>(R.id.etMinGoal)
        val spinner = dv.findViewById<android.widget.Spinner>(R.id.spinnerCategory)
        spinner.adapter = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, DEFAULT_CATEGORIES.map { "${it.emoji} ${it.name}" })
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        existing?.let {
            etAmt.setText(it.limitAmount.toString())
            etMin.setText(it.minGoal.toString())
        }
        MaterialAlertDialogBuilder(requireContext()).setTitle(if (existing == null) "Set Budget" else "Edit Budget").setView(dv)
            .setPositiveButton("Save") { _, _ ->
                val amt = etAmt.text.toString().toDoubleOrNull() ?: 0.0
                val min = etMin.text.toString().toDoubleOrNull() ?: 0.0
                if (amt <= 0 && min <= 0) { Toast.makeText(requireContext(), "Enter at least one goal", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                val cat = DEFAULT_CATEGORIES[spinner.selectedItemPosition]
                vm.save(existing?.copy(limitAmount = amt, minGoal = min) ?: Budget(
                    categoryId=cat.id,
                    categoryName=cat.name,
                    categoryEmoji=cat.emoji,
                    limitAmount=amt,
                    minGoal=min,
                    month=vm.month.value ?: 1,
                    year=vm.year.value ?: 2026
                ))
            }.setNegativeButton("Cancel", null).show()
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}

class BudgetAdapter(private val onEdit: (Budget)->Unit, private val onDelete: (Budget)->Unit) : ListAdapter<Budget, BudgetAdapter.VH>(DIFF) {
    private val fmt = NumberFormat.getCurrencyInstance(Locale("en","ZA"))
    inner class VH(val b: ItemBudgetBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(budget: Budget) {
            b.tvName.text    = budget.categoryName
            b.tvAmounts.text = "${fmt.format(budget.spentAmount)} / ${fmt.format(budget.limitAmount)}"
            b.tvPct.text     = "${budget.percentage.toInt()}%"
            b.progress.progress = budget.percentage.toInt().coerceIn(0,100)
            val color = when { budget.percentage >= 100 -> Color.parseColor("#EF4444"); budget.percentage >= 75 -> Color.parseColor("#F59E0B"); else -> Color.parseColor("#22C55E") }
            b.progress.progressTintList = android.content.res.ColorStateList.valueOf(color)
            b.tvPct.setTextColor(color)
            b.btnDelete.setOnClickListener { onDelete(budget) }
            b.root.setOnLongClickListener  { onEdit(budget); true }
        }
    }
    override fun onCreateViewHolder(p: ViewGroup, t: Int) = VH(ItemBudgetBinding.inflate(LayoutInflater.from(p.context), p, false))
    override fun onBindViewHolder(h: VH, pos: Int) = h.bind(getItem(pos))
    companion object DIFF : DiffUtil.ItemCallback<Budget>() {
        override fun areItemsTheSame(a: Budget, b: Budget) = a.id == b.id
        override fun areContentsTheSame(a: Budget, b: Budget) = a == b
    }
}
