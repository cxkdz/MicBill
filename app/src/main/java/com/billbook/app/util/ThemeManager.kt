package com.billbook.app.util

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.billbook.app.model.ThemePalette

/**
 * 主题管理器 - 背景预设渐变 + 前景图片 + 自定义配置
 *
 * 说明：早期版本的「纯色背景列表 / 启动图标切换 / 导航图标集 / 旧五套主题样式 /
 * 自定义主色」五条链路从未接入 UI（全项目零调用），已整体删除，只保留
 * 真正在用的预设调色板 + 自定义前景图 + 自定义 UI 配色覆盖层。
 */
class ThemeManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
    private val appContext = context.applicationContext

    companion object {
        const val KEY_CUSTOM_BG_URI = "custom_bg_uri"
        const val KEY_CUSTOM_BG_ENABLED = "custom_bg_enabled"
        const val KEY_BG_OPACITY = "bg_opacity"
        const val KEY_DARK_MODE = "dark_mode"
        const val KEY_PALETTE = "current_palette"
        const val KEY_TILT_ANGLE = "tilt_angle"
        const val KEY_CUSTOM_DIORAMA_URI = "custom_diorama_uri"
        const val KEY_TEXT_COLOR_MODE = "text_color_mode"
        const val KEY_SNOW_ENABLED = "snow_enabled"
        // 账单浏览方式：horizontal（一天一页横滑）/ vertical（按日期分组竖滑）
        const val KEY_BILL_VIEW_MODE = "bill_view_mode"
        // 自定义 UI 配色覆盖层（独立存储，不改动预设 key）
        const val KEY_USE_CUSTOM_UI_COLOR = "use_custom_ui_color"
        const val KEY_CUSTOM_UI_GROUND = "custom_ui_ground"
        const val KEY_CUSTOM_UI_ACCENT = "custom_ui_accent"

        // 预设颜色（UiColorDialog 的 18 格色板）
        val PRESET_COLORS = listOf(
            "#F44336", "#E91E63", "#9C27B0", "#673AB7",
            "#3F51B5", "#2196F3", "#03A9F4", "#00BCD4",
            "#009688", "#4CAF50", "#8BC34A", "#CDDC39",
            "#FFC107", "#FF9800", "#FF5722", "#795548",
            "#607D8B", "#9E9E9E"
        )
    }

    // ========== 深色模式 ==========

    fun isDarkMode(): Boolean {
        return prefs.getBoolean(KEY_DARK_MODE, false)
    }

    fun setDarkMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_MODE, enabled).apply()
    }

    fun toggleDarkMode(): Boolean {
        val newValue = !isDarkMode()
        setDarkMode(newValue)
        return newValue
    }

    // ========== 自定义前景图片 ==========

    fun setCustomBackground(uri: Uri) {
        try {
            appContext.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {}

        prefs.edit()
            .putString(KEY_CUSTOM_BG_URI, uri.toString())
            .putBoolean(KEY_CUSTOM_BG_ENABLED, true)
            .apply()
    }

    fun getCustomBackgroundUri(): Uri? {
        val uriStr = prefs.getString(KEY_CUSTOM_BG_URI, null) ?: return null
        return Uri.parse(uriStr)
    }

    fun isCustomBackgroundEnabled(): Boolean {
        return prefs.getBoolean(KEY_CUSTOM_BG_ENABLED, false) && getCustomBackgroundUri() != null
    }

    fun clearCustomBackground() {
        prefs.edit()
            .putBoolean(KEY_CUSTOM_BG_ENABLED, false)
            .remove(KEY_CUSTOM_BG_URI)
            .apply()
    }

    // ========== 前景透明度 ==========

    fun setBgOpacity(opacity: Int) {
        prefs.edit().putInt(KEY_BG_OPACITY, opacity.coerceIn(0, 100)).apply()
    }

    fun getBgOpacity(): Int {
        return prefs.getInt(KEY_BG_OPACITY, 30)
    }

    // ========== 背景预设调色板 ==========

    fun getCurrentPaletteKey(): String {
        return prefs.getString(KEY_PALETTE, ThemePalettes.DEFAULT_KEY) ?: ThemePalettes.DEFAULT_KEY
    }

    fun setPalette(key: String) {
        prefs.edit().putString(KEY_PALETTE, key).apply()
    }

    fun getTiltAngle(): Int {
        return prefs.getInt(KEY_TILT_ANGLE, 15)
    }

    fun setTiltAngle(angle: Int) {
        prefs.edit().putInt(KEY_TILT_ANGLE, angle.coerceIn(0, 45)).apply()
    }

    fun setCustomDioramaImage(uri: Uri) {
        try {
            appContext.contentResolver.takePersistableUriPermission(
                uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {}
        prefs.edit().putString(KEY_CUSTOM_DIORAMA_URI, uri.toString()).apply()
    }

    fun getCustomDioramaImageUri(): Uri? {
        val uriStr = prefs.getString(KEY_CUSTOM_DIORAMA_URI, null) ?: return null
        return Uri.parse(uriStr)
    }

    fun hasCustomDioramaImage(): Boolean {
        return getCustomDioramaImageUri() != null
    }

    fun clearCustomDioramaImage() {
        prefs.edit().remove(KEY_CUSTOM_DIORAMA_URI).apply()
    }

    // ========== 文字颜色模式（已收敛为自动）==========

    /**
     * 文字色统一由 AutoContrast 按实际背景亮度决定（mode 0 = 自动）。
     *
     * 早期深色模式会写入 mode=2 强制白字，与 ThemePalettes.derive() 的
     * AutoContrast 派生构成两套决策；用户自定义亮底配色 + 深色模式时会出现
     * 白字压亮底。这里恒返回 0，并顺手清掉存量 SP 里的非 0 脏值，
     * 保证老用户升级后同样生效。所有调用方（4 个 Fragment、两个 Applier）
     * 无需改动。
     */
    fun getTextColorMode(): Int {
        if (prefs.getInt(KEY_TEXT_COLOR_MODE, 0) != 0) {
            prefs.edit().putInt(KEY_TEXT_COLOR_MODE, 0).apply()
        }
        return 0
    }

    @Deprecated("文字色统一由 AutoContrast 决定，不再需要手动切换模式")
    fun setTextColorMode(mode: Int) {
        prefs.edit().putInt(KEY_TEXT_COLOR_MODE, mode.coerceIn(0, 2)).apply()
    }

    // ========== 账单浏览方式 ==========

    /** 默认横向翻页，保持老用户升级后界面不变 */
    fun isVerticalBillMode(): Boolean =
        prefs.getString(KEY_BILL_VIEW_MODE, "horizontal") == "vertical"

    fun setVerticalBillMode(vertical: Boolean) {
        prefs.edit()
            .putString(KEY_BILL_VIEW_MODE, if (vertical) "vertical" else "horizontal")
            .apply()
    }

    // ========== 雪花效果 ==========

    fun isSnowEnabled(): Boolean {
        return prefs.getBoolean(KEY_SNOW_ENABLED, true)
    }

    fun setSnowEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SNOW_ENABLED, enabled).apply()
    }

    // ========== 自定义 UI 配色（默认关闭的覆盖层）==========

    /** 覆盖层默认关闭，保证存量用户升级后界面不突变 */
    fun isCustomUiColorEnabled(): Boolean =
        prefs.getBoolean(KEY_USE_CUSTOM_UI_COLOR, false)

    /** @return 容器底色 to 强调色；未开启或数据不全时返回 null */
    fun getCustomUiColors(): Pair<Int, Int>? {
        if (!isCustomUiColorEnabled()) return null
        val ground = prefs.getInt(KEY_CUSTOM_UI_GROUND, 0)
        val accent = prefs.getInt(KEY_CUSTOM_UI_ACCENT, 0)
        return if (ground != 0 && accent != 0) ground to accent else null
    }

    fun setCustomUiColors(ground: Int, accent: Int) {
        prefs.edit()
            .putInt(KEY_CUSTOM_UI_GROUND, ground)
            .putInt(KEY_CUSTOM_UI_ACCENT, accent)
            .putBoolean(KEY_USE_CUSTOM_UI_COLOR, true)
            .apply()
    }

    /** 关掉覆盖层，回到当前预设的配色（保留已存色值，方便再开回来） */
    fun clearCustomUiColors() {
        prefs.edit().putBoolean(KEY_USE_CUSTOM_UI_COLOR, false).apply()
    }

    /**
     * 全项目唯一取色入口。预设决定图片/玻璃参数，覆盖层只替换两个基础色，
     * 并走与预设**完全相同**的 derive() 重算所有派生字段。
     * 保留 base 的 key/name，因此按 key 判断的逻辑与设置页主题名不受影响。
     */
    fun getEffectivePalette(): ThemePalette {
        val base = ThemePalettes.findByKey(getCurrentPaletteKey()) ?: ThemePalettes.DEFAULT
        val (ground, accent) = getCustomUiColors() ?: return base
        return ThemePalettes.derive(base, ground, accent, overrideGradient = true)
    }
}
