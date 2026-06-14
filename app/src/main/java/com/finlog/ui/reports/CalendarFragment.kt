package com.finlog.ui.reports

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.finlog.FinLogApp
import com.finlog.data.model.Transaction
import com.finlog.data.repository.Repository
import com.finlog.databinding.FragmentCalendarBinding
import com.finlog.databinding.ItemCalendarDayBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class CalendarViewModel(private val repo: Repository) : ViewModel() {
    private val _month = MutableLiveData(Calendar.getInstance().get(Calendar.MONTH))
    private val _year = MutableLiveData(Calendar.getInstance().get(Calendar.YEAR))
    val month: LiveData<Int> = _month
    val year: LiveData<Int> = _year

    val dailySpending = MutableLiveData<Map<Int, Double>>()

    fun navigate(d: Int) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.MONTH, _month.value ?: 0)
            set(Calendar.YEAR, _year.value ?: 2026)
            add(Calendar.MONTH, d)
        }
        _month.value = cal.get(Calendar.MONTH)
        _year.value = cal.get(Calendar.YEAR)
        loadData()
    }

    fun loadData() = viewModelScope.launch {
        val cal = Calendar.getInstance().apply {
            set(Calendar.MONTH, _month.value ?: 0)
            set(Calendar.YEAR, _year.value ?: 2026)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val from = cal.timeInMillis
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        val to = cal.timeInMillis

        val txs = repo.getByDateRange(from, to).first()
        val map = txs.filter { it.type == Transaction.TYPE_EXPENSE }
            .groupBy {
                val c = Calendar.getInstance().apply { timeInMillis = it.date }
                c.get(Calendar.DAY_OF_MONTH)
            }.mapValues { it.value.sumOf { t -> t.amount } }
        dailySpending.value = map
    }
}

class CalendarFragment : Fragment() {
    private var _b: FragmentCalendarBinding? = null
    private val b get() = _b!!
    private val vm: CalendarViewModel by viewModels { ReportsVMFactory((requireActivity().application as FinLogApp).repo) }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentCalendarBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, s: Bundle?) {
        super.onViewCreated(view, s)
        b.rvCalendar.layoutManager = GridLayoutManager(requireContext(), 7)

        vm.month.observe(viewLifecycleOwner) { updateTitle() }
        vm.year.observe(viewLifecycleOwner) { updateTitle() }
        vm.dailySpending.observe(viewLifecycleOwner) { render() }

        b.btnPrev.setOnClickListener { vm.navigate(-1) }
        b.btnNext.setOnClickListener { vm.navigate(1) }

        vm.loadData()
    }

    private fun updateTitle() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.MONTH, vm.month.value ?: 0)
            set(Calendar.YEAR, vm.year.value ?: 2026)
        }
        b.tvMonth.text = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(cal.time)
    }

    private fun render() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.MONTH, vm.month.value ?: 0)
            set(Calendar.YEAR, vm.year.value ?: 2026)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val firstDay = cal.get(Calendar.DAY_OF_WEEK) - 1
        val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val items = mutableListOf<CalendarDay>()

        for (i in 0 until firstDay) items.add(CalendarDay(0, 0.0))
        for (i in 1..maxDays) items.add(CalendarDay(i, vm.dailySpending.value?.get(i) ?: 0.0))

        b.rvCalendar.adapter = CalendarAdapter(items)
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}

data class CalendarDay(val day: Int, val amount: Double)

class CalendarAdapter(private val list: List<CalendarDay>) : RecyclerView.Adapter<CalendarAdapter.VH>() {
    inner class VH(val b: ItemCalendarDayBinding) : RecyclerView.ViewHolder(b.root)
    override fun onCreateViewHolder(p: ViewGroup, t: Int) = VH(ItemCalendarDayBinding.inflate(LayoutInflater.from(p.context), p, false))
    override fun onBindViewHolder(h: VH, pos: Int) {
        val d = list[pos]
        if (d.day == 0) {
            h.b.root.visibility = View.INVISIBLE
            return
        }
        h.b.root.visibility = View.VISIBLE
        h.b.tvDay.text = d.day.toString()
        h.b.tvAmount.text = if (d.amount > 0) "R ${d.amount.toInt()}" else ""

        val color = when {
            d.amount == 0.0 -> "#1C1F27"
            d.amount < 100 -> "#22C55E"
            d.amount < 300 -> "#F59E0B"
            d.amount < 600 -> "#F97316"
            else -> "#EF4444"
        }
        h.b.cardDay.setCardBackgroundColor(Color.parseColor(color))
    }
    override fun getItemCount() = list.size
}
