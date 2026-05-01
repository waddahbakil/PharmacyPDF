package com.example.pharmacypdf

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.telephony.SmsManager
import android.util.Log
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pharmacypdf.databinding.ActivityMainBinding
import smile.io.Read

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var adapter: CustomerAdapter
    private lateinit var licenseManager: LicenseManager
    private var customerList = mutableListOf<Customer>()

    private val pickExcelLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                importExcelFromUri(uri)
            }
        }
    }

    private val requestSmsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            Toast.makeText(this, "تم رفض صلاحية SMS", Toast.LENGTH_SHORT).show()
        }
    }

    private val requestStoragePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            openExcelPicker()
        } else {
            Toast.makeText(this, "تم رفض صلاحية قراءة الملفات", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dbHelper = DatabaseHelper(this)
        licenseManager = LicenseManager(this)
        licenseManager.startTrialIfFirstRun()

        Log.d("DEVICE_ID", Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID))

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
            if (!licenseManager.isActivated()) { showActivationDialog(true); return@setOnClickListener }
            showAddCustomerDialog()
        }

        binding.btnDeleteAll.setOnClickListener {
            if (!licenseManager.isActivated()) { showActivationDialog(true); return@setOnClickListener }
            AlertDialog.Builder(this)
              .setTitle("تأكيد الحذف")
              .setMessage("هل أنت متأكد من حذف كل العملاء؟")
              .setPositiveButton("نعم") { _, _ ->
                    dbHelper.deleteAllCustomers()
                    loadCustomers()
                    Toast.makeText(this, "تم حذف الكل", Toast.LENGTH_SHORT).show()
                }
              .setNegativeButton("لا", null)
              .show()
        }

        binding.btnImportExcel.setOnClickListener {
            if (!licenseManager.isActivated()) { showActivationDialog(true); return@setOnClickListener }
            checkStoragePermissionAndImport()
        }

        binding.btnSmsAll.setOnClickListener {
            if (!licenseManager.isActivated()) { showActivationDialog(true); return@setOnClickListener }
            sendSmsToAll()
        }

        binding.btnWhatsAppAll.setOnClickListener {
            if (!licenseManager.isActivated()) { showActivationDialog(true); return@setOnClickListener }
            sendWhatsAppToAll()
        }

        binding.btnActivation.setOnClickListener {
            showActivationDialog(false)
        }
    }

    private fun checkStoragePermissionAndImport() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            openExcelPicker()
        } else {
            requestStoragePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun openExcelPicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        pickExcelLauncher.launch(Intent.createChooser(intent, "اختر ملف الإكسل"))
    }

    private fun importExcelFromUri(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val df = Read.xlsx(inputStream, header = true) // يقرأ أول صف كعناوين
            var importedCount = 0

            for (i in 0 until df.nrows()) {
                try {
                    val name = df.get(i, 0)?.toString()?: ""
                    val phone = df.get(i, 1)?.toString()?: ""
                    val debt = df.get(i, 2)?.toString()?.toDoubleOrNull()?: 0.0
                    
                    if (name.isNotEmpty() && phone.isNotEmpty()) {
                        val customer = Customer(0, name, phone, debt)
                        dbHelper.addCustomer(customer)
                        importedCount++
                    }
                } catch (e: Exception) {
                    Log.e("ExcelImport", "Error in row $i", e)
                }
            }
            loadCustomers()
            Toast.makeText(this, "تم استيراد $importedCount عميل", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "فشل الاستيراد: تأكد أن الملف.xlsx", Toast.LENGTH_LONG).show()
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
        val editText = EditText(this)
        editText.hint = "الاسم,الرقم,الدين"
        AlertDialog.Builder(this)
          .setTitle("إضافة عميل جديد")
          .setMessage("مثال: احمد,777123456,500")
          .setView(editText)
          .setPositiveButton("إضافة") { _, _ ->
                val data = editText.text.toString().split(",")
                if (data.size == 3) {
                    try {
                        val newCustomer = Customer(0, data[0].trim(), data[1].trim(), data[2].trim().toDouble())
                        dbHelper.addCustomer(newCustomer)
                        loadCustomers()
                    } catch (e: Exception) {
                        Toast.makeText(this, "الدين لازم يكون رقم", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "اكتب 3 قيم مفصولة بفاصلة", Toast.LENGTH_SHORT).show()
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
            requestSmsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
            return
        }
        try {
            val smsManager = this.getSystemService(SmsManager::class.java)
            val message = "عزيزي ${customer.name}، نود تذكيرك بمبلغ الدين: ${customer.debt} ريال. شكراً لتعاملكم معنا."
            smsManager.sendTextMessage(customer.phone, null, message, null, null)
            Toast.makeText(this, "تم الإرسال لـ ${customer.name}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "فشل الإرسال: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendWhatsAppToCustomer(customer: Customer) {
        try {
            val message = "عزيزي ${customer.name}، نود تذكيرك بمبلغ الدين: ${customer.debt} ريال. شكراً لتعاملكم معنا."
            val url = "https://api.whatsapp.com/send?phone=967${customer.phone}&text=${Uri.encode(message)}"
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse(url)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "الواتساب غير مثبت", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendSmsToAll() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)!= PackageManager.PERMISSION_GRANTED) {
            requestSmsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
            return
        }
        if (customerList.isEmpty()) {
            Toast.makeText(this, "لا يوجد عملاء", Toast.LENGTH_SHORT).show()
            return
        }
        val smsManager = this.getSystemService(SmsManager::class.java)
        var successCount = 0
        Thread {
            for (customer in customerList) {
                if (customer.phone.isNotEmpty()) {
                    try {
                        val message = "عزيزي ${customer.name}، نود تذكيرك بمبلغ الدين: ${customer.debt} ريال."
                        smsManager.sendTextMessage(customer.phone, null, message, null, null)
                        successCount++
                        Thread.sleep(1000) // توقف ثانية بين كل رسالة عشان ما تنحظر
                    } catch (e: Exception) {
                        Log.e("SMS_ALL", "Failed for ${customer.name}", e)
                    }
                }
            }
            runOnUiThread {
                 Toast.makeText(this, "تم إرسال $successCount رسالة من ${customerList.size}", Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    private fun sendWhatsAppToAll() {
        if (customerList.isEmpty()) {
            Toast.makeText(this, "لا يوجد عملاء", Toast.LENGTH_SHORT).show()
            return
        }
        // واتساب يمنع فتح عدة محادثات. لازم واحد واحد
        AlertDialog.Builder(this)
           .setTitle("تنبيه واتساب")
           .setMessage("سيتم فتح محادثة واتساب لكل عميل. بعد الإرسال اضغط رجوع للانتقال للعميل التالي.")
           .setPositiveButton("ابدأ") { _, _ ->
                for (customer in customerList) {
                    if (customer.phone.isNotEmpty()) {
                        sendWhatsAppToCustomer(customer)
                        Thread.sleep(1500) // نعطيه فرصة يفتح
                    }
                }
            }
           .setNegativeButton("إلغاء", null)
           .show()
    }

    private fun showActivationDialog(force: Boolean) {
        val editText = EditText(this)
        editText.hint = "WD-WXXXX-X"

        val builder = AlertDialog.Builder(this)
          .setTitle("تفعيل التطبيق")
          .setMessage("الفترة التجريبية: ${licenseManager.getRemainingDays()} يوم متبقي\n\nادخل كود التفعيل")
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
