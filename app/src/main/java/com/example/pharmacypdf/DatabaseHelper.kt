package com.example.pharmacypdf

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_VERSION = 1
        private const val DATABASE_NAME = "CustomersDB"
        private const val TABLE_CUSTOMERS = "customers"
        private const val KEY_ID = "id"
        private const val KEY_NAME = "name"
        private const val KEY_PHONE = "phone"
        private const val KEY_DEBT = "debt"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = ("CREATE TABLE $TABLE_CUSTOMERS($KEY_ID INTEGER PRIMARY KEY AUTOINCREMENT,$KEY_NAME TEXT,$KEY_PHONE TEXT,$KEY_DEBT REAL)")
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_CUSTOMERS")
        onCreate(db)
    }

    fun addCustomer(customer: Customer) {
        val db = this.writableDatabase
        val values = ContentValues()
        values.put(KEY_NAME, customer.name)
        values.put(KEY_PHONE, customer.phone)
        values.put(KEY_DEBT, customer.debt)
        db.insert(TABLE_CUSTOMERS, null, values)
        db.close()
    }

    fun getAllCustomers(): List<Customer> {
        val customerList = ArrayList<Customer>()
        val selectQuery = "SELECT * FROM $TABLE_CUSTOMERS"
        val db = this.readableDatabase
        val cursor = db.rawQuery(selectQuery, null)
        if (cursor.moveToFirst()) {
            do {
                val customer = Customer(
                    cursor.getInt(0),
                    cursor.getString(1),
                    cursor.getString(2),
                    cursor.getDouble(3)
                )
                customerList.add(customer)
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return customerList
    }

    fun deleteAllCustomers() {
        val db = this.writableDatabase
        db.delete(TABLE_CUSTOMERS, null, null)
        db.close()
    }
}
