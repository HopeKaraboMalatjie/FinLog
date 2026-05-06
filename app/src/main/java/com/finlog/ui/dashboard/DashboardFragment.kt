package com.finlog.ui.dashboard

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.finlog.FinLogApp
import com.finlog.R
import com.finlog.data.model.Transaction
import com.finlog.data.repository.Repository
import com.finlog.databinding.FragmentDashboardBinding
import com.finlog.ui.transactions.TransactionAdapter
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.*

class DashboardViewModel(private val repo: Repository) : ViewModel() {
    val transactions = repo.getTransactions().asLiveData()
    val totalBalance = repo.getTotalBalance().asLiveData()
    val recentFive: LiveData<List<Transaction>> = transactions.map { it.take(5) }
    val monthlyIncome   = MutableLiveData(0.0)
    val monthlyExpenses = MutableLiveData(0.0)
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

        val adapter = TransactionAdapter { tx ->
            findNavController().navigate(DashboardFragmentDirections.actionDashboardToDetail(tx.id))
        }
        b.rvRecent.layoutManager = LinearLayoutManager(requireContext())
        b.rvRecent.adapter = adapter
        b.fabAdd.setOnClickListener { findNavController().navigate(R.id.action_dashboard_to_addEdit) }

        vm.totalBalance.observe(viewLifecycleOwner)    { b.tvBalance.text  = fmt.format(it ?: 0.0) }
        vm.monthlyIncome.observe(viewLifecycleOwner)   { b.tvIncome.text   = fmt.format(it) }
        vm.monthlyExpenses.observe(viewLifecycleOwner) { b.tvExpenses.text = fmt.format(it) }
        vm.recentFive.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            b.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
