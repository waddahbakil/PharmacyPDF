package com.example.pharmacypdf

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telephony.SmsManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pharmacypdf.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var adapter: CustomerAdapter
    private var customerList = mutableListOf<Customer>()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            Toast.makeText(this, "يجب السماح بالصلاحية للإرسال", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dbHelper = DatabaseHelper(this)
        setupRecyclerView()
        loadCustomers()
        setupClickListeners()
    }

    private fun setupRecyclerView() {
        adapter = CustomerAdapter(customerList)
        binding.rvCustomers.layoutManager = LinearLayoutManager(this)
        binding.rvCustomers.adapter = adapter
    }

    private fun setupClickListeners() {
        binding.btnAddCustomer.setOnClickListener {
            val newCustomer = Customer(0, "عميل تجريبي", "777000000", 250.0)
            dbHelper.addCustomer(newCustomer)
            loadCustomers()
            Toast.makeText(this, "تمت الإضافة", Toast.LENGTH_SHORT).show()
        }

        binding.btnDeleteAll.setOnClickListener {
            dbHelper.deleteAllCustomers()
            loadCustomers()
            Toast.makeText(this, "تم حذف الكل", Toast.LENGTH_SHORT).show()
        }

        binding.btnSmsAll.setOnClickListener {
            sendSmsToAll()
        }

        binding.btnWhatsAppAll.setOnClickListener {
            sendWhatsAppToAll()
        }
        
        binding.btnImportExcel.setOnClickListener {
            Toast.makeText(this, "ميزة الاستيراد قريباً", Toast.LENGTH_SHORT).show()
        }

        binding.btnActivation.setOnClickListener {
            Toast.makeText(this, "التطبيق مفعل", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadCustomers() {
        customerList.clear()
        customerList.addAll(dbHelper.getAllCustomers())
        adapter.notifyDataSetChanged()
        updateTotalDebt()
    }

    private fun updateTotalDebt() {
        val total = customerList.sumOf { it.debt }
        binding.tvTotalDebt.text = "إجمالي الديون: $total ريال"
    }

    private fun sendSmsToAll() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.SEND_SMS)
            return
        }
        if (customerList.isEmpty()){
            Toast.makeText(this, "لا يوجد عملاء", Toast.LENGTH_SHORT).show()
            return
        }
        val smsManager = SmsManager.getDefault()
        for (customer in customerList) {
            if (customer.phone.isNotEmpty()) {
                val message = "عزيزي ${customer.name}، نود تذكيرك بمبلغ الدين: ${customer.debt} ريال."
                smsManager.sendTextMessage(customer.phone, null, message, null)
            }
        }
        Toast.makeText(this, "تم إرسال الرسائل", Toast.LENGTH_SHORT).show()
    }

    private fun sendWhatsAppToAll() {
        if (customerList.isEmpty()){
            Toast.makeText(this, "لا يوجد عملاء", Toast.LENGTH_SHORT).show()
            return
        }
        for (customer in customerList) {
             if (customer.phone.isNotEmpty()) {
                val message = "عزيزي ${customer.name}، نود تذكيرك بمبلغ الدين: ${customer.debt} ريال."
                val url = "https://api.whatsapp.com/send?phone=967${customer.phone}&text=${Uri.encode(message)}"
                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = Uri.parse(url)
                startActivity(intent)
             }
        }
    }
}
