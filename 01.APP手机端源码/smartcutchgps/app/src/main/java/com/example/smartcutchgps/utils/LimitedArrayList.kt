package com.example.smartcutchgps.utils


/***
 * 只会放入指定长度
 */
class LimitedArrayList <T>(private val maxSize: Int) : ArrayList<T>() {
    override fun add(element: T): Boolean {
        val added = super.add(element)
        if (size > maxSize) {
            removeAt(0)
        }
        return added
    }
}