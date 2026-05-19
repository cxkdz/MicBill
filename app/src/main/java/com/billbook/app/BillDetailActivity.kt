package com.billbook.app

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.billbook.app.databinding.ActivityBillDetailBinding
import com.billbook.app.db.BillDao
import com.billbook.app.model.Bill
import com.billbook.app.util.BackgroundApplier
import com.billbook.app.util.ThemeManager
import java.text.SimpleDateFormat
import java.util.*

class BillDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBillDetailBinding
    private lateinit var billDao: BillDao
    private lateinit var themeManager: ThemeManager
    private var billId: Long = 0
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)

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

        binding = ActivityBillDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbar) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.updatePadding(top = insets.top)
            windowInsets
        }

        BackgroundApplier.apply(this)

        billDao = BillDao(this)
        billId = intent.getLongExtra("bill_id", 0)

        setupToolbar()
        loadBillDetail()
        setupButtons()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "账单详情"
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun loadBillDetail() {
        val bill = billDao.getById(billId) ?: run {
            Toast.makeText(this, "账单不存在", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.apply {
            tvDetailIcon.text = bill.categoryIcon.ifEmpty { "📦" }
            tvDetailCategory.text = bill.categoryName

            val prefix = if (bill.type == Bill.TYPE_EXPENSE) "-" else "+"
            tvDetailMoney.text = String.format("%s¥%.2f", prefix, bill.money)
            tvDetailMoney.setTextColor(
                resources.getColor(
                    if (bill.type == Bill.TYPE_EXPENSE) R.color.expense_red else R.color.income_green,
                    null
                )
            )

            tvDetailType.text = if (bill.type == Bill.TYPE_EXPENSE) "支出" else "收入"
            tvDetailPayType.text = bill.payType
            tvDetailTime.text = dateFormat.format(Date(bill.billTime))
            tvDetailRemark.text = bill.remark.ifEmpty { "无" }
            tvDetailSource.text = bill.source
        }
    }

    private fun setupButtons() {
        binding.btnEdit.setOnClickListener {
            val intent = Intent(this, AddBillActivity::class.java)
            intent.putExtra("bill_id", billId)
            startActivity(intent)
        }

        binding.btnDelete.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("确认删除")
                .setMessage("确定要删除这条账单记录吗？")
                .setPositiveButton("删除") { _, _ ->
                    billDao.delete(billId)
                    Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        loadBillDetail()
    }
}
