package com.finlog.ui.reports

import android.app.DatePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import androidx.navigation.fragment.findNavController
import com.finlog.FinLogApp
import com.finlog.R
import com.finlog.data.local.CategoryTotal
import com.finlog.data.model.Budget
import com.finlog.data.model.Transaction
import com.finlog.data.repository.Repository
import com.finlog.databinding.FragmentReportsBinding
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class ReportsViewModel(private val repo: Repository) : ViewModel() {

    data class MonthData(val label: String, val income: Float, val expense: Float)

    private val _period = MutableLiveData<Pair<Long, Long>>(
        Calendar.getInstance().apply { 
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0) 
        }.timeInMillis to System.currentTimeMillis()
    )

    private val MON = arrayOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")

    // Reactive: Observe transactions to update 6-month chart automatically
    val sixMonths: LiveData<List<MonthData>> = repo.getTransactions().asLiveData().switchMap {
        liveData {
            val months = (5 downTo 0).map { off ->
                val cal = Calendar.getInstance().apply { add(Calendar.MONTH, -off) }
                val m = cal.get(Calendar.MONTH) + 1; val y = cal.get(Calendar.YEAR)
                MonthData(MON[m-1],
                    repo.monthlyTotal(Transaction.TYPE_INCOME, m, y).toFloat(),
                    repo.monthlyTotal(Transaction.TYPE_EXPENSE, m, y).toFloat())
            }
            emit(months)
        }
    }

    // Reactive: Updates when transactions OR period changes
    val categoryTotals: LiveData<List<CategoryTotal>> = repo.getTransactions().asLiveData().switchMap {
        _period.switchMap { period ->
            liveData {
                emit(repo.getCategoryTotals(period.first, period.second))
            }
        }
    }

    // Reactive: Budgets for the current month
    val currentBudgets: LiveData<List<Budget>> = repo.getTransactions().asLiveData().switchMap {
        val cal = Calendar.getInstance()
        repo.getBudgets(cal.get(Calendar.MONTH) + 1, cal.get(Calendar.YEAR)).asLiveData()
    }

    var fromMs: Long
        get() = _period.value?.first ?: 0L
        set(value) { _period.value = value to (_period.value?.second ?: System.currentTimeMillis()) }

    var toMs: Long
        get() = _period.value?.second ?: 0L
        set(value) { _period.value = (_period.value?.first ?: 0L) to value }

    fun reloadForPeriod(from: Long, to: Long) {
        _period.value = from to to
    }
}

class ReportsVMFactory(private val repo: Repository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = ReportsViewModel(repo) as T
}

