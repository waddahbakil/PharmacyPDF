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
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.InputStream

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var adapter: CustomerAdapter
    private lateinit var licenseManager: LicenseManager
    private var customerList = mutableListOf<Customer>()

    // لاختيار ملف الإكسل
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
            Toast.makeText(this, "يجب السماح بصلاحية SMS للإرسال", Toast.LENGTH_SHORT).show()
        }
    }

    private val requestStoragePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            openExcelPicker()
        } else {
            Toast.makeText(this, "يجب السماح بقراءة الملفات للاستيراد", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dbHelper = DatabaseHelper(this)
        licenseManager = LicenseManager(this)
        licenseManager.startTrialIfFirstRun()

        // اطبع Device ID عشان تولد الأكواد للعملاء
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
            if (!licenseManager.isActivated()) {
                showActivationDialog(true); return@setOnClickListener
            }
            showAddCustomerDialog()
        }

        binding.btnDeleteAll.setOnClickListener {
            if (!licenseManager.isActivated()) {
                showActivationDialog(true); return@setOnClickListener
            }
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
            if (!licenseManager.isActivated()) {
                showActivationDialog(true); return@setOnClickListener
            }
            checkStoragePermissionAndImport()
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

    private fun checkStoragePermissionAndImport() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            openExcelPicker()
        } else {
            requestStoragePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun openExcelPicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" // .xlsx
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        pickExcelLauncher.launch(Intent.createChooser(intent, "اختر ملف الإكسل"))
    }

    private fun importExcelFromUri(uri: Uri) {
        try {
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            val workbook = WorkbookFactory.create(inputStream)
            val sheet = workbook.getSheetAt(0)
            var importedCount = 0

            for (row in sheet) {
                if (row.rowNum == 0) continue // تخطي العناوين
                try {
                    val name = row.getCell(0)?.toString() ?: ""
                    val phone = row.getCell(1)?.toString() ?: ""
                    val debt = row.getCell(2)?.numericCellValue ?: 0.0
                    
                    if (name.isNotEmpty() && phone.isNotEmpty()) {
                        val customer = Customer(0, name, phone, debt)
                        dbHelper.addCustomer(customer)
                        importedCount++
                    }
                } catch (e: Exception) {
                    Log.e("ExcelImport", "Error in row ${row.rowNum}", e)
                }
            }
            workbook.close()
            loadCustomers()
            Toast.makeText(this, "تم استيراد $importedCount عميل", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "فشل الاستيراد: ${e.message}", Toast.LENGTH_LONG).show()
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
           .setMessage("ادخل البيانات مفصولة بفاصلة\nمثال: احمد,777123456,500")
           .setView(editText)
           .setPositiveButton("إضافة") { _, _ ->
                val data = editText.text.toString().split(",")
                if (data.size == 3) {
                    try {
                        val newCustomer = Customer(0, data[0].trim(), data[1].trim(), data[2].trim().toDouble())
                        dbHelper.addCustomer(newCustomer)
                        loadCustomers()
                    } catch (e: Exception) {
                        Toast.makeText(this, "تأكد من كتابة الدين كرقم", Toast.LENGTH_SHORT).show()
                    }
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
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            requestSmsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
            return
        }
        try {
            val smsManager = this.getSystemService(SmsManager::class.java)
            val message = "عزيزي ${customer.name}، نود تذكيرك بمبلغ الدين: ${customer.debt} ريال."
            smsManager.sendTextMessage(customer.phone, null, message, null, null)
            Toast.makeText(this, "تم الإرسال لـ ${customer.name}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "فشل الإرسال: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendWhatsAppToCustomer(customer: Customer) {
        try {
            val message = "عزيزي ${customer.name}، نود تذكيرك بمبلغ الدين: ${customer.debt} ريال."
            val url = "https://api.whatsapp.com/send?phone=${customer.phone}&text=${Uri.encode(message)}"
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse(url)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "الواتساب غير مثبت", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendSmsToAll() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            requestSmsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
            return
        }
        if (customerList.isEmpty()) {
            Toast.makeText(this, "لا يوجد عملاء", Toast.LENGTH_SHORT).show()
            return
        }
        val smsManager = this.getSystemService(SmsManager::class.java)
        var successCount = 0
        for (customer in customerList) {
            if (customer.phone.isNotEmpty()) {
                try {
                    val message = "عزيزي ${customer.name}، نود تذكيرك بمبلغ الدين: ${customer.debt} ريال."
                    smsManager.sendTextMessage(customer.phone, null, message, null, null)
                    successCount++
                    Thread.sleep(500) // توقف نص ثانية بين كل رسالة عشان ما يحظرونك
                } catch (e: Exception) {
                    Log.e("SMS_ALL", "Failed for ${customer.name}", e)
                }
            }
        }
        Toast.makeText(this, "تم إرسال $successCount رسالة من ${customerList.size}", Toast.LENGTH_LONG).show()
    }

    private fun sendWhatsAppToAll() {
        if (customerList.isEmpty()) {
            Toast.makeText(this, "لا يوجد عملاء", Toast.LENGTH_SHORT).show()
            return
        }
        // واتساب ما يسمح تفتح محادثات كثيرة مرة واحدة. لازم كل واحد لحاله
        Toast.makeText(this, "سيتم فتح واتساب لكل عميل. اضغط رجوع بعد كل رسالة", Toast.LENGTH_LONG).show()
        
        // نرسل للكل بالتتابع. المستخدم لازم يرجع للتطبيق بعد كل رسالة
        for (customer in customerList) {
            if (customer.phone.isNotEmpty()) {
                sendWhatsAppToCustomer(customer)
                Thread.sleep(1000) // نعطيه فرصة ثانية
            }
        }
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
