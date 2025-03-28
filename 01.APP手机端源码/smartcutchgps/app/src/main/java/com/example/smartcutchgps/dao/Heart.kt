package com.example.smartcutchgps.dao

import com.example.smartcutchgps.utils.TimeCycle

data class Heart(
    var dataTime: String = TimeCycle.getDateTime(),
    var heart: Int,//心率
    var isWaring: Int,//异常数据
    var id: Int? = null
)
