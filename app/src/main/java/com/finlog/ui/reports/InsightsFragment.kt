package com.finlog.ui.reports

import android.graphics.Color
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.finlog.FinLogApp
import com.finlog.data.local.CategoryTotal
import com.finlog.data.model.Transaction
import com.finlog.data.repository.Repository
import com.finlog.databinding.FragmentInsightsBinding
import com.finlog.databinding.ItemInsightBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.*

data class Insight(val title: String, val description: String, val color: String = "#22C55E")

class InsightsViewModel(private val repo: Repository) : ViewModel() {
    val insights = MutableLiveData<List<Insight>>()
    val comparisonText = MutableLiveData<String>()

    fun loadInsights() = viewModelScope.launch {
        val cal = Calendar.getInstance()
        val toMs = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, -30)
        val fromMs = cal.timeInMillis

        val txs = repo.getByDateRange(fromMs, toMs).first()
        val totals = repo.getCategoryTotals(fromMs, toMs)

        val list = mutableListOf<Insight>()
        val fmt = NumberFormat.getCurrencyInstance(Locale("en", "ZA"))

        if (totals.isNotEmpty()) {
            val top = totals.first()
            list.add(Insight("Top Category", "${top.categoryName} is your biggest expense at ${fmt.format(top.total)}", "#EF4444"))
        }

        val biggest = txs.filter { it.type == Transaction.TYPE_EXPENSE }.maxByOrNull { it.amount }
        if (biggest != null) {
            list.add(Insight("Biggest Single Expense", "${biggest.description.ifEmpty { biggest.categoryName }} cost ${fmt.format(biggest.amount)}", "#F59E0B"))
        }

        val totalSpend = txs.filter { it.type == Transaction.TYPE_EXPENSE }.sumOf { it.amount }
        val avgDaily = totalSpend / 30.0
        list.add(Insight("Daily Average", "You spend an average of ${fmt.format(avgDaily)} per day", "#3B82F6"))

        if (totalSpend > 0) {
            list.add(Insight("Monthly Projection", "At this rate, you'll spend ${fmt.format(avgDaily * 30.5)} this month", "#8B5CF6"))
        }

        insights.value = list

        // Comparison (Conceptual)
        cal.timeInMillis = fromMs
        cal.add(Calendar.DAY_OF_YEAR, -30)
        val prevFrom = cal.timeInMillis
        val prevTo = fromMs
        val prevTxs = repo.getByDateRange(prevFrom, prevTo).first()
        val prevTotal = prevTxs.filter { it.type == Transaction.TYPE_EXPENSE }.sumOf { it.amount }

        val diff = totalSpend - prevTotal
        val pct = if (prevTotal > 0) (diff / prevTotal * 100).toInt() else 0
        comparisonText.value = if (diff >= 0) "vs last month: $pct% more spending 📈" else "vs last month: ${-pct}% less spending 📉"
    }
}

class InsightsVMFactory(private val repo: Repository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(c: Class<T>): T = InsightsViewModel(repo) as T
}

class InsightsFragment : Fragment() {
    private var _b: FragmentInsightsBinding? = null
    private val b get() = _b!!
    private val vm: InsightsViewModel by viewModels { InsightsVMFactory((requireActivity().application as FinLogApp).repo) }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentInsightsBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, s: Bundle?) {
        super.onViewCreated(view, s)
        b.rvInsights.layoutManager = LinearLayoutManager(requireContext())
        val adapter = InsightAdapter()
        b.rvInsights.adapter = adapter

        vm.insights.observe(viewLifecycleOwner) { adapter.list = it; adapter.notifyDataSetChanged() }
        vm.comparisonText.observe(viewLifecycleOwner) { b.tvComparison.text = it }

        vm.loadInsights()
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}

class InsightAdapter : RecyclerView.Adapter<InsightAdapter.VH>() {
    var list = listOf<Insight>()
    inner class VH(val b: ItemInsightBinding) : RecyclerView.ViewHolder(b.root)
    override fun onCreateViewHolder(p: ViewGroup, t: Int) = VH(ItemInsightBinding.inflate(LayoutInflater.from(p.context), p, false))
    override fun onBindViewHolder(h: VH, pos: Int) {
        val item = list[pos]
        h.b.tvInsightTitle.text = item.title
        h.b.tvInsightDesc.text = item.description
        h.b.cardInsight.setCardBackgroundColor(Color.parseColor(item.color))
    }
    override fun getItemCount() = list.size
}
