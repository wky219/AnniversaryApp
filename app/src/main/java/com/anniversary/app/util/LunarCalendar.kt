package com.anniversary.app.util

import java.util.*

/**
 * 农历日历工具类
 * 使用经典数据表算法，覆盖 1900-2100 年
 * 支持阳历↔农历互转、农历日期格式化
 */
object LunarCalendar {

    // 农历数据表 1900-2100 年
    // 每个long值编码了该年的农历信息：
    // Bit 0-3: 闰月月份(0=无闰月)
    // Bit 4-15: 1-12月天数(1=30天, 0=29天)，通过 0x10000>>month 访问
    //     month=1对应bit15, month=12对应bit4
    // Bit 16: 闰月天数(1=30天, 0=29天)
    private val LUNAR_INFO = longArrayOf(
        0x04bd8, 0x04ae0, 0x0a570, 0x054d5, 0x0d260, 0x0d950, 0x16554, 0x056a0, 0x09ad0, 0x055d2, // 1900-1909
        0x04ae0, 0x0a5b6, 0x0a4d0, 0x0d250, 0x1d255, 0x0b540, 0x0d6a0, 0x0ada2, 0x095b0, 0x14977, // 1910-1919
        0x04970, 0x0a4b0, 0x0b4b5, 0x06a50, 0x06d40, 0x1ab54, 0x02b60, 0x09570, 0x052f2, 0x04970, // 1920-1929
        0x06566, 0x0d4a0, 0x0ea50, 0x06e95, 0x05ad0, 0x02b60, 0x186e3, 0x092e0, 0x1c8d7, 0x0c950, // 1930-1939
        0x0d4a0, 0x1d8a6, 0x0b550, 0x056a0, 0x1a5b4, 0x025d0, 0x092d0, 0x0d2b2, 0x0a950, 0x0b557, // 1940-1949
        0x06ca0, 0x0b550, 0x15355, 0x04da0, 0x0a5b0, 0x14573, 0x052b0, 0x0a9a8, 0x0e950, 0x06aa0, // 1950-1959
        0x0aea6, 0x0ab50, 0x04b60, 0x0aae4, 0x0a570, 0x05260, 0x0f263, 0x0d950, 0x05b57, 0x056a0, // 1960-1969
        0x096d0, 0x04dd5, 0x04ad0, 0x0a4d0, 0x0d4d4, 0x0d250, 0x0d558, 0x0b540, 0x0b6a0, 0x195a6, // 1970-1979
        0x095b0, 0x049b0, 0x0a974, 0x0a4b0, 0x0b27a, 0x06a50, 0x06d40, 0x0af46, 0x0ab60, 0x09570, // 1980-1989
        0x04af5, 0x04970, 0x064b0, 0x074a3, 0x0ea50, 0x06b58, 0x055c0, 0x0ab60, 0x096d5, 0x092e0, // 1990-1999
        0x0c960, 0x0d954, 0x0d4a0, 0x0da50, 0x07552, 0x056a0, 0x0abb7, 0x025d0, 0x092d0, 0x0cab5, // 2000-2009
        0x0a950, 0x0b4a0, 0x0baa4, 0x0ad50, 0x055d9, 0x04ba0, 0x0a5b0, 0x15176, 0x052b0, 0x0a930, // 2010-2019
        0x07954, 0x06aa0, 0x0ad50, 0x05b52, 0x04b60, 0x0a6e6, 0x0a4e0, 0x0d260, 0x0ea65, 0x0d530, // 2020-2029
        0x05aa0, 0x076a3, 0x096d0, 0x04afb, 0x04ad0, 0x0a4d0, 0x1d0b6, 0x0d250, 0x0d520, 0x0dd45, // 2030-2039
        0x0b5a0, 0x056d0, 0x055b2, 0x049b0, 0x0a577, 0x0a4b0, 0x0aa50, 0x1b255, 0x06d20, 0x0ada0, // 2040-2049
        0x14b63, 0x09370, 0x049f8, 0x04970, 0x064b0, 0x168a6, 0x0ea50, 0x06b20, 0x1a6c4, 0x0aae0, // 2050-2059
        0x0a2e0, 0x0d2e3, 0x0c960, 0x0d557, 0x0d4a0, 0x0da50, 0x05d55, 0x056a0, 0x0a6d0, 0x055d4, // 2060-2069
        0x052d0, 0x0a9b8, 0x0a950, 0x0b4a0, 0x0b6a6, 0x0ad50, 0x055a0, 0x0aba4, 0x0a5b0, 0x052b0, // 2070-2079
        0x0b273, 0x06930, 0x07337, 0x06aa0, 0x0ad50, 0x14b55, 0x04b60, 0x0a570, 0x054e4, 0x0d160, // 2080-2089
        0x0e968, 0x0d520, 0x0daa0, 0x16aa6, 0x056d0, 0x04ae0, 0x0a9d4, 0x0a2d0, 0x0d150, 0x0f252, // 2090-2099
        0x0d520  // 2100
    )

