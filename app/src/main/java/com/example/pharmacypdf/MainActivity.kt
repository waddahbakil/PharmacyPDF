package com.example.pharmacypdf

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var adapter: CustomerAdapter
    private lateinit var licenseManager: LicenseManager
    private var customerList = mutableListOf<Customer>()
    private val handler = Handler(Looper.getMainLooper())
    private var isSendingWhatsApp = false

    private val pickCsvLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri -> importCsvFromUri(uri) }
        }
    }

    private val requestSmsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) Toast.makeText(this, "تم رفض صلاحية SMS", Toast.LENGTH_SHORT).show()
    }

    private val requestStoragePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) openCsvPicker() else Toast.makeText(this, "تم رفض صلاحية قراءة الملفات", Toast.LENGTH_SHORT).show()
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
            openCsvPicker()
        } else {
            requestStoragePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun openCsvPicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "text/*"
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        pickCsvLauncher.launch(Intent.createChooser(intent, "اختر ملف CSV"))
    }

    private fun importCsvFromUri(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val reader = inputStream?.bufferedReader()
            var importedCount = 0
            var errorCount = 0

            reader?.readLine() // تخطي العنوان

            reader?.forEachLine { line ->
                try {
                    val columns = line.split(",")
                    if (columns.size >= 3) {
                        val name = columns[0].trim()
                        val phone = columns[1].trim().replace(" ", "").replace("-", "")
                        val debt = columns[2].trim().toDoubleOrNull()?: 0.0

                        if (name.isNotEmpty() && phone.isNotEmpty()) {
                            dbHelper.addCustomer(Customer(0, name, phone, debt))
                            importedCount++
                        }
                    }
                } catch (e: Exception) {
                    errorCount++
                    Log.e("CSVImport", "Error in line: $line", e)
                }
            }
            reader?.close()
            loadCustomers()
            Toast.makeText(this, "تم استيراد $importedCount عميل. أخطاء: $errorCount", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "فشل الاستيراد: تأكد أن الملف CSV", Toast.LENGTH_LONG).show()
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
        AlertDialog.Builder(this)
   .setTitle("تأكيد الحذف")
   .setMessage("حذف ${customer.name}؟")
   .setPositiveButton("حذف") { _, _ ->
                dbHelper.deleteCustomer(customer.id)
                loadCustomers()
            }
   .setNegativeButton("إلغاء", null)
   .show()
    }

    private fun sendSmsToCustomer(customer: Customer) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)!= PackageManager.PERMISSION_GRANTED) {
            requestSmsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
            return
        }
        try {
            val smsManager = this.getSystemService(SmsManager::class.java)
            val message = "عزيزي ${customer.name}، نود تذكيرك بمبلغ الدين: ${customer.debt} ريال."
            smsManager.sendTextMessage(customer.phone, null, message, null)
            Toast.makeText(this, "تم الإرسال لـ ${customer.name}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "فشل الإرسال", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendWhatsAppToCustomer(customer: Customer) {
        try {
            val message = "عزيزي ${customer.name}، نود تذكيرك بمبلغ الدين: ${customer.debt} ريال."
            var phone = customer.phone.replace(" ", "").replace("-", "")
            if (!phone.startsWith("967")) phone = "967$phone"
            val url = "https://api.whatsapp.com/send?phone=$phone&text=${Uri.encode(message)}"
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
        var index = 0
        var successCount = 0

        fun sendNext() {
            if (index >= customerList.size) {
                Toast.makeText(this, "اكتمل الإرسال: $successCount من ${customerList.size}", Toast.LENGTH_LONG).show()
                return
            }
            val customer = customerList[index]
            if (customer.phone.isNotEmpty()) {
                try {
                    val message = "عزيزي ${customer.name}، نود تذكيرك بمبلغ الدين: ${customer.debt} ريال."
                    smsManager.sendTextMessage(customer.phone, null, message, null)
                    successCount++
                } catch (e: Exception) {
                    Log.e("SMS_ALL", "Failed for ${customer.name}", e)
                }
            }
            index++
            handler.postDelayed({ sendNext() }, 1500)
        }

        AlertDialog.Builder(this)
  .setTitle("إرسال جماعي SMS")
  .setMessage("سيتم الإرسال لـ ${customerList.size} عميل. المدة المتوقعة: ${customerList.size * 1.5} ثانية")
  .setPositiveButton("ابدأ") { _, _ ->
                Toast.makeText(this, "جاري الإرسال...", Toast.LENGTH_SHORT).show()
                sendNext()
            }
  .setNegativeButton("إلغاء", null)
  .show()
    }

    private fun sendWhatsAppToAll() {
        if (customerList.isEmpty()) {
            Toast.makeText(this, "لا يوجد عملاء", Toast.LENGTH_SHORT).show()
            return
        }
        if (isSendingWhatsApp) {
            Toast.makeText(this, "جاري الإرسال بالفعل", Toast.LENGTH_SHORT).show()
            return
        }

        val validCustomers = customerList.filter { it.phone.isNotEmpty() }
        if (validCustomers.isEmpty()) {
            Toast.makeText(this, "لا يوجد أرقام صحيحة", Toast.LENGTH_SHORT).show()
            return
        }

        var index = 0
        isSendingWhatsApp = true

        fun openNext() {
            if (index >= validCustomers.size) {
                isSendingWhatsApp = false
                Toast.makeText(this, "تم فتح كل المحادثات", Toast.LENGTH_SHORT).show()
                return
            }
            sendWhatsAppToCustomer(validCustomers[index])
            index++
            handler.postDelayed({ openNext() }, 3500)
        }

        AlertDialog.Builder(this)
  .setTitle("إرسال واتساب للكل")
  .setMessage("سيتم فتح ${validCustomers.size} محادثة. أرسل للعميل الأول واضغط رجوع، وسيفتح التالي تلقائياً كل 3.5 ثانية.")
  .setPositiveButton("ابدأ") { _, _ ->
                Toast.makeText(this, "بدء الإرسال...", Toast.LENGTH_SHORT).show()
                openNext()
            }
  .setNegativeButton("إلغاء", null)
  .setOnDismissListener { isSendingWhatsApp = false }
  .show()
    }

    private fun showActivationDialog(force: Boolean) {
        val options = arrayOf("تفعيل بكود", "توليد كود لعميل", "عرض ID الجهاز")
        AlertDialog.Builder(this)
  .setTitle("إدارة التفعيل")
  .setMessage("متبقي: ${licenseManager.getRemainingDays()} يوم")
  .setItems(options) { _, which ->
                when (which) {
                    0 -> showEnterCodeDialog()
                    1 -> showGenerateCodeDialog()
                    2 -> showDeviceIdDialog()
                }
            }
  .setNegativeButton("إغلاق", null)
  .setCancelable(!force)
  .show()
    }

    private fun showEnterCodeDialog() {
        val editText = EditText(this)
        editText.hint = "WD-WXXXX-X"
        AlertDialog.Builder(this)
  .setTitle("تفعيل التطبيق")
  .setView(editText)
  .setPositiveButton("تفعيل") { _, _ ->
                val code = editText.text.toString().trim().uppercase()
                val result = licenseManager.activateWithCode(code)
                Toast.makeText(this, result, Toast.LENGTH_LONG).show()
                checkActivation()
            }
  .setNegativeButton("إلغاء", null)
  .show()
    }

    private fun showGenerateCodeDialog() {
        val editText = EditText(this)
        editText.hint = "ID جهاز العميل"
        AlertDialog.Builder(this)
  .setTitle("توليد كود تفعيل")
  .setMessage("الصق ID جهاز العميل هنا")
  .setView(editText)
  .setPositiveButton("أسبوع") { _, _ -> generateAndCopyCode(editText.text.toString(), 'W') }
  .setNeutralButton("شهر") { _, _ -> generateAndCopyCode(editText.text.toString(), 'M') }
  .setNegativeButton("سنة") { _, _ -> generateAndCopyCode(editText.text.toString(), 'Y') }
  .show()
    }

    private fun generateAndCopyCode(deviceId: String, type: Char) {
        if (deviceId.isEmpty()) {
            Toast.makeText(this, "ادخل ID الجهاز أولاً", Toast.LENGTH_SHORT).show()
            return
        }
        val code = licenseManager.generateCodeForDevice(deviceId.trim(), type)
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("كود التفعيل", code)
        clipboard.setPrimaryClip(clip)

        val typeText = when (type) { 'W' -> "أسبوع"; 'M' -> "شهر"; 'Y' -> "سنة"; else -> "" }
        AlertDialog.Builder(this)
  .setTitle("تم توليد الكود")
  .setMessage("الكود لـ $typeText:\n\n$code\n\nتم نسخه للحافظة")
  .setPositiveButton("تمام", null)
  .show()
    }

    private fun showDeviceIdDialog() {
        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Device ID", deviceId)
        clipboard.setPrimaryClip(clip)
        AlertDialog.Builder(this)
  .setTitle("ID هذا الجهاز")
  .setMessage("$deviceId\n\nتم نسخه للحافظة")
  .setPositiveButton("تمام", null)
  .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
