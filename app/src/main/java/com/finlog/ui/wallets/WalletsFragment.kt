package com.finlog.ui.wallets

import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import androidx.recyclerview.widget.*
import com.finlog.FinLogApp
import com.finlog.R
import com.finlog.data.model.Wallet
import com.finlog.data.repository.Repository
import com.finlog.databinding.FragmentWalletsBinding
import com.finlog.databinding.ItemWalletBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.*

class WalletsViewModel(private val repo: Repository) : ViewModel() {
    val wallets      = repo.getWallets().asLiveData()
    val totalBalance = repo.getTotalBalance().asLiveData()
    fun save(w: Wallet)   = viewModelScope.launch { repo.saveWallet(w) }
    fun delete(w: Wallet) = viewModelScope.launch { repo.deleteWallet(w) }
    fun transfer(fromId: String, toId: String, amount: Double) = viewModelScope.launch { repo.transfer(fromId, toId, amount) }
}

class WalletsVMFactory(private val repo: Repository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = WalletsViewModel(repo) as T
}

class WalletsFragment : Fragment() {
    private var _b: FragmentWalletsBinding? = null
    private val b get() = _b!!
    private val vm: WalletsViewModel by viewModels { WalletsVMFactory((requireActivity().application as FinLogApp).repo) }
    private val fmt = NumberFormat.getCurrencyInstance(Locale("en", "ZA"))

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentWalletsBinding.inflate(i, c, false)
        return b.root
    }

    override fun onViewCreated(view: View, s: Bundle?) {
        super.onViewCreated(view, s)
        val adapter = WalletAdapter(
            onTransfer = { wallet -> showTransfer(wallet) },
            onDelete   = { wallet ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Delete Wallet")
                    .setMessage("Delete \"${wallet.name}\"?")
                    .setPositiveButton("Delete") { _, _ -> vm.delete(wallet) }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )
        b.rvWallets.layoutManager = LinearLayoutManager(requireContext())
        b.rvWallets.adapter = adapter
        b.fabAdd.setOnClickListener { showAdd() }
        vm.totalBalance.observe(viewLifecycleOwner) { b.tvTotal.text = fmt.format(it) }
        vm.wallets.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            b.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun showAdd() {
        val dv = layoutInflater.inflate(R.layout.dialog_add_wallet, null)
        val etName = dv.findViewById<TextInputEditText>(R.id.etName)
        val etBal  = dv.findViewById<TextInputEditText>(R.id.etBalance)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("New Wallet")
            .setView(dv)
            .setPositiveButton("Create") { _, _ ->
                val name = etName.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(requireContext(), "Name required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                vm.save(Wallet(name = name, balance = etBal.text.toString().toDoubleOrNull() ?: 0.0))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showTransfer(from: Wallet) {
        val walletsList = vm.wallets.value?.filter { it.id != from.id } ?: return
        if (walletsList.isEmpty()) {
            Toast.makeText(requireContext(), "Need at least 2 wallets", Toast.LENGTH_SHORT).show()
            return
        }
        val dv = layoutInflater.inflate(R.layout.dialog_transfer, null)
        val et = dv.findViewById<TextInputEditText>(R.id.etAmount)
        val sp = dv.findViewById<android.widget.Spinner>(R.id.spinnerTo)
        sp.adapter = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, walletsList.map { "${it.emoji} ${it.name}" })
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Transfer from ${from.name}")
            .setView(dv)
            .setPositiveButton("Transfer") { _, _ ->
                val amt = et.text.toString().toDoubleOrNull()
                if (amt == null || amt <= 0) {
                    Toast.makeText(requireContext(), "Invalid amount", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (amt > from.balance) {
                    Toast.makeText(requireContext(), "Insufficient funds", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                vm.transfer(from.id, walletsList[sp.selectedItemPosition].id, amt)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}

class WalletAdapter(private val onTransfer: (Wallet) -> Unit, private val onDelete: (Wallet) -> Unit) :
    ListAdapter<Wallet, WalletAdapter.VH>(DIFF) {

    class VH(val b: ItemWalletBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(w: Wallet, onTransfer: (Wallet) -> Unit, onDelete: (Wallet) -> Unit) {
            b.tvEmoji.text = w.emoji
            b.tvName.text = w.name
            b.tvCurrency.text = w.currency
            val balanceStr = String.format(Locale.getDefault(), "%.2f", w.balance)
            b.tvBalance.text = "${w.currency} $balanceStr"
            b.btnTransfer.setOnClickListener { onTransfer(w) }
            b.root.setOnLongClickListener { onDelete(w); true }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        return VH(ItemWalletBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position), onTransfer, onDelete)
    }

    companion object DIFF : DiffUtil.ItemCallback<Wallet>() {
        override fun areItemsTheSame(oldItem: Wallet, newItem: Wallet): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Wallet, newItem: Wallet): Boolean = oldItem == newItem
    }
}
