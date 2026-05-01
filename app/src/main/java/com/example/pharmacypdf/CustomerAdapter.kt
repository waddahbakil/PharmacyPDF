package com.example.pharmacypdf

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.pharmacypdf.databinding.ItemCustomerBinding

class CustomerAdapter(
    private val customerList: MutableList<Customer>,
    private val onDeleteClick: (Customer) -> Unit,
    private val onSmsClick: (Customer) -> Unit,
    private val onWhatsAppClick: (Customer) -> Unit
) : RecyclerView.Adapter<CustomerAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemCustomerBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCustomerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val customer = customerList[position]
        holder.binding.tvCustomerName.text = customer.name
        holder.binding.tvCustomerPhone.text = customer.phone
        holder.binding.tvCustomerDebt.text = "الدين: ${customer.debt} ريال"

        holder.binding.btnDelete.setOnClickListener { onDeleteClick(customer) }
        holder.binding.btnSms.setOnClickListener { onSmsClick(customer) }
        holder.binding.btnWhatsApp.setOnClickListener { onWhatsAppClick(customer) }
    }

    override fun getItemCount() = customerList.size
}
