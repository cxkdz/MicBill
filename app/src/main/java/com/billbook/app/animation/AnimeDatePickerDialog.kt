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
    private val onDateTimeSelected: (year: Int, month: Int, day: Int, hour: Int, minute: Int) -> Unit
) : Dialog(context, android.R.style.Theme_Translucent_NoTitleBar) {

    private lateinit var datePickerCard: LinearLayout
    private lateinit var tvYearTitle: TextView
    private lateinit var tvMonthTitle: TextView
    private lateinit var rvDays: RecyclerView
    private lateinit var btnPrevYear: ImageView
    private lateinit var btnNextYear: ImageView
    private lateinit var btnPrevMonth: ImageView
    private lateinit var btnNextMonth: ImageView
    private lateinit var btnToday: TextView
    private lateinit var btnCancel: TextView
    private lateinit var btnConfirm: TextView

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
        loadDays()

        playEntryAnimation()
    }

    private fun initViews() {
        datePickerCard = findViewById(R.id.datePickerCard)
        tvYearTitle = findViewById(R.id.tvYearTitle)
        tvMonthTitle = findViewById(R.id.tvMonthTitle)
        rvDays = findViewById(R.id.rvDays)
        btnPrevYear = findViewById(R.id.btnPrevYear)
        btnNextYear = findViewById(R.id.btnNextYear)
        btnPrevMonth = findViewById(R.id.btnPrevMonth)
        btnNextMonth = findViewById(R.id.btnNextMonth)
        btnToday = findViewById(R.id.btnToday)
        btnCancel = findViewById(R.id.btnCancel)
        btnConfirm = findViewById(R.id.btnConfirm)
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
            cal.set(selectedYear, selectedMonth, 1)
            updateTitles()
            loadDays()
            dayAdapter.setSelectedDay(selectedDay)
        }

        btnCancel.setOnClickListener {
            playExitAnimation { dismiss() }
        }

        btnConfirm.setOnClickListener {
            showTimePicker()
        }
    }

    private fun showTimePicker() {
        android.app.TimePickerDialog(context, { _, hour, minute ->
            selectedHour = hour
            selectedMinute = minute
            onDateTimeSelected(selectedYear, selectedMonth, selectedDay, selectedHour, selectedMinute)
            playExitAnimation { dismiss() }
        }, selectedHour, selectedMinute, true).show()
    }

    private fun updateTitles() {
        tvYearTitle.text = "${selectedYear}年"
        val monthNames = arrayOf("一月", "二月", "三月", "四月", "五月", "六月",
            "七月", "八月", "九月", "十月", "十一月", "十二月")
        tvMonthTitle.text = monthNames[selectedMonth]
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
