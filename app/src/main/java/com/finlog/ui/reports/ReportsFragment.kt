package com.finlog.ui.reports

import android.graphics.Color
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import com.finlog.FinLogApp
import com.finlog.data.model.Transaction
import com.finlog.data.repository.Repository
import com.finlog.databinding.FragmentReportsBinding
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import kotlinx.coroutines.launch
import java.util.Calendar

class ReportsViewModel(private val repo: Repository) : ViewModel() {
    data class MonthData(val label: String, val income: Float, val expense: Float)
    val sixMonths  = MutableLiveData<List<MonthData>>()
    val pieData    = MutableLiveData<List<Pair<String,Float>>>()
    private val MON = arrayOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
    init { load() }
    private fun load() = viewModelScope.launch {
        val months = (5 downTo 0).map { off ->
            val cal = Calendar.getInstance().apply { add(Calendar.MONTH, -off) }
            val m = cal.get(Calendar.MONTH) + 1; val y = cal.get(Calendar.YEAR)
            MonthData(MON[m-1], repo.monthlyTotal(Transaction.TYPE_INCOME, m, y).toFloat(), repo.monthlyTotal(Transaction.TYPE_EXPENSE, m, y).toFloat())
        }
        sixMonths.value = months
        pieData.value = listOf("Food" to 1200f, "Transport" to 760f, "Entertainment" to 520f, "Health" to 200f, "Other" to 870f)
    }
}
class ReportsVMFactory(private val repo: Repository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(c: Class<T>): T = ReportsViewModel(repo) as T
}

class ReportsFragment : Fragment() {
    private var _b: FragmentReportsBinding? = null
    private val b get() = _b!!
    private val vm: ReportsViewModel by viewModels { ReportsVMFactory((requireActivity().application as FinLogApp).repo) }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View { _b = FragmentReportsBinding.inflate(i, c, false); return b.root }

    override fun onViewCreated(view: View, s: Bundle?) {
        super.onViewCreated(view, s)
        b.barChart.apply {
            description.isEnabled = false; setBackgroundColor(Color.parseColor("#1C1F27"))
            legend.textColor = Color.WHITE; xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.textColor  = Color.WHITE; xAxis.setDrawGridLines(false)
            axisLeft.textColor = Color.WHITE; axisRight.isEnabled = false; animateY(600)
        }
        b.pieChart.apply {
            description.isEnabled = false; isDrawHoleEnabled = true; holeRadius = 50f
            setHoleColor(Color.parseColor("#1C1F27")); setBackgroundColor(Color.parseColor("#1C1F27"))
            legend.textColor = Color.WHITE; setCenterText("Spending DNA")
            setCenterTextColor(Color.WHITE); setCenterTextSize(11f); animateY(600)
        }
        vm.sixMonths.observe(viewLifecycleOwner) { data ->
            val iSet = BarDataSet(data.mapIndexed { i, d -> BarEntry(i.toFloat(), d.income)  }, "Income").apply  { color = Color.parseColor("#22C55E"); valueTextColor = Color.WHITE }
            val eSet = BarDataSet(data.mapIndexed { i, d -> BarEntry(i.toFloat(), d.expense) }, "Expenses").apply { color = Color.parseColor("#EF4444"); valueTextColor = Color.WHITE }
            b.barChart.data = BarData(iSet, eSet).apply { barWidth = 0.35f }
            b.barChart.xAxis.valueFormatter = IndexAxisValueFormatter(data.map { it.label })
            b.barChart.groupBars(0f, 0.1f, 0.05f); b.barChart.invalidate()
        }
        vm.pieData.observe(viewLifecycleOwner) { data ->
            val colors = listOf("#EF4444","#F59E0B","#8B5CF6","#EC4899","#9CA3AF").map { Color.parseColor(it) }
            val ds = PieDataSet(data.map { PieEntry(it.second, it.first) }, "").apply { this.colors = colors; valueTextColor = Color.WHITE; valueTextSize = 11f; sliceSpace = 2f }
            b.pieChart.data = PieData(ds); b.pieChart.invalidate()
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
