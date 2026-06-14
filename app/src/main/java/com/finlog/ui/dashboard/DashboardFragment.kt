package com.finlog.ui.dashboard

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.finlog.FinLogApp
import com.finlog.R
import com.finlog.data.model.Budget
import com.finlog.data.model.Transaction
import com.finlog.data.repository.Repository
import com.finlog.databinding.FragmentDashboardBinding
import com.finlog.databinding.ItemBudgetProgressBinding
import com.finlog.ui.transactions.TransactionAdapter
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.*

class DashboardViewModel(private val repo: Repository) : ViewModel() {
    val transactions = repo.getTransactions().asLiveData()
    val totalBalance = repo.getTotalBalance().asLiveData()
    val recentFive: LiveData<List<Transaction>> = transactions.map { it.take(5) }
    val monthlyIncome   = MutableLiveData(0.0)
    val monthlyExpenses = MutableLiveData(0.0)

    val currentMonthBudgets: LiveData<List<Budget>> = liveData {
        val cal = Calendar.getInstance()
        repo.getBudgets(cal.get(Calendar.MONTH) + 1, cal.get(Calendar.YEAR))
            .collect { emit(it) }
    }

    init { loadSummary() }
    private fun loadSummary() = viewModelScope.launch {
        val cal = Calendar.getInstance()
        val m = cal[Calendar.MONTH] + 1; val y = cal[Calendar.YEAR]
        monthlyIncome.value   = repo.monthlyTotal(Transaction.TYPE_INCOME,  m, y)
        monthlyExpenses.value = repo.monthlyTotal(Transaction.TYPE_EXPENSE, m, y)
    }
}

class DashboardVMFactory(private val repo: Repository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = DashboardViewModel(repo) as T
}

class DashboardFragment : Fragment() {
    private var _b: FragmentDashboardBinding? = null
    private val b get() = _b!!
    private val fmt = NumberFormat.getCurrencyInstance(Locale("en", "ZA"))
    private val vm: DashboardViewModel by viewModels {
        DashboardVMFactory((requireActivity().application as FinLogApp).repo)
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentDashboardBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, s: Bundle?) {
        super.onViewCreated(view, s)
        val prefs = requireContext().getSharedPreferences("finlog_prefs", 0)
        val name  = prefs.getString("display_name", "there") ?: "there"
        val hour  = Calendar.getInstance()[Calendar.HOUR_OF_DAY]
        val greeting = when {
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            else -> "Good evening"
        }
        b.tvGreeting.text = getString(R.string.greeting_format, greeting, name)

        // Streak
        val gamificationPrefs = requireContext().getSharedPreferences("finlog_gamification", 0)
        val streak = gamificationPrefs.getInt("log_streak", 0)
        b.tvStreak.text = "🔥 $streak days"

        val adapter = TransactionAdapter { tx ->
            findNavController().navigate(DashboardFragmentDirections.actionDashboardToDetail(tx.id))
        }
        b.rvRecent.layoutManager = LinearLayoutManager(requireContext())
        b.rvRecent.adapter = adapter
        b.fabAdd.setOnClickListener { findNavController().navigate(R.id.action_dashboard_to_addEdit) }

        // Budget Progress
        val budgetAdapter = BudgetProgressAdapter()
        b.rvBudgetProgress.layoutManager = LinearLayoutManager(requireContext())
        b.rvBudgetProgress.adapter = budgetAdapter

        vm.totalBalance.observe(viewLifecycleOwner)    { b.tvBalance.text  = fmt.format(it ?: 0.0) }
        vm.monthlyIncome.observe(viewLifecycleOwner)   { b.tvIncome.text   = fmt.format(it) }
        vm.monthlyExpenses.observe(viewLifecycleOwner) { b.tvExpenses.text = fmt.format(it) }
        vm.recentFive.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            b.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }
        vm.currentMonthBudgets.observe(viewLifecycleOwner) { list ->
            budgetAdapter.submitList(list)
        }

        b.btnInsights.setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_insights)
        }

        b.btnSettings.setOnClickListener {
            findNavController().navigate(R.id.profileFragment)
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}

class BudgetProgressAdapter : ListAdapter<Budget, BudgetProgressAdapter.VH>(DIFF) {
    private val fmt = NumberFormat.getCurrencyInstance(Locale("en", "ZA"))

    inner class VH(val binding: ItemBudgetProgressBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(budget: Budget) {
            binding.tvCategoryName.text = "${budget.categoryEmoji} ${budget.categoryName}"
            binding.tvSpentAmount.text = "Spent: ${fmt.format(budget.spentAmount)}"
            binding.tvLimitAmount.text = "Max: ${fmt.format(budget.limitAmount)}"

            if (budget.minGoal > 0) {
                binding.tvMinGoalHint.visibility = View.VISIBLE
                binding.tvMinGoalHint.text = "Min target: ${fmt.format(budget.minGoal)}"
            } else {
                binding.tvMinGoalHint.visibility = View.GONE
            }

            val progress = if (budget.limitAmount > 0) (budget.spentAmount / budget.limitAmount * 100).toInt().coerceIn(0, 100) else 0
            binding.progressSpending.progress = progress

            val (status, color) = when {
                budget.isOverBudget -> "OVER LIMIT" to "#EF4444"
                budget.isNearLimit -> "NEAR LIMIT" to "#F59E0B"
                budget.isBelowMin -> "BELOW TARGET" to "#F97316"
                else -> "ON TRACK" to "#22C55E"
            }

            binding.tvStatusBadge.text = status
            binding.tvStatusBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor(color))
            binding.progressSpending.progressTintList = ColorStateList.valueOf(Color.parseColor(color))
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        return VH(ItemBudgetProgressBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    companion object DIFF : DiffUtil.ItemCallback<Budget>() {
        override fun areItemsTheSame(oldItem: Budget, newItem: Budget) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Budget, newItem: Budget) = oldItem == newItem
    }
}
