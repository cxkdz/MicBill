package com.billbook.app.animation

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.billbook.app.R
import com.billbook.app.adapter.DateDayAdapter
import java.util.Calendar

class AnimeDatePickerDialog(
    context: Context,
    private val initialTimeMillis: Long = System.currentTimeMillis(),
    private val showDayToggle: Boolean = false,
    private val onDateTimeSelected: (year: Int, month: Int, day: Int, hour: Int, minute: Int) -> Unit
) : Dialog(context, android.R.style.Theme_Translucent_NoTitleBar) {

    private lateinit var datePickerCard: LinearLayout
    private lateinit var tvYearTitle: TextView
    private lateinit var tvMonthTitle: TextView
    private lateinit var rvDays: RecyclerView
    private lateinit var weekdayRow: LinearLayout
    private lateinit var btnPrevYear: ImageView
    private lateinit var btnNextYear: ImageView
    private lateinit var btnPrevMonth: ImageView
    private lateinit var btnNextMonth: ImageView
    private lateinit var btnToday: TextView
    private lateinit var btnCancel: TextView
    private lateinit var btnConfirm: TextView
    private lateinit var switchDayPrecision: android.widget.CheckBox
    private lateinit var layoutTimePicker: LinearLayout

    // 时间选择器
    private lateinit var btnPrevHour: ImageView
    private lateinit var btnNextHour: ImageView
    private lateinit var tvHour: TextView
    private lateinit var btnPrevMinute: ImageView
    private lateinit var btnNextMinute: ImageView
    private lateinit var tvMinute: TextView

    private lateinit var dayAdapter: DateDayAdapter

    private val cal = Calendar.getInstance()
    private var selectedYear: Int = 0
    private var selectedMonth: Int = 0
    private var selectedDay: Int = 0
    private var selectedHour: Int = 0
    private var selectedMinute: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_anime_date_picker)

        cal.timeInMillis = initialTimeMillis
        selectedYear = cal.get(Calendar.YEAR)
        selectedMonth = cal.get(Calendar.MONTH)
        selectedDay = cal.get(Calendar.DAY_OF_MONTH)
        selectedHour = cal.get(Calendar.HOUR_OF_DAY)
        selectedMinute = cal.get(Calendar.MINUTE)

        initViews()
        setupRecyclerView()
        setupListeners()
        updateTitles()
        updateTimeDisplay()
        loadDays()

        layoutTimePicker.visibility = View.GONE
        if (showDayToggle) {
            switchDayPrecision.setOnCheckedChangeListener { _, isChecked ->
                val vis = if (isChecked) View.VISIBLE else View.GONE
                weekdayRow.visibility = vis
                rvDays.visibility = vis
            }
            switchDayPrecision.isChecked = false
            weekdayRow.visibility = View.GONE
            rvDays.visibility = View.GONE
        } else {
            switchDayPrecision.visibility = View.GONE
        }

        playEntryAnimation()
    }

    private fun initViews() {
        datePickerCard = findViewById(R.id.datePickerCard)
        tvYearTitle = findViewById(R.id.tvYearTitle)
        tvMonthTitle = findViewById(R.id.tvMonthTitle)
        rvDays = findViewById(R.id.rvDays)
        weekdayRow = findViewById(R.id.weekdayRow)
        btnPrevYear = findViewById(R.id.btnPrevYear)
        btnNextYear = findViewById(R.id.btnNextYear)
        btnPrevMonth = findViewById(R.id.btnPrevMonth)
        btnNextMonth = findViewById(R.id.btnNextMonth)
        btnToday = findViewById(R.id.btnToday)
        btnCancel = findViewById(R.id.btnCancel)
        switchDayPrecision = findViewById(R.id.switchDayPrecision)
        layoutTimePicker = findViewById(R.id.layoutTimePicker)
        btnConfirm = findViewById(R.id.btnConfirm)

        btnPrevHour = findViewById(R.id.btnPrevHour)
        btnNextHour = findViewById(R.id.btnNextHour)
        tvHour = findViewById(R.id.tvHour)
        btnPrevMinute = findViewById(R.id.btnPrevMinute)
        btnNextMinute = findViewById(R.id.btnNextMinute)
        tvMinute = findViewById(R.id.tvMinute)
    }

    private fun setupRecyclerView() {
        dayAdapter = DateDayAdapter { day ->
            selectedDay = day
        }
        rvDays.layoutManager = GridLayoutManager(context, 7)
        rvDays.adapter = dayAdapter
    }

    private fun setupListeners() {
        btnPrevYear.setOnClickListener {
            animateNavClick(it) {
                cal.add(Calendar.YEAR, -1)
                selectedYear = cal.get(Calendar.YEAR)
                updateTitles()
                loadDays()
            }
        }

        btnNextYear.setOnClickListener {
            animateNavClick(it) {
                cal.add(Calendar.YEAR, 1)
                selectedYear = cal.get(Calendar.YEAR)
                updateTitles()
                loadDays()
            }
        }

        btnPrevMonth.setOnClickListener {
            animateNavClick(it) {
                cal.add(Calendar.MONTH, -1)
                selectedYear = cal.get(Calendar.YEAR)
                selectedMonth = cal.get(Calendar.MONTH)
                updateTitles()
                loadDays()
            }
        }

        btnNextMonth.setOnClickListener {
            animateNavClick(it) {
                cal.add(Calendar.MONTH, 1)
                selectedYear = cal.get(Calendar.YEAR)
                selectedMonth = cal.get(Calendar.MONTH)
                updateTitles()
                loadDays()
            }
        }

        btnToday.setOnClickListener {
            val today = Calendar.getInstance()
            selectedYear = today.get(Calendar.YEAR)
            selectedMonth = today.get(Calendar.MONTH)
            selectedDay = today.get(Calendar.DAY_OF_MONTH)
            selectedHour = today.get(Calendar.HOUR_OF_DAY)
            selectedMinute = today.get(Calendar.MINUTE)
            cal.set(selectedYear, selectedMonth, 1)
            updateTitles()
            updateTimeDisplay()
            loadDays()
            dayAdapter.setSelectedDay(selectedDay)
        }

        // 小时 -1
        btnPrevHour.setOnClickListener {
            animateNavClick(it) {
                selectedHour = if (selectedHour <= 0) 23 else selectedHour - 1
                updateTimeDisplay()
            }
        }

        // 小时 +1
        btnNextHour.setOnClickListener {
            animateNavClick(it) {
                selectedHour = if (selectedHour >= 23) 0 else selectedHour + 1
                updateTimeDisplay()
            }
        }

        // 分钟 -1
        btnPrevMinute.setOnClickListener {
            animateNavClick(it) {
                selectedMinute = if (selectedMinute <= 0) 59 else selectedMinute - 1
                updateTimeDisplay()
            }
        }

        // 分钟 +1
        btnNextMinute.setOnClickListener {
            animateNavClick(it) {
                selectedMinute = if (selectedMinute >= 59) 0 else selectedMinute + 1
                updateTimeDisplay()
            }
        }

        btnCancel.setOnClickListener {
            playExitAnimation { dismiss() }
        }

        btnConfirm.setOnClickListener {
            if (selectedDay <= 0) {
                selectedDay = 1
            }
            onDateTimeSelected(selectedYear, selectedMonth, selectedDay, selectedHour, selectedMinute)
            playExitAnimation { dismiss() }
        }
    }

    private fun updateTitles() {
        tvYearTitle.text = "${selectedYear}年"
        val monthNames = arrayOf("一月", "二月", "三月", "四月", "五月", "六月",
            "七月", "八月", "九月", "十月", "十一月", "十二月")
        tvMonthTitle.text = monthNames[selectedMonth]
    }

    private fun updateTimeDisplay() {
        tvHour.text = String.format("%02d", selectedHour)
        tvMinute.text = String.format("%02d", selectedMinute)
    }

    private fun loadDays() {
        dayAdapter.setMonth(selectedYear, selectedMonth)
        if (selectedDay > 0 &&
            cal.get(Calendar.YEAR) == selectedYear &&
            cal.get(Calendar.MONTH) == selectedMonth
        ) {
            dayAdapter.setSelectedDay(selectedDay)
        }
    }

    private fun animateNavClick(view: View, action: () -> Unit) {
        view.animate()
            .scaleX(0.8f).scaleY(0.8f)
            .setDuration(80)
            .withEndAction {
                view.animate()
                    .scaleX(1f).scaleY(1f)
                    .setDuration(120)
                    .setInterpolator(OvershootInterpolator(2f))
                    .start()
                action()
            }
            .start()
    }

    private fun playEntryAnimation() {
        datePickerCard.scaleX = 0.3f
        datePickerCard.scaleY = 0.3f
        datePickerCard.alpha = 0f

        val scaleUpX = ObjectAnimator.ofFloat(datePickerCard, "scaleX", 0.3f, 1f)
        val scaleUpY = ObjectAnimator.ofFloat(datePickerCard, "scaleY", 0.3f, 1f)
        val fadeIn = ObjectAnimator.ofFloat(datePickerCard, "alpha", 0f, 1f)

        AnimatorSet().apply {
            playTogether(scaleUpX, scaleUpY, fadeIn)
            duration = 300
            interpolator = OvershootInterpolator(1.2f)
            start()
        }
    }

    private fun playExitAnimation(onEnd: () -> Unit) {
        val scaleDownX = ObjectAnimator.ofFloat(datePickerCard, "scaleX", 1f, 0.3f)
        val scaleDownY = ObjectAnimator.ofFloat(datePickerCard, "scaleY", 1f, 0.3f)
        val fadeOut = ObjectAnimator.ofFloat(datePickerCard, "alpha", 1f, 0f)

        AnimatorSet().apply {
            playTogether(scaleDownX, scaleDownY, fadeOut)
            duration = 200
            interpolator = DecelerateInterpolator()
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    onEnd()
                }
            })
            start()
        }
    }
}