class ReportsFragment : Fragment() {
    private var _b: FragmentReportsBinding? = null
    private val b get() = _b!!
    private val vm: ReportsViewModel by viewModels { ReportsVMFactory((requireActivity().application as FinLogApp).repo) }
    private val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentReportsBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, s: Bundle?) {
        super.onViewCreated(view, s)
        setupCharts()
        setupDatePicker()
        observeData()

        b.btnCalendar.setOnClickListener {
            findNavController().navigate(R.id.action_reports_to_calendar)
        }
    }

    private fun setupDatePicker() {
        updateDateLabel()
        b.btnPickPeriod.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(requireContext(), { _, y, m, d ->
                val from = Calendar.getInstance().apply { set(y, m, d, 0, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
                DatePickerDialog(requireContext(), { _, y2, m2, d2 ->
                    val to = Calendar.getInstance().apply { set(y2, m2, d2, 23, 59, 59); set(Calendar.MILLISECOND, 999) }.timeInMillis
                    if (to < from) { Toast.makeText(requireContext(), "End date must be after start date", Toast.LENGTH_SHORT).show(); return@DatePickerDialog }
                    vm.reloadForPeriod(from, to)
                    updateDateLabel()
                }, y, m, d).show()
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }
    }

    private fun updateDateLabel() {
        b.tvPeriodLabel.text = "${dateFmt.format(Date(vm.fromMs))} – ${dateFmt.format(Date(vm.toMs))}"
    }

    private fun setupCharts() {
        val dark = Color.parseColor("#1C1F27")
        b.barChart.apply {
            description.isEnabled = false; setBackgroundColor(dark)
            legend.textColor = Color.WHITE; xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.textColor = Color.WHITE; xAxis.setDrawGridLines(false)
            axisLeft.textColor = Color.WHITE; axisRight.isEnabled = false; animateY(600)
        }
        b.categoryChart.apply {
            description.isEnabled = false; setBackgroundColor(dark)
            legend.textColor = Color.WHITE; xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.textColor = Color.WHITE; xAxis.setDrawGridLines(false)
            xAxis.labelRotationAngle = -30f; xAxis.labelCount = 6
            axisLeft.textColor = Color.WHITE; axisRight.isEnabled = false; animateY(600)
        }
        b.pieChart.apply {
            description.isEnabled = false; isDrawHoleEnabled = true; holeRadius = 50f
            setHoleColor(dark); setBackgroundColor(dark)
            legend.textColor = Color.WHITE; setCenterTextColor(Color.WHITE)
            setCenterTextSize(11f); animateY(600)
        }
    }

    private fun observeData() {
        // 6-month overview bar chart
        vm.sixMonths.observe(viewLifecycleOwner) { data ->
            val iSet = BarDataSet(data.mapIndexed { i, d -> BarEntry(i.toFloat(), d.income) }, "Income").apply {
                color = Color.parseColor("#22C55E"); valueTextColor = Color.WHITE
            }
            val eSet = BarDataSet(data.mapIndexed { i, d -> BarEntry(i.toFloat(), d.expense) }, "Expenses").apply {
                color = Color.parseColor("#EF4444"); valueTextColor = Color.WHITE
            }
            b.barChart.data = BarData(iSet, eSet).apply { barWidth = 0.35f }
            b.barChart.xAxis.valueFormatter = IndexAxisValueFormatter(data.map { it.label })
            b.barChart.groupBars(0f, 0.1f, 0.05f); b.barChart.invalidate()
        }

        // Category spending chart (real data + budget goal lines)
        vm.categoryTotals.observe(viewLifecycleOwner) { totals ->
            if (totals.isEmpty()) { b.tvNoCategoryData.visibility = View.VISIBLE; b.categoryChart.visibility = View.GONE; return@observe }
            b.tvNoCategoryData.visibility = View.GONE; b.categoryChart.visibility = View.VISIBLE

            val chartColors = listOf("#EF4444","#F59E0B","#8B5CF6","#EC4899","#06B6D4","#F97316","#6366F1","#84CC16","#22C55E","#10B981","#3B82F6","#9CA3AF")
                .map { Color.parseColor(it) }

            val entries = totals.mapIndexed { i, ct -> BarEntry(i.toFloat(), ct.total.toFloat()) }
            val ds = BarDataSet(entries, "Spending").apply {
                colors = chartColors.take(totals.size); valueTextColor = Color.WHITE; valueTextSize = 9f
            }
            b.categoryChart.data = BarData(ds)
            b.categoryChart.xAxis.valueFormatter = IndexAxisValueFormatter(totals.map { it.categoryName })
            b.categoryChart.xAxis.labelCount = totals.size

            // Add min/max limit lines from budgets
            b.categoryChart.axisLeft.removeAllLimitLines()
            vm.currentBudgets.value?.forEach { budget ->
                if (budget.limitAmount > 0) {
                    val maxLine = LimitLine(budget.limitAmount.toFloat(), "Max: ${budget.categoryName}").apply {
                        lineColor = Color.parseColor("#EF4444"); lineWidth = 1.5f; textColor = Color.parseColor("#EF4444")
                        enableDashedLine(10f, 5f, 0f)
                    }
                    b.categoryChart.axisLeft.addLimitLine(maxLine)
                }
                if (budget.minGoal > 0) {
                    val minLine = LimitLine(budget.minGoal.toFloat(), "Min: ${budget.categoryName}").apply {
                        lineColor = Color.parseColor("#22C55E"); lineWidth = 1.5f; textColor = Color.parseColor("#22C55E")
                        enableDashedLine(10f, 5f, 0f)
                    }
                    b.categoryChart.axisLeft.addLimitLine(minLine)
                }
            }
            b.categoryChart.invalidate()

            // Real pie chart from same data
            val pieColors = chartColors.take(totals.size)
            val pieds = PieDataSet(totals.map { PieEntry(it.total.toFloat(), it.categoryName) }, "").apply {
                colors = pieColors; valueTextColor = Color.WHITE; valueTextSize = 11f; sliceSpace = 2f
            }
            b.pieChart.data = PieData(pieds)
            b.pieChart.setCenterText("Spending\nBreakdown")
            b.pieChart.invalidate()
        }

        vm.currentBudgets.observe(viewLifecycleOwner) { /* Reactive updates via switchMap */ }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
