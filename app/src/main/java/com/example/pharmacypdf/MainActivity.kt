package com.example.pharmacypdf

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telephony.SmsManager
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pharmacypdf.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var adapter: CustomerAdapter
    private lateinit var licenseManager: LicenseManager
    private var customerList = mutableListOf<Customer>()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            Toast.makeText(this, "يجب السماح بصلاحية SMS للإرسال", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dbHelper = DatabaseHelper(this)
        licenseManager = LicenseManager(this)
        licenseManager.startTrialIfFirstRun()

        checkActivation()
        setupRecyclerView()
        loadCustomers()
        setupClickListeners()
    }
    
    override fun onResume() {
        super.onResume()
        checkActivation()
    }

    private fun checkActivation() {
        if (!licenseManager.isActivated()) {
            showActivationDialog(true)
        } else {
            val days = licenseManager.getRemainingDays()
            binding.btnActivation.text = "مفعل - متبقي $days يوم"
        }
    }

    private fun setupRecyclerView() {
        adapter = CustomerAdapter(
            customerList,
            onDeleteClick = { customer -> deleteCustomer(customer) },
            onSmsClick = { customer -> sendSmsToCustomer(customer) },
            onWhatsAppClick = { customer -> sendWhatsAppToCustomer(customer) }
        )
        binding.rvCustomers.layoutManager = LinearLayoutManager(this)
        binding.rvCustomers.adapter = adapter
    }

    private fun setupClickListeners() {
        binding.btnAddCustomer.setOnClickListener {
            if (!licenseManager.isActivated()) {
                showActivationDialog(true); return@setOnClickListener
            }
            showAddCustomerDialog()
        }

        binding.btnDeleteAll.setOnClickListener {
            if (!licenseManager.isActivated()) {
                showActivationDialog(true); return@setOnClickListener
            }
            dbHelper.deleteAllCustomers()
            loadCustomers()
            Toast.makeText(this, "تم حذف الكل", Toast.LENGTH_SHORT).show()
        }

        binding.btnSmsAll.setOnClickListener {
            if (!licenseManager.isActivated()) {
                showActivationDialog(true); return@setOnClickListener
            }
            sendSmsToAll()
        }

        binding.btnWhatsAppAll.setOnClickListener {
            if (!licenseManager.isActivated()) {
                showActivationDialog(true); return@setOnClickListener
            }
            sendWhatsAppToAll()
        }

        binding.btnActivation.setOnClickListener {
            showActivationDialog(false)
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

    private fun showAddCustomerDialog() {
        // فورم بسيط للإضافة
        val editText = EditText(this)
        editText.hint = "الاسم,الرقم,الدين"
        AlertDialog.Builder(this)
           .setTitle("إضافة عميل جديد")
           .setMessage("ادخل البيانات مفصولة بفاصلة\nمثال: احمد,777123456,500")
           .setView(editText)
           .setPositiveButton("إضافة") { _, _ ->
                val data = editText.text.toString().split(",")
                if (data.size == 3) {
                    val newCustomer = Customer(0, data[0].trim(), data[1].trim(), data[2].trim().toDouble())
                    dbHelper.addCustomer(newCustomer)
                    loadCustomers()
                } else {
                    Toast.makeText(this, "البيانات غير صحيحة", Toast.LENGTH_SHORT).show()
                }
            }
           .setNegativeButton("إلغاء", null)
           .show()
    }
    
    private fun deleteCustomer(customer: Customer) {
        dbHelper.deleteCustomer(customer.id)
        loadCustomers()
        Toast.makeText(this, "تم حذف ${customer.name}", Toast.LENGTH_SHORT).show()
    }

    private fun sendSmsToCustomer(customer: Customer) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)!= PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.SEND_SMS)
            return
        }
        val smsManager = this.getSystemService(SmsManager::class.java)
        val message = "عزيزي ${customer.name}، نود تذكيرك بمبلغ الدين: ${customer.debt} ريال."
        smsManager.sendTextMessage(customer.phone, null, message, null, null)
        Toast.makeText(this, "تم الإرسال لـ ${customer.name}", Toast.LENGTH_SHORT).show()
    }

    private fun sendWhatsAppToCustomer(customer: Customer) {
        val message = "عزيزي ${customer.name}، نود تذكيرك بمبلغ الدين: ${customer.debt} ريال."
        val url = "https://api.whatsapp.com/send?phone=${customer.phone}&text=${Uri.encode(message)}"
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse(url)
        startActivity(intent)
    }

    private fun sendSmsToAll() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)!= PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.SEND_SMS)
            return
        }
        if (customerList.isEmpty()){
            Toast.makeText(this, "لا يوجد عملاء", Toast.LENGTH_SHORT).show()
            return
        }
        val smsManager = this.getSystemService(SmsManager::class.java)
        for (customer in customerList) {
            if (customer.phone.isNotEmpty()) {
                val message = "عزيزي ${customer.name}، نود تذكيرك بمبلغ الدين: ${customer.debt} ريال."
                smsManager.sendTextMessage(customer.phone, null, message, null, null)
            }
        }
        Toast.makeText(this, "تم إرسال الرسائل للكل", Toast.LENGTH_SHORT).show()
    }

    private fun sendWhatsAppToAll() {
        if (customerList.isEmpty()){
            Toast.makeText(this, "لا يوجد عملاء", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, "سيتم فتح واتس لكل عميل على حدة", Toast.LENGTH_LONG).show()
        for (customer in customerList) {
            if (customer.phone.isNotEmpty()) {
                sendWhatsAppToCustomer(customer)
            }
        }
    }

    private fun showActivationDialog(force: Boolean) {
        val editText = EditText(this)
        editText.hint = "ادخل كود التفعيل"
        
        val builder = AlertDialog.Builder(this)
           .setTitle("تفعيل التطبيق")
           .setMessage("الفترة التجريبية: ${licenseManager.getRemainingDays()} يوم متبقي\n\nادخل كود التفعيل للاستمرار")
           .setView(editText)
           .setPositiveButton("تفعيل") { _, _ ->
                val code = editText.text.toString().trim().uppercase()
                val result = licenseManager.activateWithCode(code)
                Toast.makeText(this, result, Toast.LENGTH_LONG).show()
                checkActivation()
            }
        
        if (!force) {
            builder.setNegativeButton("لاحقاً", null)
        }
        
        val dialog = builder.create()
        dialog.setCancelable(!force)
        dialog.show()
    }
}
