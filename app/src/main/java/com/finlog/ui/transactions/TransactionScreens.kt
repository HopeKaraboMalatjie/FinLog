package com.finlog.ui.transactions

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.net.Uri
import android.view.*
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.*
import com.bumptech.glide.Glide
import com.finlog.FinLogApp
import com.finlog.R
import com.finlog.data.model.*
import com.finlog.data.repository.Repository
import com.finlog.databinding.*
import com.finlog.ui.gamification.GamificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

// ── ViewModel ──────────────────────────────────────────────────────
class TransactionViewModel(private val repo: Repository) : ViewModel() {
    private val _filterType = MutableLiveData<String?>(null)
    private val _filterRange = MutableLiveData<Pair<Long, Long>?>(null)

    // Strictly Reactive: Every UI list is bound directly to the database via Repository Flow/LiveData
    val transactions: LiveData<List<Transaction>> = _filterType.switchMap { type ->
        _filterRange.switchMap { range ->
            when {
                type != null -> repo.getByType(type).asLiveData()
                range != null -> repo.getByDateRange(range.first, range.second).asLiveData()
                else -> repo.getTransactions().asLiveData()
            }
        }
    }

    val categories = repo.getCategories().asLiveData()
    val wallets    = repo.getWallets().asLiveData()

    fun setFilterType(type: String?) { _filterType.value = type }
    fun setFilterRange(from: Long, to: Long) { _filterRange.value = Pair(from, to) }
    fun clearFilters() { _filterType.value = null; _filterRange.value = null }

    fun add(t: Transaction)    = viewModelScope.launch { repo.addTransaction(t) }
    fun update(t: Transaction) = viewModelScope.launch { repo.updateTransaction(t) }
    fun delete(t: Transaction) = viewModelScope.launch { repo.deleteTransaction(t) }
}

class TransactionVMFactory(private val repo: Repository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(c: Class<T>): T = TransactionViewModel(repo) as T
}

// ── List Fragment ──────────────────────────────────────────────────
class TransactionsFragment : Fragment() {
    private var _b: FragmentTransactionsBinding? = null
    private val b get() = _b!!
    private val vm: TransactionViewModel by viewModels { TransactionVMFactory((requireActivity().application as FinLogApp).repo) }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View { _b = FragmentTransactionsBinding.inflate(i, c, false); return b.root }

    override fun onViewCreated(view: View, s: Bundle?) {
        super.onViewCreated(view, s)
        val adapter = TransactionAdapter { tx -> findNavController().navigate(TransactionsFragmentDirections.actionTransactionsToDetail(tx.id)) }
        b.rvTransactions.layoutManager = LinearLayoutManager(requireContext())
        b.rvTransactions.adapter = adapter
        
        b.fabAdd.setOnClickListener { 
            findNavController().navigate(TransactionsFragmentDirections.actionTransactionsToAddEdit())
        }

        // BINDING: Direct reactive link to DB
        vm.transactions.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            b.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }

        b.chipIncome.setOnCheckedChangeListener { _, ch -> vm.setFilterType(if (ch) Transaction.TYPE_INCOME else null) }
        b.chipExpense.setOnCheckedChangeListener { _, ch -> vm.setFilterType(if (ch) Transaction.TYPE_EXPENSE else null) }
        b.btnFilterDate.setOnClickListener { pickDate() }
        b.btnClearFilter.setOnClickListener { b.chipGroupType.clearCheck(); vm.clearFilters() }
    }

    private fun pickDate() {
        val cal = Calendar.getInstance()
        DatePickerDialog(requireContext(), { _, y, m, d ->
            val s = Calendar.getInstance().apply { set(y,m,d,0,0,0) }.timeInMillis
            DatePickerDialog(requireContext(), { _, y2, m2, d2 ->
                val e = Calendar.getInstance().apply { set(y2,m2,d2,23,59,59) }.timeInMillis
                vm.setFilterRange(s, e)
            }, y, m, d).show()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}

// ── Add/Edit Fragment ──────────────────────────────────────────────
class AddEditTransactionFragment : Fragment() {
    private var _b: FragmentAddEditTransactionBinding? = null
    private val b get() = _b!!
    private val vm: TransactionViewModel by viewModels { TransactionVMFactory((requireActivity().application as FinLogApp).repo) }
    private val args: AddEditTransactionFragmentArgs by navArgs()

    private var selectedDate = System.currentTimeMillis()
    private var selectedStart = System.currentTimeMillis()
    private var selectedEnd   = System.currentTimeMillis()
    private var catId = "cat_food"; private var catName = "Food"
    private var walletId = ""; private var txType = Transaction.TYPE_EXPENSE
    private var photoPath = ""; private var cameraUri: Uri? = null
    private val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    private var editingTx: Transaction? = null

    private val pickPhoto = registerForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { setPhoto(it.toString()) } }
    private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) cameraUri?.let { setPhoto(it.toString()) } }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View { _b = FragmentAddEditTransactionBinding.inflate(i, c, false); return b.root }

    override fun onViewCreated(view: View, s: Bundle?) {
        super.onViewCreated(view, s)
        
        if (args.transactionId.isNotEmpty()) {
            lifecycleScope.launch {
                editingTx = (requireActivity().application as FinLogApp).repo.getById(args.transactionId)
                editingTx?.let { tx ->
                    b.etAmount.setText(tx.amount.toString())
                    b.etDescription.setText(tx.description)
                    selectedDate = tx.date
                    selectedStart = tx.startTime
                    selectedEnd = tx.endTime
                    catId = tx.categoryId
                    catName = tx.categoryName
                    walletId = tx.walletId
                    txType = tx.type
                    photoPath = tx.photoPath
                    b.switchRecurring.isChecked = tx.isRecurring
                    if (tx.photoPath.isNotEmpty()) setPhoto(tx.photoPath)
                    
                    when (txType) {
                        Transaction.TYPE_INCOME -> b.btnIncome.isChecked = true
                        Transaction.TYPE_TRANSFER -> b.btnTransfer.isChecked = true
                        else -> b.btnExpense.isChecked = true
                    }
                    updateLabels()
                }
            }
        } else {
            txType = args.defaultType
            when (txType) {
                Transaction.TYPE_INCOME -> b.btnIncome.isChecked = true
                Transaction.TYPE_TRANSFER -> b.btnTransfer.isChecked = true
                else -> b.btnExpense.isChecked = true
            }
            updateLabels()
        }

        b.toggleType.addOnButtonCheckedListener { _, id, ch ->
            if (!ch) return@addOnButtonCheckedListener
            txType = when (id) { 
                R.id.btnIncome -> Transaction.TYPE_INCOME
                R.id.btnTransfer -> Transaction.TYPE_TRANSFER
                else -> Transaction.TYPE_EXPENSE 
            }
        }
        b.tvDate.setOnClickListener { pickDate() }
        b.tvStartTime.setOnClickListener { pickTime(true) }
        b.tvEndTime.setOnClickListener   { pickTime(false) }

        vm.categories.observe(viewLifecycleOwner) { cats ->
            b.chipGroupCats.removeAllViews()
            cats.forEach { cat ->
                val chip = com.google.android.material.chip.Chip(requireContext()).apply {
                    text = "${cat.emoji} ${cat.name}"; isCheckable = true; isChecked = cat.id == catId
                    setOnCheckedChangeListener { _, ch -> if (ch) { catId = cat.id; catName = cat.name } }
                }
                b.chipGroupCats.addView(chip)
            }
        }

        vm.wallets.observe(viewLifecycleOwner) { wallets ->
            if (wallets.isEmpty()) return@observe
            val selectedIdx = if (walletId.isEmpty()) 0 else wallets.indexOfFirst { it.id == walletId }.coerceAtLeast(0)
            walletId = wallets[selectedIdx].id
            b.spinnerWallet.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, wallets.map { "${it.emoji} ${it.name}" })
                .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            b.spinnerWallet.setSelection(selectedIdx)
            b.spinnerWallet.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) { walletId = wallets[pos].id }
                override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
            }
        }

        b.switchRecurring.setOnCheckedChangeListener { _, ch -> b.spinnerRecurring.visibility = if (ch) View.VISIBLE else View.GONE }

        b.btnAddPhoto.setOnClickListener {
            AlertDialog.Builder(requireContext()).setTitle("Add Photo")
                .setItems(arrayOf("Camera", "Gallery")) { _, w -> if (w == 0) launchCamera() else pickPhoto.launch("image/*") }.show()
        }

        b.btnSave.setOnClickListener {
            val amt = b.etAmount.text.toString().toDoubleOrNull()
            if (amt == null || amt <= 0) { b.tilAmount.error = "Enter a valid amount"; return@setOnClickListener }
            b.tilAmount.error = null
            val interval = if (b.switchRecurring.isChecked) listOf("DAILY","WEEKLY","MONTHLY","ANNUALLY")[b.spinnerRecurring.selectedItemPosition] else "NONE"
            
            val tx = editingTx?.copy(
                amount=amt, type=txType, categoryId=catId, categoryName=catName,
                description=b.etDescription.text.toString(), date=selectedDate,
                startTime=selectedStart, endTime=selectedEnd, walletId=walletId,
                photoPath=photoPath, isRecurring=b.switchRecurring.isChecked, recurringInterval=interval
            ) ?: Transaction(amount=amt, type=txType, categoryId=catId, categoryName=catName,
                description=b.etDescription.text.toString(), date=selectedDate,
                startTime=selectedStart, endTime=selectedEnd, walletId=walletId,
                photoPath=photoPath, isRecurring=b.switchRecurring.isChecked, recurringInterval=interval)
            
            if (editingTx != null) vm.update(tx) else vm.add(tx)

            // Gamification
            if (editingTx == null) {
                val gPrefs = requireContext().getSharedPreferences("finlog_gamification", 0)
                val newTotal = gPrefs.getInt("tx_count_total", 0) + 1
                gPrefs.edit().putInt("tx_count_total", newTotal).apply()
                
                GamificationManager.recordLogToday(requireContext())
                GamificationManager.addPoints(requireContext(), 10)
                val unlocked = GamificationManager.checkFirstTransaction(requireContext(), newTotal)
                if (unlocked != null) {
                    GamificationManager.showBadgeUnlocked(this, unlocked)
                }
            }

            Toast.makeText(requireContext(), "Saved!", Toast.LENGTH_SHORT).show()
            findNavController().navigateUp()
        }
    }

    private fun pickDate() {
        val cal = Calendar.getInstance().apply { timeInMillis = selectedDate }
        DatePickerDialog(requireContext(), { _, y, m, d -> cal.set(y,m,d); selectedDate = cal.timeInMillis; updateLabels() },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun pickTime(isStart: Boolean) {
        val cal = Calendar.getInstance().apply { timeInMillis = if (isStart) selectedStart else selectedEnd }
        TimePickerDialog(requireContext(), { _, h, min ->
            cal.set(Calendar.HOUR_OF_DAY, h); cal.set(Calendar.MINUTE, min)
            if (isStart) selectedStart = cal.timeInMillis else selectedEnd = cal.timeInMillis
            updateLabels()
        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
    }

    private fun launchCamera() {
        val f = File.createTempFile("tx_", ".jpg", requireContext().cacheDir)
        cameraUri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", f)
        takePhoto.launch(cameraUri)
    }

    private fun setPhoto(path: String) {
        photoPath = path; b.ivPhoto.visibility = View.VISIBLE
        Glide.with(this).load(path).centerCrop().into(b.ivPhoto)
        b.btnAddPhoto.text = "Change Photo"
    }

    private fun updateLabels() {
        b.tvDate.text      = dateFmt.format(Date(selectedDate))
        b.tvStartTime.text = "Start: ${timeFmt.format(Date(selectedStart))}"
        b.tvEndTime.text   = "End: ${timeFmt.format(Date(selectedEnd))}"
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}

// ── Detail Fragment ────────────────────────────────────────────────
class TransactionDetailFragment : Fragment() {
    private var _b: FragmentTransactionDetailBinding? = null
    private val b get() = _b!!
    private val args: TransactionDetailFragmentArgs by navArgs()
    private val vm: TransactionViewModel by viewModels { TransactionVMFactory((requireActivity().application as FinLogApp).repo) }
    private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    private val fmt = NumberFormat.getCurrencyInstance(Locale("en","ZA"))

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View { _b = FragmentTransactionDetailBinding.inflate(i, c, false); return b.root }

    override fun onViewCreated(view: View, s: Bundle?) {
        super.onViewCreated(view, s)
        lifecycleScope.launch {
            val tx = (requireActivity().application as FinLogApp).repo.getById(args.transactionId) ?: run { findNavController().navigateUp(); return@launch }
            b.tvAmount.text  = "${if (tx.type == Transaction.TYPE_INCOME) "+" else "-"}${fmt.format(tx.amount)}"
            b.tvAmount.setTextColor(requireContext().getColor(if (tx.type == Transaction.TYPE_INCOME) R.color.income_green else R.color.expense_red))
            b.tvCategory.text    = tx.categoryName
            b.tvDescription.text = tx.description.ifEmpty { "No description" }
            b.tvDate.text        = dateFmt.format(Date(tx.date))
            b.tvStartTime.text   = "Start: ${timeFmt.format(Date(tx.startTime))}"
            b.tvEndTime.text     = "End:   ${timeFmt.format(Date(tx.endTime))}"
            b.tvPayment.text     = tx.paymentMethod
            b.tvRecurring.text   = if (tx.isRecurring) "Recurring: ${tx.recurringInterval}" else "One-time"
            
            if (tx.photoPath.isNotEmpty()) {
                b.cardPhoto.visibility = View.VISIBLE
                Glide.with(requireContext())
                    .load(if (tx.photoPath.startsWith("content://")) Uri.parse(tx.photoPath) else File(tx.photoPath))
                    .centerCrop()
                    .placeholder(R.drawable.ic_camera)
                    .into(b.ivPhoto)
            } else {
                b.cardPhoto.visibility = View.GONE
            }

            b.btnEdit.setOnClickListener {
                val action = TransactionDetailFragmentDirections.actionDetailToEdit(tx.type, tx.id)
                findNavController().navigate(action)
            }

            b.btnDelete.setOnClickListener {
                AlertDialog.Builder(requireContext()).setTitle("Delete Transaction")
                    .setMessage("This cannot be undone.")
                    .setPositiveButton("Delete") { _, _ -> 
                        lifecycleScope.launch {
                            // Wait for the repository to finish deletion and wallet adjustment
                            vm.delete(tx).join()
                            findNavController().popBackStack() 
                        }
                    }
                    .setNegativeButton("Cancel", null).show()
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}

// ── Adapter ────────────────────────────────────────────────────────
class TransactionAdapter(private val onClick: (Transaction) -> Unit) : ListAdapter<Transaction, TransactionAdapter.VH>(DIFF) {
    private val fmt = NumberFormat.getCurrencyInstance(Locale("en","ZA"))
    private val df  = SimpleDateFormat("dd MMM", Locale.getDefault())

    inner class VH(val b: ItemTransactionBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(tx: Transaction) {
            b.tvEmoji.text  = tx.categoryName.firstOrNull()?.toString() ?: "💰"
            b.tvName.text   = tx.categoryName
            b.tvDesc.text   = tx.description.ifEmpty { tx.categoryName }
            b.tvDate.text   = df.format(Date(tx.date))
            b.tvAmount.text = "${if (tx.type == Transaction.TYPE_INCOME) "+" else "-"}${fmt.format(tx.amount)}"
            b.tvAmount.setTextColor(b.root.context.getColor(if (tx.type == Transaction.TYPE_INCOME) R.color.income_green else R.color.expense_red))
            b.ivPhotoIcon.visibility = if (tx.photoPath.isNotEmpty()) View.VISIBLE else View.GONE
            b.root.setOnClickListener { onClick(tx) }
        }
    }

    override fun onCreateViewHolder(p: ViewGroup, t: Int) = VH(ItemTransactionBinding.inflate(LayoutInflater.from(p.context), p, false))
    override fun onBindViewHolder(h: VH, pos: Int) = h.bind(getItem(pos))
    companion object DIFF : DiffUtil.ItemCallback<Transaction>() {
        override fun areItemsTheSame(a: Transaction, b: Transaction) = a.id == b.id
        override fun areContentsTheSame(a: Transaction, b: Transaction) = a == b
    }
}
