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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

// ── ViewModel ──────────────────────────────────────────────────────
class TransactionViewModel(private val repo: Repository) : ViewModel() {
    val transactions = repo.getTransactions().asLiveData()
    val categories   = repo.getCategories().asLiveData()
    val wallets      = repo.getWallets().asLiveData()
    fun add(t: Transaction)    = viewModelScope.launch { repo.addTransaction(t) }
    fun update(t: Transaction) = viewModelScope.launch { repo.updateTransaction(t) }
    fun delete(t: Transaction) = viewModelScope.launch { repo.deleteTransaction(t) }
    fun filtered(catId: String?, type: String?, from: Long?, to: Long?): LiveData<List<Transaction>> = when {
        catId != null -> repo.getByCategory(catId).asLiveData()
        type  != null -> repo.getByType(type).asLiveData()
        from  != null && to != null -> repo.getByDateRange(from, to).asLiveData()
        else -> transactions
    }
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
        b.fabAdd.setOnClickListener { findNavController().navigate(R.id.action_transactions_to_addEdit) }
        b.chipIncome.setOnCheckedChangeListener { _, ch -> load(if (ch) Transaction.TYPE_INCOME else null, null, null, null, adapter) }
        b.chipExpense.setOnCheckedChangeListener { _, ch -> load(null, if (ch) Transaction.TYPE_EXPENSE else null, null, null, adapter) }
        b.btnFilterDate.setOnClickListener { pickDate(adapter) }
        b.btnClearFilter.setOnClickListener { b.chipGroupType.clearCheck(); load(null, null, null, null, adapter) }
        load(null, null, null, null, adapter)
    }

    private fun load(catId: String?, type: String?, from: Long?, to: Long?, adapter: TransactionAdapter) {
        vm.filtered(catId, type, from, to).observe(viewLifecycleOwner) { list ->
            adapter.submitList(list); b.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun pickDate(adapter: TransactionAdapter) {
        val cal = Calendar.getInstance()
        DatePickerDialog(requireContext(), { _, y, m, d ->
            val s = Calendar.getInstance().apply { set(y,m,d,0,0,0) }.timeInMillis
            DatePickerDialog(requireContext(), { _, y2, m2, d2 ->
                val e = Calendar.getInstance().apply { set(y2,m2,d2,23,59,59) }.timeInMillis
                load(null, null, s, e, adapter)
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

    private var selectedDate = System.currentTimeMillis()
    private var selectedStart = System.currentTimeMillis()
    private var selectedEnd   = System.currentTimeMillis()
    private var catId = "cat_food"; private var catName = "Food"
    private var walletId = ""; private var txType = Transaction.TYPE_EXPENSE
    private var photoPath = ""; private var cameraUri: Uri? = null
    private val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

    private val pickPhoto = registerForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { setPhoto(it.toString()) } }
    private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) cameraUri?.let { setPhoto(it.toString()) } }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View { _b = FragmentAddEditTransactionBinding.inflate(i, c, false); return b.root }

    override fun onViewCreated(view: View, s: Bundle?) {
        super.onViewCreated(view, s)
        updateLabels()
        b.btnExpense.isChecked = true
        b.toggleType.addOnButtonCheckedListener { _, id, ch ->
            if (!ch) return@addOnButtonCheckedListener
            txType = when (id) { R.id.btnIncome -> Transaction.TYPE_INCOME; R.id.btnTransfer -> Transaction.TYPE_TRANSFER; else -> Transaction.TYPE_EXPENSE }
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
            walletId = wallets.first().id
            b.spinnerWallet.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, wallets.map { "${it.emoji} ${it.name}" })
                .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
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
            vm.add(Transaction(amount=amt, type=txType, categoryId=catId, categoryName=catName,
                description=b.etDescription.text.toString(), date=selectedDate,
                startTime=selectedStart, endTime=selectedEnd, walletId=walletId,
                photoPath=photoPath, isRecurring=b.switchRecurring.isChecked, recurringInterval=interval))
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
        CoroutineScope(Dispatchers.Main).launch {
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
                Glide.with(requireContext()).load(tx.photoPath).centerCrop().into(b.ivPhoto)
            }
            b.btnDelete.setOnClickListener {
                AlertDialog.Builder(requireContext()).setTitle("Delete Transaction")
                    .setMessage("This cannot be undone.")
                    .setPositiveButton("Delete") { _, _ -> vm.delete(tx); findNavController().navigateUp() }
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
