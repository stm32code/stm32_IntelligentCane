package com.example.smartcutchgps.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.smartcutchgps.R
import com.example.smartcutchgps.dao.Heart

class ListViewAdapter(var context: Context, var list: MutableList<Heart>) : BaseAdapter() {

    override fun getCount(): Int {
        return list.size
    }

    override fun getItem(p0: Int): Any {
        return list[p0]
    }

    override fun getItemId(p0: Int): Long {
        return p0.toLong()
    }

   
    class MyHold {
        lateinit var layout: LinearLayout
        lateinit var heartText: TextView
        lateinit var heartDateTime: TextView
    }
}