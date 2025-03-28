package com.example.smartcutchgps

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ListView
import android.widget.TextView
import com.example.smartcutchgps.adapter.ListViewAdapter
import com.example.smartcutchgps.dao.Heart
import com.example.smartcutchgps.dao.HeartDao
import com.example.smartcutchgps.databinding.ActivityRecordBinding
import com.example.smartcutchgps.utils.MToast
import com.example.smartcutchgps.utils.TimeCycle
import com.example.smartcutchgps.utils.TimeUtils
import java.util.Calendar
import kotlin.math.abs

class RecordActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRecordBinding
    private lateinit var dao: HeartDao
    private var list: MutableList<Heart>? = null
    private var adapter: ListViewAdapter? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecordBinding.inflate(layoutInflater)
        setContentView(binding.root)
        dao = HeartDao(this)
        initView()
    }

    /**
     * 初始化视图
     */
    private fun initView() {
        binding.endTime.text = TimeCycle.getDateTime()
        list = dao.query("", "", "")
        initListView(list)
        eventManager()
    }

    /***
     * 初始化listview
     */
    private fun initListView(list: MutableList<Heart>?) {
        if (list != null) {
            adapter = ListViewAdapter(this, list)
            binding.listView.adapter = adapter
            if (list.size > 0) {
                binding.listDataNull.visibility = View.GONE
                binding.listView.visibility = View.VISIBLE
            } else {
                binding.listDataNull.visibility = View.VISIBLE
                binding.listView.visibility = View.GONE
            }
        } else {
            MToast.mToast(this, "数据库加载异常")
        }
    }

    /**
     * 监听事件
     */
    private fun eventManager() {
        binding.backBtn.setOnClickListener {
            finish()
        }
        binding.refreshBtn.setOnClickListener {
            list = dao.query("", "", "")
            initListView(list)
            MToast.mToast(this, "刷新数据")
        }
        binding.startTime.setOnClickListener {
            showDateTimeDialog(binding.startTime, true)
        }
        binding.endTime.setOnClickListener {
            showDateTimeDialog(binding.endTime, true)
        }
        binding.isWaring.setOnCheckedChangeListener { _, b ->
            list = if (binding.startTime.text == "开始时间") {
                dao.query("", "", if (binding.isWaring.isChecked) "1" else "")
            } else {
                dao.query(
                    binding.startTime.text.toString(),
                    binding.endTime.text.toString(),
                    if (binding.isWaring.isChecked) "1" else ""
                )
            }
            initListView(list)
        }
    }

    /**
     * 显示日期弹窗
     * @param view TextView
     * @param setMax 是否设置日期最大值为当前
     */
    private fun showDateTimeDialog(view: TextView, setMax: Boolean) {
        //获取当前系统时间
        val currentTime = Calendar.getInstance()
        val datePickerDialog = DatePickerDialog(
            this,
            { _, year, month, day ->
                TimePickerDialog(
                    this, { _, hourOfDay, minute ->
                        view.text = String.format(
                            "%4d-%02d-%02d %02d:%02d:00", year, month+1, day, hourOfDay, minute
                        )
                        if (view == binding.endTime && binding.startTime.text != "开始时间") {
                            if (TimeUtils.compareDateTime(
                                    binding.endTime.text.toString(),
                                    binding.startTime.text.toString()
                                ) > 0
                            ) {
                                list = dao.query(
                                    binding.startTime.text.toString(),
                                    binding.endTime.text.toString(),
                                    if (binding.isWaring.isChecked) "1" else ""
                                )
                                initListView(list)
                            } else {
                                MToast.mToast(this, "结束时间必须大于开始时间")
                            }
                        }
                    }, currentTime.get(Calendar.HOUR_OF_DAY), currentTime.get(Calendar.MINUTE), true
                ).show()
            },
            currentTime.get(Calendar.YEAR),
            currentTime.get(Calendar.MONTH + 1),
            currentTime.get(Calendar.DAY_OF_MONTH)
        )
        if (setMax) {
            // 设置最大日期值为当前日期
            datePickerDialog.datePicker.maxDate = currentTime.timeInMillis
        }
        datePickerDialog.show()

    }
}