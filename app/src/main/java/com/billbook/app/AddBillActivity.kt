package com.billbook.app

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.GridLayoutManager
import com.billbook.app.adapter.CategoryGridAdapter
import com.billbook.app.databinding.ActivityAddBillBinding
import com.billbook.app.db.BillDao
import com.billbook.app.db.CategoryDao
import com.billbook.app.model.Bill
import com.billbook.app.model.Category
import com.billbook.app.util.BackgroundApplier
import com.billbook.app.util.ThemeManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AddBillActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddBillBinding
    private lateinit var billDao: BillDao
    private lateinit var categoryDao: CategoryDao
    private lateinit var themeManager: ThemeManager

    private var billType = Bill.TYPE_EXPENSE
    private var selectedCategoryId = 0L
    private var selectedCategoryName = ""
    private var selectedCategoryIcon = ""
    private var selectedPayType = Bill.PAY_WECHAT
    private var selectedTime = System.currentTimeMillis()
    private var editBillId = 0L // 编辑模式的账单ID

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)

    override fun onCreate(savedInstanceState: Bundle?) {
        themeManager = ThemeManager(this)
        setTheme(themeManager.getCurrentTheme().styleRes)
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        binding = ActivityAddBillBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbar) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.updatePadding(top = insets.top)
            windowInsets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.btnSave) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.updatePadding(bottom = insets.bottom)
            windowInsets
        }

        BackgroundApplier.apply(this)

        billDao = BillDao(this)
        categoryDao = CategoryDao(this)

        editBillId = intent.getLongExtra("bill_id", 0L)

        setupToolbar()
        setupTypeSwitch()
        updateTypeSelection()
        setupCategoryGrid()
        setupPayType()
        setupTimePicker()
        setupSaveButton()

        if (editBillId > 0) {
            loadBillData()
        } else {
            updateTimeDisplay()
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = if (editBillId > 0) "编辑账单" else "记一笔"
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupTypeSwitch() {
        binding.chipExpense.setOnClickListener {
            billType = Bill.TYPE_EXPENSE
            updateTypeSelection()
            loadCategories()
        }
        binding.chipIncome.setOnClickListener {
            billType = Bill.TYPE_INCOME
            updateTypeSelection()
            loadCategories()
        }
    }

    private fun updateTypeSelection() {
        val themeColor = try {
            android.graphics.Color.parseColor(themeManager.getCurrentColor())
        } catch (_: Exception) {
            android.graphics.Color.parseColor("#6B8DD6")
        }

        if (billType == Bill.TYPE_EXPENSE) {
            binding.chipExpense.setBackgroundResource(R.drawable.bg_neu_button)
            binding.chipExpense.setTextColor(getColor(R.color.expense_red))
            binding.chipIncome.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            binding.chipIncome.setTextColor(getColor(R.color.text_hint))
        } else {
            binding.chipIncome.setBackgroundResource(R.drawable.bg_neu_button)
            binding.chipIncome.setTextColor(getColor(R.color.income_green))
            binding.chipExpense.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            binding.chipExpense.setTextColor(getColor(R.color.text_hint))
        }
    }

    private fun setupCategoryGrid() {
        loadCategories()
    }

    private fun loadCategories() {
        val categories = categoryDao.getAll(billType)
        val adapter = CategoryGridAdapter(categories) { category ->
            selectedCategoryId = category.id
            selectedCategoryName = category.name
            selectedCategoryIcon = category.icon
        }
        binding.rvCategories.layoutManager = GridLayoutManager(this, 4)
        binding.rvCategories.adapter = adapter
    }

    private fun setupPayType() {
        val payTypes = Bill.PAY_TYPES
        binding.chipGroupPayType.setOnCheckedStateChangeListener { _, checkedIds ->
            selectedPayType = when {
                checkedIds.contains(R.id.chipWechat) -> Bill.PAY_WECHAT
                checkedIds.contains(R.id.chipAlipay) -> Bill.PAY_ALIPAY
                checkedIds.contains(R.id.chipCash) -> Bill.PAY_CASH
                checkedIds.contains(R.id.chipBank) -> Bill.PAY_BANK
                else -> Bill.PAY_OTHER
            }
        }
    }

    private fun setupTimePicker() {
        binding.tvTime.setOnClickListener {
            val cal = Calendar.getInstance()
            cal.timeInMillis = selectedTime

            DatePickerDialog(this, { _, year, month, day ->
                TimePickerDialog(this, { _, hour, minute ->
                    cal.set(year, month, day, hour, minute, 0)
                    selectedTime = cal.timeInMillis
                    updateTimeDisplay()
                }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }
    }

    private fun updateTimeDisplay() {
        binding.tvTime.text = dateFormat.format(selectedTime)
    }

    private fun setupSaveButton() {
        binding.btnSave.setOnClickListener {
            val moneyStr = binding.etMoney.text.toString().trim()
            val money = moneyStr.toDoubleOrNull()

            if (money == null || money <= 0) {
                Toast.makeText(this, "请输入有效金额", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (selectedCategoryId == 0L) {
                Toast.makeText(this, "请选择分类", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val remark = binding.etRemark.text.toString().trim()

            if (editBillId > 0) {
                // 编辑模式
                val bill = Bill(
                    id = editBillId,
                    money = money,
                    type = billType,
                    categoryId = selectedCategoryId,
                    categoryName = selectedCategoryName,
                    categoryIcon = selectedCategoryIcon,
                    payType = selectedPayType,
                    billTime = selectedTime,
                    remark = remark
                )
                billDao.update(bill)
                Toast.makeText(this, "修改成功", Toast.LENGTH_SHORT).show()
            } else {
                // 新增模式
                val bill = Bill(
                    money = money,
                    type = billType,
                    categoryId = selectedCategoryId,
                    categoryName = selectedCategoryName,
                    categoryIcon = selectedCategoryIcon,
                    payType = selectedPayType,
                    billTime = selectedTime,
                    remark = remark,
                    source = Bill.SOURCE_MANUAL
                )
                billDao.insert(bill)
                Toast.makeText(this, "记账成功", Toast.LENGTH_SHORT).show()
            }

            finish()
        }
    }

    private fun loadBillData() {
        val bill = billDao.getById(editBillId) ?: return
        binding.etMoney.setText(bill.money.toString())
        binding.etRemark.setText(bill.remark)

        billType = bill.type
        updateTypeSelection()

        selectedCategoryId = bill.categoryId
        selectedCategoryName = bill.categoryName
        selectedCategoryIcon = bill.categoryIcon
        selectedPayType = bill.payType
        selectedTime = bill.billTime
        updateTimeDisplay()
    }
}
