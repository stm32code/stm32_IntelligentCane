package com.example.smartcutchgps.dao

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DBHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, VERSION) {
    companion object {
        private const val DB_NAME: String = "my.db"
        private const val VERSION: Int = 1
    }

    override fun onCreate(p0: SQLiteDatabase?) {
        val sql = "CREATE TABLE m_heart(" +
                "id INTEGER primary key autoincrement," +
                "dataTime varchar(60)," +
                "heart INTEGER," +
                "isWaring INTEGER" +
                ")"
        p0?.execSQL(
            sql
        )

    }

    override fun onUpgrade(p0: SQLiteDatabase?, p1: Int, p2: Int) {

    }
}