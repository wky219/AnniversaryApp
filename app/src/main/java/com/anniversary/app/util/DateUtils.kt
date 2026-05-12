package com.anniversary.app.util

import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

object DateUtils {

    private val dateFormat = SimpleDateFormat("yyyy年MM月dd日", Locale.CHINA)

    fun formatDate(timestamp: Long): String {
        return dateFormat.format(Date(timestamp))
    }

    fun getDaysUntil(timestamp: Long): Long {
        val today = getStartOfDay(System.currentTimeMillis())
        val target = getStartOfDay(timestamp)
        return TimeUnit.MILLISECONDS.toDays(target - today)
    }

    fun getDaysSince(timestamp: Long): Long {
        val today = getStartOfDay(System.currentTimeMillis())
        val target = getStartOfDay(timestamp)
        return TimeUnit.MILLISECONDS.toDays(today - target)
    }

    fun getNextOccurrence(timestamp: Long): Long {
        val calendar = Calendar.getInstance()
        val targetCal = Calendar.getInstance().apply { timeInMillis = timestamp }

        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        val nextCal = Calendar.getInstance().apply {
            set(Calendar.YEAR, calendar.get(Calendar.YEAR))
            set(Calendar.MONTH, targetCal.get(Calendar.MONTH))
            set(Calendar.DAY_OF_MONTH, targetCal.get(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (nextCal.before(calendar)) {
            nextCal.add(Calendar.YEAR, 1)
        }

        return nextCal.timeInMillis
    }

    /**
     * 获取农历日期的下次出现时间（阳历时间戳）
     * @param lunarMonth 农历月
     * @param lunarDay 农历日
     * @param isLeapMonth 是否闰月
     */
    fun getNextLunarOccurrence(lunarMonth: Int, lunarDay: Int, isLeapMonth: Boolean): Long {
        return LunarCalendar.getNextLunarOccurrence(lunarMonth, lunarDay, isLeapMonth)
    }

    fun getCountdownText(timestamp: Long, isRepeatYearly: Boolean): String {
        val days = if (isRepeatYearly) {
            val nextOccurrence = getNextOccurrence(timestamp)
            getDaysUntil(nextOccurrence)
        } else {
            getDaysUntil(timestamp)
        }

        return when {
            days > 0 -> "还有 ${days} 天"
            days == 0L -> "就是今天!"
            else -> "已过去 ${-days} 天"
        }
    }

    /**
     * 获取倒计时文本（支持农历）
     */
    fun getCountdownText(anniversary: com.anniversary.app.data.entity.Anniversary): String {
        val days = if (anniversary.isRepeatYearly) {
            val nextOccurrence = if (anniversary.isLunar) {
                getNextLunarOccurrence(
                    anniversary.lunarMonth,
                    anniversary.lunarDay,
                    anniversary.lunarIsLeapMonth
                )
            } else {
                getNextOccurrence(anniversary.date)
            }
            getDaysUntil(nextOccurrence)
        } else {
            getDaysUntil(anniversary.date)
        }

        return when {
            days > 0 -> "还有 ${days} 天"
            days == 0L -> "就是今天!"
            else -> "已过去 ${-days} 天"
        }
    }

    fun getYearsSince(timestamp: Long): Int {
        val today = Calendar.getInstance()
        val target = Calendar.getInstance().apply { timeInMillis = timestamp }
        var years = today.get(Calendar.YEAR) - target.get(Calendar.YEAR)
        if (today.get(Calendar.DAY_OF_YEAR) < target.get(Calendar.DAY_OF_YEAR)) {
            years--
        }
        return years
    }

    /**
     * 获取农历年份间隔（周年计算）
     * @param timestamp 原始阳历时间戳
     * @param lunarMonth 纪念日农历月
     * @param lunarDay 纪念日农历日
     * @param isLeapMonth 纪念日是否为闰月
     */
    fun getLunarYearsSince(timestamp: Long, lunarMonth: Int, lunarDay: Int, isLeapMonth: Boolean): Int {
        val originalLunar = LunarCalendar.solarToLunar(timestamp)
        return LunarCalendar.getLunarYearsSince(originalLunar.lunarYear, lunarMonth, lunarDay, isLeapMonth)
    }

    /**
     * 格式化日期（支持农历显示）
     * @param anniversary 纪念日对象
     * @return 格式化后的日期字符串
     */
    fun formatDate(anniversary: com.anniversary.app.data.entity.Anniversary): String {
        return if (anniversary.isLunar) {
            val lunarStr = LunarCalendar.formatLunarDate(
                anniversary.lunarMonth,
                anniversary.lunarDay,
                anniversary.lunarIsLeapMonth
            )
            if (anniversary.isRepeatYearly) {
                // 重复年度纪念日：显示下次出现的阳历日期
                val nextSolar = getNextLunarOccurrence(
                    anniversary.lunarMonth,
                    anniversary.lunarDay,
                    anniversary.lunarIsLeapMonth
                )
                val solarStr = formatDate(nextSolar)
                "$lunarStr（$solarStr）"
            } else {
                // 非重复纪念日：显示原始阳历日期
                "$lunarStr（${formatDate(anniversary.date)}）"
            }
        } else {
            formatDate(anniversary.date)
        }
    }

    /**
     * 获取下一次出现的时间戳（支持农历）
     */
    fun getNextOccurrence(anniversary: com.anniversary.app.data.entity.Anniversary): Long {
        return if (anniversary.isLunar && anniversary.isRepeatYearly) {
            getNextLunarOccurrence(
                anniversary.lunarMonth,
                anniversary.lunarDay,
                anniversary.lunarIsLeapMonth
            )
        } else if (anniversary.isRepeatYearly) {
            getNextOccurrence(anniversary.date)
        } else {
            anniversary.date
        }
    }

    /**
     * 获取距今天数（支持农历）
     */
    fun getDaysUntilNext(anniversary: com.anniversary.app.data.entity.Anniversary): Long {
        val nextOccurrence = if (anniversary.isRepeatYearly) {
            if (anniversary.isLunar) {
                getNextLunarOccurrence(
                    anniversary.lunarMonth,
                    anniversary.lunarDay,
                    anniversary.lunarIsLeapMonth
                )
            } else {
                getNextOccurrence(anniversary.date)
            }
        } else {
            anniversary.date
        }
        return getDaysUntil(nextOccurrence)
    }

    private fun getStartOfDay(timestamp: Long): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }
}
