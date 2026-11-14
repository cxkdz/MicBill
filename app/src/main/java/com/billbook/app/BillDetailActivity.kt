package com.billbook.app

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.billbook.app.databinding.ActivityBillDetailBinding
import com.billbook.app.db.BillDao
import com.billbook.app.model.Bill
import com.billbook.app.util.CategoryIconMapper
import com.billbook.app.util.ThemeManager
import java.text.SimpleDateFormat
import java.util.*

class BillDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBillDetailBinding
    private lateinit var billDao: BillDao
    private lateinit var themeManager: ThemeManager
    private var billId: Long = 0
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
    private var glowAnimator: android.animation.ValueAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        themeManager = ThemeManager(this)
        setTheme(R.style.Theme_BillBook)  // 主题样式已收敛为一套，配色由 ThemePalettes 负责
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.setBackgroundDrawableResource(android.R.color.transparent)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        binding = ActivityBillDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        com.billbook.app.util.ThemeSurfaces.apply(binding.root)

        // 顶部栏适配状态栏 insets（标题下移避开刘海）
        ViewCompat.setOnApplyWindowInsetsListener(binding.topBar) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.updatePadding(top = insets.top)
            windowInsets
        }

        billDao = BillDao(this)
        billId = intent.getLongExtra("bill_id", 0)

        setupBackButton()
        setupButtons()
        loadBillDetail()
        playEntryAnimation()
        setupBackPress()
    }

    private fun setupBackButton() {
        binding.btnBack.setOnClickListener { playExitAnimation { finish() } }
    }

    private fun setupBackPress() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                playExitAnimation { finish() }
            }
        })
    }

    private fun loadBillDetail() {
        val bill = billDao.getById(billId) ?: run {
            Toast.makeText(this, "账单不存在", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.apply {
            // 使用 CategoryIconMapper 获取图标资源 ID
            val iconResId = if (bill.categoryIconResId != 0) {
                bill.categoryIconResId
            } else {
                CategoryIconMapper.getIconResId(bill.categoryName, bill.type)
            }

            if (iconResId != 0) {
                ivDetailIcon.setImageResource(iconResId)
                ivDetailIcon.visibility = View.VISIBLE
                tvDetailIcon.visibility = View.GONE
            } else {
                // 兜底分支：不再显示系统 emoji，图标位留空
                tvDetailIcon.text = ""
                tvDetailIcon.visibility = View.VISIBLE
                ivDetailIcon.visibility = View.GONE
            }

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
        startGlowAnimation(binding.btnEdit)

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

    private fun startGlowAnimation(view: android.view.View) {
        val bg = view.background
        if (bg !is GradientDrawable) return
        // 创建一个独立的可变副本，避免影响原始 drawable
        val glowBg = bg.constantState?.newDrawable()?.mutate() as? GradientDrawable ?: return
        view.background = glowBg

        glowAnimator = ObjectAnimator.ofFloat(0f, 1f).apply {
            duration = 2000
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            addUpdateListener {
                val alpha = (it.animatedValue as Float * 120).toInt()
                glowBg.setStroke(
                    (2 * resources.displayMetrics.density).toInt(),
                    Color.argb(alpha, 107, 141, 214)
                )
            }
            start()
        }
    }

    private fun playEntryAnimation() {
        binding.detailCard.scaleX = 0.3f
        binding.detailCard.scaleY = 0.3f
        binding.detailCard.alpha = 0f
        binding.dimBackground.alpha = 0f

        val scaleUpX = ObjectAnimator.ofFloat(binding.detailCard, "scaleX", 0.3f, 1f)
        val scaleUpY = ObjectAnimator.ofFloat(binding.detailCard, "scaleY", 0.3f, 1f)
        val fadeIn = ObjectAnimator.ofFloat(binding.detailCard, "alpha", 0f, 1f)
        val dimIn = ObjectAnimator.ofFloat(binding.dimBackground, "alpha", 0f, 0.5f)

        AnimatorSet().apply {
            playTogether(scaleUpX, scaleUpY, fadeIn, dimIn)
            duration = 350
            interpolator = OvershootInterpolator(1.1f)
            start()
        }
    }

    private fun playExitAnimation(onEnd: () -> Unit) {
        glowAnimator?.cancel()

        val scaleDownX = ObjectAnimator.ofFloat(binding.detailCard, "scaleX", 1f, 0.3f)
        val scaleDownY = ObjectAnimator.ofFloat(binding.detailCard, "scaleY", 1f, 0.3f)
        val fadeOut = ObjectAnimator.ofFloat(binding.detailCard, "alpha", 1f, 0f)
        val dimOut = ObjectAnimator.ofFloat(binding.dimBackground, "alpha", 0.5f, 0f)

        AnimatorSet().apply {
            playTogether(scaleDownX, scaleDownY, fadeOut, dimOut)
            duration = 250
            interpolator = DecelerateInterpolator()
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    onEnd()
                }
            })
            start()
        }
    }

    override fun onResume() {
        super.onResume()
        loadBillDetail()
    }

    override fun onDestroy() {
        glowAnimator?.cancel()
        super.onDestroy()
    }
}
