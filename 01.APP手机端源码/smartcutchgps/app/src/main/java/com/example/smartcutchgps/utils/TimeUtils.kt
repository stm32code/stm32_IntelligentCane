package com.example.smartcutchgps.utils

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object TimeUtils {

    /***
     * 比较两个"yyyy-MM-dd HH:mm:ss"字符串大小
     * @param dateTime1 String
     * @param dateTime2 String
     * @return 如果返回值小于 0，则表示 dateTime1 较小；如果返回值大于 0，则表示 dateTime1 较大；如果返回值等于 0，则表示两个日期时间相等
     */
    fun compareDateTime(dateTime1: String, dateTime2: String): Int {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val dt1 = LocalDateTime.parse(dateTime1, formatter)
        val dt2 = LocalDateTime.parse(dateTime2, formatter)

        return dt1.compareTo(dt2)
    }
}