    // 1900年1月31日是农历1900年正月初一
    private const val LUNAR_START_YEAR = 1900
    private val LUNAR_START_DATE = Calendar.getInstance().apply {
        set(1900, Calendar.JANUARY, 31, 0, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }

    // 农历月份名称
    private val LUNAR_MONTH_NAMES = arrayOf(
        "", "正", "二", "三", "四", "五", "六", "七", "八", "九", "十", "冬", "腊"
    )

    // 农历日名称
    private val LUNAR_DAY_NAMES = arrayOf(
        "",
        "初一", "初二", "初三", "初四", "初五",
        "初六", "初七", "初八", "初九", "初十",
        "十一", "十二", "十三", "十四", "十五",
        "十六", "十七", "十八", "十九", "二十",
        "廿一", "廿二", "廿三", "廿四", "廿五",
        "廿六", "廿七", "廿八", "廿九", "三十"
    )

    // 天干
    private val TEN_GAN = arrayOf("甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸")
    // 地支
    private val TWELVE_ZHI = arrayOf("子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥")
    // 生肖
    private val ANIMALS = arrayOf("鼠", "牛", "虎", "兔", "龙", "蛇", "马", "羊", "猴", "鸡", "狗", "猪")

    /**
     * 农历日期数据类
     */
    data class LunarDate(
        val lunarYear: Int,
        val lunarMonth: Int,
        val lunarDay: Int,
        val isLeapMonth: Boolean,
        val leapMonth: Int // 该年闰月月份，0表示无闰月
    )

    /**
     * 阳历转农历
     */
    fun solarToLunar(year: Int, month: Int, day: Int): LunarDate {
        val calendar = Calendar.getInstance().apply {
            set(year, month - 1, day, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // 计算与农历1900年正月初一的偏移天数
        val offset = daysBetween(LUNAR_START_DATE, calendar)

        // 确定农历年
        var lunarYear = LUNAR_START_YEAR
        var temp: Long = offset.toLong()
        while (temp >= lunarYearDays(lunarYear)) {
            temp -= lunarYearDays(lunarYear)
            lunarYear++
        }

        // temp 现在是农历年内的偏移量
        return calculateLunarMonthAndDay(lunarYear, temp.toInt())
    }

    /**
     * 精确计算农历月和日
     */
    private fun calculateLunarMonthAndDay(lunarYear: Int, totalOffset: Int): LunarDate {
        val leapMon = leapMonth(lunarYear)
        var offset = totalOffset
        var lunarMonth = 1
        var isLeapMonth = false

        // 遍历每个月
        var currentMonth = 1
        while (currentMonth <= 12) {
            // 正常月
            val normalDays = monthDays(lunarYear, currentMonth)
            if (offset < normalDays) {
                lunarMonth = currentMonth
                isLeapMonth = false
                return LunarDate(lunarYear, lunarMonth, offset + 1, isLeapMonth, leapMon)
            }
            offset -= normalDays

            // 如果该月后是闰月
            if (currentMonth == leapMon) {
                val leapDays = leapMonthDays(lunarYear)
                if (offset < leapDays) {
                    lunarMonth = currentMonth
                    isLeapMonth = true
                    return LunarDate(lunarYear, lunarMonth, offset + 1, isLeapMonth, leapMon)
                }
                offset -= leapDays
            }

            currentMonth++
        }

        // 不应该到这里，返回安全值
        return LunarDate(lunarYear, 12, offset + 1, false, leapMon)
    }

    /**
     * 农历转阳历
     */
    fun lunarToSolar(lunarYear: Int, lunarMonth: Int, lunarDay: Int, isLeapMonth: Boolean): IntArray {
        val offset = calculateOffset(lunarYear, lunarMonth, lunarDay, isLeapMonth)

        val result = Calendar.getInstance().apply {
            timeInMillis = LUNAR_START_DATE.timeInMillis
        }
        result.add(Calendar.DAY_OF_YEAR, offset.toInt())

        return intArrayOf(
            result.get(Calendar.YEAR),
            result.get(Calendar.MONTH) + 1,
            result.get(Calendar.DAY_OF_MONTH)
        )
    }

    /**
     * 计算农历日期到1900年正月初一的天数偏移
     */
    private fun calculateOffset(lunarYear: Int, lunarMonth: Int, lunarDay: Int, isLeapMonth: Boolean): Long {
        var offset = 0L

        // 累加年
        for (y in LUNAR_START_YEAR until lunarYear) {
            offset += lunarYearDays(y)
        }

        // 累加月
        val leapMon = leapMonth(lunarYear)
        var currentMonth = 1

        while (currentMonth < lunarMonth) {
            offset += monthDays(lunarYear, currentMonth)

            // 如果当前月后面跟着闰月，且已经过了当前月，需要加上闰月
            if (currentMonth == leapMon) {
                offset += leapMonthDays(lunarYear)
            }
            currentMonth++
        }

        // 如果目标是闰月，需要加上正常月的天数（因为闰月紧跟在正常月之后）
        // 注意：如果目标年不存在该闰月（leapMon != lunarMonth），条件不成立，
        // 此时回退到普通月日期（中国农历传统惯例：无闰月时在普通月过）
        if (isLeapMonth && lunarMonth == leapMon) {
            offset += monthDays(lunarYear, lunarMonth)
        }

        // 加上日偏移
        offset += (lunarDay - 1)

        return offset
    }

    /**
     * 获取农历年的总天数
     */
    fun lunarYearDays(year: Int): Long {
        var sum = 348L // 12个月 × 29天 = 348
        val info = LUNAR_INFO[year - LUNAR_START_YEAR]

        // 逐月加上大月多出的1天（位4到位15，对应12月到1月）
        var i = 0x8000
        while (i >= 0x10) {
            if ((info.toInt() and i) != 0) sum++
            i = i shr 1
        }

        // 加上闰月天数
        sum += leapMonthDays(year).toLong()
        return sum
    }

    /**
     * 获取闰月的天数
     */
    fun leapMonthDays(year: Int): Int {
        if (leapMonth(year) == 0) return 0
        val info = LUNAR_INFO[year - LUNAR_START_YEAR]
        return if ((info.toInt() and 0x10000) != 0) 30 else 29
    }

    /**
     * 获取闰月月份，0表示无闰月
     */
    fun leapMonth(year: Int): Int {
        val info = LUNAR_INFO[year - LUNAR_START_YEAR]
        return info.toInt() and 0xf
    }

    /**
     * 获取正常月的天数
     */
    fun monthDays(year: Int, month: Int): Int {
        val info = LUNAR_INFO[year - LUNAR_START_YEAR]
        return if ((info.toInt() and (0x10000 shr month)) != 0) 30 else 29
    }

    /**
     * 获取指定月的天数（考虑是否为闰月）
     */
    fun lunarMonthDays(year: Int, month: Int, isLeap: Boolean): Int {
        return if (isLeap) leapMonthDays(year) else monthDays(year, month)
    }

    /**
     * 格式化农历日期字符串，如"正月初一"
     */
    fun formatLunarDate(lunarMonth: Int, lunarDay: Int, isLeapMonth: Boolean): String {
        val monthStr = if (isLeapMonth) "闰${LUNAR_MONTH_NAMES[lunarMonth]}" else LUNAR_MONTH_NAMES[lunarMonth]
        return "${monthStr}月${LUNAR_DAY_NAMES[lunarDay]}"
    }

    /**
     * 格式化完整农历日期，如"甲子年正月初一"
     */
    fun formatLunarDateFull(lunarYear: Int, lunarMonth: Int, lunarDay: Int, isLeapMonth: Boolean): String {
        val ganZhi = getGanZhiYear(lunarYear)
        val dateStr = formatLunarDate(lunarMonth, lunarDay, isLeapMonth)
        return "${ganZhi}年$dateStr"
    }

    /**
     * 获取天干地支年名
     */
    fun getGanZhiYear(lunarYear: Int): String {
        val ganIndex = (lunarYear - 4) % 10
        val zhiIndex = (lunarYear - 4) % 12
        return TEN_GAN[ganIndex] + TWELVE_ZHI[zhiIndex]
    }

    /**
     * 获取生肖
     */
    fun getAnimal(lunarYear: Int): String {
        return ANIMALS[(lunarYear - 4) % 12]
    }

    /**
     * 计算两个日期之间的天数
     */
    private fun daysBetween(start: Calendar, end: Calendar): Int {
        val startDay = Calendar.getInstance().apply {
            timeInMillis = start.timeInMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val endDay = Calendar.getInstance().apply {
            timeInMillis = end.timeInMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val diff = endDay.timeInMillis - startDay.timeInMillis
        return (diff / (24 * 60 * 60 * 1000)).toInt()
    }

    /**
     * 阳历时间戳转农历
     */
    fun solarToLunar(timestamp: Long): LunarDate {
        val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
        return solarToLunar(
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    /**
     * 农历转阳历时间戳
     */
    fun lunarToSolarTimestamp(lunarYear: Int, lunarMonth: Int, lunarDay: Int, isLeapMonth: Boolean): Long {
        val solar = lunarToSolar(lunarYear, lunarMonth, lunarDay, isLeapMonth)
        return Calendar.getInstance().apply {
            set(solar[0], solar[1] - 1, solar[2], 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    /**
     * 获取农历某年在阳历中的下一个出现日期
     * 例如：农历正月十五，在今年的阳历日期；如果今年已过，则返回明年的阳历日期
     *
     * 注意：如果指定的闰月在目标年不存在，将回退到普通月日期（中国农历传统惯例）
     *
     * @param lunarMonth 农历月
     * @param lunarDay 农历日
     * @param isLeapMonth 是否闰月
     */
    fun getNextLunarOccurrence(lunarMonth: Int, lunarDay: Int, isLeapMonth: Boolean): Long {
        val now = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // 获取当前阳历年对应的农历年
        val currentLunar = solarToLunar(now.timeInMillis)
        val currentLunarYear = currentLunar.lunarYear

        // 尝试今年的农历日期对应的阳历日期
        val thisYearSolar = lunarToSolarTimestamp(currentLunarYear, lunarMonth, lunarDay, isLeapMonth)

        if (thisYearSolar >= now.timeInMillis) {
            return thisYearSolar
        }

        // 今年已过，尝试明年
        val nextYearSolar = lunarToSolarTimestamp(currentLunarYear + 1, lunarMonth, lunarDay, isLeapMonth)

        return nextYearSolar
    }

    /**
     * 获取农历年份间隔（周年计算）
     * 会检查今年纪念日是否已过，未过则少算一年
     *
     * @param originalLunarYear 原始农历年
     * @param lunarMonth 纪念日农历月
     * @param lunarDay 纪念日农历日
     * @param isLeapMonth 纪念日是否为闰月
     */
    fun getLunarYearsSince(originalLunarYear: Int, lunarMonth: Int, lunarDay: Int, isLeapMonth: Boolean): Int {
        val now = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val currentLunar = solarToLunar(now.timeInMillis)
        var years = currentLunar.lunarYear - originalLunarYear

        // 检查今年纪念日是否已过
        if (years > 0) {
            val thisYearOccurrence = lunarToSolarTimestamp(
                currentLunar.lunarYear, lunarMonth, lunarDay, isLeapMonth
            )
            val thisYearCal = Calendar.getInstance().apply {
                timeInMillis = thisYearOccurrence
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            // 如果今年纪念日的阳历日期还未到，少算一年
            if (thisYearCal.after(now)) {
                years--
            }
        }

        return years
    }

    /**
     * 获取指定年份范围内的所有可选农历月
     * 返回月份列表（1-12，加上闰月标记）
     */
    fun getLunarMonths(lunarYear: Int): List<Pair<Int, Boolean>> {
        val months = mutableListOf<Pair<Int, Boolean>>()
        val leapMon = leapMonth(lunarYear)
        for (m in 1..12) {
            months.add(m to false)
            if (m == leapMon) {
                months.add(m to true) // 闰月
            }
        }
        return months
    }

    /**
     * 获取指定农历月的天数
     */
    fun getLunarMonthDayCount(lunarYear: Int, lunarMonth: Int, isLeapMonth: Boolean): Int {
        return if (isLeapMonth) leapMonthDays(lunarYear) else monthDays(lunarYear, lunarMonth)
    }
}
