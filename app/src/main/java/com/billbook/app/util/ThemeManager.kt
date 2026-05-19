package com.billbook.app.util

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import com.billbook.app.R
import com.billbook.app.model.DioramaType
import com.billbook.app.model.ThemePalette

/**
 * 主题管理器 - 支持多种主题/背景切换 + 自定义图片背景 + 自定义颜色
 */
class ThemeManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
    private val appContext = context.applicationContext

    companion object {
        const val KEY_THEME = "current_theme"
        const val KEY_BG_INDEX = "current_background"
        const val KEY_CUSTOM_BG_URI = "custom_bg_uri"
        const val KEY_CUSTOM_BG_ENABLED = "custom_bg_enabled"
        const val KEY_BG_OPACITY = "bg_opacity"
        const val KEY_ICON = "current_icon"
        const val KEY_DARK_MODE = "dark_mode"
        const val KEY_CUSTOM_COLOR = "custom_color"
        const val KEY_USE_CUSTOM_COLOR = "use_custom_color"
        const val KEY_NAV_ICON_SET = "nav_icon_set"
        const val KEY_NAV_LEFT = "nav_left"
        const val KEY_PALETTE = "current_palette"
        const val KEY_TILT_ANGLE = "tilt_angle"
        const val KEY_CUSTOM_DIORAMA_URI = "custom_diorama_uri"

        const val BG_MODE_COLOR = 0
        const val BG_MODE_CUSTOM_IMAGE = 1

        private const val PKG = "com.billbook.app"

        // 主题列表
        val THEMES = listOf(
            ThemeItem("default", "默认蓝", R.style.Theme_BillBook, "#2196F3"),
            ThemeItem("green", "清新绿", R.style.Theme_BillBook_Green, "#4CAF50"),
            ThemeItem("orange", "活力橙", R.style.Theme_BillBook_Orange, "#FF9800"),
            ThemeItem("purple", "优雅紫", R.style.Theme_BillBook_Purple, "#9C27B0"),
            ThemeItem("pink", "少女粉", R.style.Theme_BillBook_Pink, "#E91E63"),
            ThemeItem("custom", "自定义", R.style.Theme_BillBook, "#6B8DD6")
        )

        // 预设颜色
        val PRESET_COLORS = listOf(
            "#F44336", "#E91E63", "#9C27B0", "#673AB7",
            "#3F51B5", "#2196F3", "#03A9F4", "#00BCD4",
            "#009688", "#4CAF50", "#8BC34A", "#CDDC39",
            "#FFC107", "#FF9800", "#FF5722", "#795548",
            "#607D8B", "#9E9E9E"
        )

        // 背景列表
        val BACKGROUNDS = listOf(
            BgItem(0, "纯白", "#FFFFFF"),
            BgItem(1, "浅灰", "#F5F5F5"),
            BgItem(2, "米白", "#FFF8E1"),
            BgItem(3, "浅蓝", "#E3F2FD"),
            BgItem(4, "浅绿", "#E8F5E9"),
            BgItem(5, "浅粉", "#FCE4EC")
        )

        // 图标列表
        val ICONS = listOf(
            IconItem("default", "默认绿", R.drawable.ic_launcher, ""),
            IconItem("blue", "天空蓝", R.drawable.ic_launcher_blue, "$PKG.MainActivityAliasBlue"),
            IconItem("orange", "活力橙", R.drawable.ic_launcher_orange, "$PKG.MainActivityAliasOrange"),
            IconItem("purple", "优雅紫", R.drawable.ic_launcher_purple, "$PKG.MainActivityAliasPurple"),
            IconItem("red", "中国红", R.drawable.ic_launcher_red, "$PKG.MainActivityAliasRed"),
            IconItem("dark", "深邃黑", R.drawable.ic_launcher_dark, "$PKG.MainActivityAliasDark")
        )

        // 导航栏图标集
        val NAV_ICON_SETS = listOf(
            NavIconSet("default", "默认",
                R.drawable.ic_nav_bills, R.drawable.ic_nav_bills_selected,
                R.drawable.ic_nav_stats, R.drawable.ic_nav_stats_selected,
                R.drawable.ic_nav_settings, R.drawable.ic_nav_settings_selected),
            NavIconSet("rounded", "圆角",
                R.drawable.ic_nav_bills_rounded, R.drawable.ic_nav_bills_rounded_selected,
                R.drawable.ic_nav_stats_rounded, R.drawable.ic_nav_stats_rounded_selected,
                R.drawable.ic_nav_settings_rounded, R.drawable.ic_nav_settings_rounded_selected),
            NavIconSet("minimal", "简约",
                R.drawable.ic_nav_bills_minimal, R.drawable.ic_nav_bills_minimal_selected,
                R.drawable.ic_nav_stats_minimal, R.drawable.ic_nav_stats_minimal_selected,
                R.drawable.ic_nav_settings_minimal, R.drawable.ic_nav_settings_minimal_selected)
        )
    }

    data class ThemeItem(val key: String, val name: String, val styleRes: Int, val color: String)
    data class BgItem(val index: Int, val name: String, val color: String)
    data class IconItem(val key: String, val name: String, val drawableRes: Int, val aliasName: String)

    data class NavIconSet(
        val key: String,
        val name: String,
        val bills: Int,
        val billsSelected: Int,
        val stats: Int,
        val statsSelected: Int,
        val settings: Int,
        val settingsSelected: Int
    )

    fun getCurrentTheme(): ThemeItem {
        val key = prefs.getString(KEY_THEME, "default") ?: "default"
        return THEMES.find { it.key == key } ?: THEMES[0]
    }

    fun setTheme(key: String) {
        prefs.edit().putString(KEY_THEME, key).apply()
        if (key != "custom") {
            prefs.edit().putBoolean(KEY_USE_CUSTOM_COLOR, false).apply()
        }
    }

    // ========== 自定义颜色 ==========

    fun isCustomColorEnabled(): Boolean {
        return prefs.getBoolean(KEY_USE_CUSTOM_COLOR, false)
    }

    fun getCustomColor(): String {
        return prefs.getString(KEY_CUSTOM_COLOR, "#6B8DD6") ?: "#6B8DD6"
    }

    fun setCustomColor(color: String) {
        prefs.edit()
            .putString(KEY_CUSTOM_COLOR, color)
            .putBoolean(KEY_USE_CUSTOM_COLOR, true)
            .putString(KEY_THEME, "custom")
            .apply()
    }

    fun getCurrentColor(): String {
        return if (isCustomColorEnabled()) {
            getCustomColor()
        } else {
            getCurrentTheme().color
        }
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

    // ========== 纯色背景 ==========

    fun getCurrentBackground(): BgItem {
        val index = prefs.getInt(KEY_BG_INDEX, 0)
        return BACKGROUNDS.getOrNull(index) ?: BACKGROUNDS[0]
    }

    fun setBackground(index: Int) {
        prefs.edit()
            .putInt(KEY_BG_INDEX, index)
            .putBoolean(KEY_CUSTOM_BG_ENABLED, false)
            .apply()
    }

    // ========== 自定义图片背景 ==========

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

    // ========== 背景透明度 ==========

    fun setBgOpacity(opacity: Int) {
        prefs.edit().putInt(KEY_BG_OPACITY, opacity.coerceIn(0, 100)).apply()
    }

    fun getBgOpacity(): Int {
        return prefs.getInt(KEY_BG_OPACITY, 30)
    }

    // ========== 获取当前背景模式 ==========

    fun getBackgroundMode(): Int {
        return if (isCustomBackgroundEnabled()) BG_MODE_CUSTOM_IMAGE else BG_MODE_COLOR
    }

    // ========== 导航栏图标集 ==========

    fun getCurrentNavIconSet(): NavIconSet {
        val key = prefs.getString(KEY_NAV_ICON_SET, "default") ?: "default"
        return NAV_ICON_SETS.find { it.key == key } ?: NAV_ICON_SETS[0]
    }

    fun setNavIconSet(key: String) {
        prefs.edit().putString(KEY_NAV_ICON_SET, key).apply()
    }

    // ========== 导航栏位置 ==========

    fun isNavLeft(): Boolean {
        return prefs.getBoolean(KEY_NAV_LEFT, false)
    }

    fun setNavLeft(left: Boolean) {
        prefs.edit().putBoolean(KEY_NAV_LEFT, left).apply()
    }

    // ========== 图标切换 ==========

    fun getCurrentIcon(): IconItem {
        val key = prefs.getString(KEY_ICON, "default") ?: "default"
        if (key == "custom") {
            return IconItem("custom", "自定义图片", R.drawable.ic_launcher, "")
        }
        return ICONS.find { it.key == key } ?: ICONS[0]
    }

    fun setCustomIcon() {
        val pm = appContext.packageManager
        for (icon in ICONS) {
            if (icon.aliasName.isNotEmpty()) {
                val component = ComponentName(appContext, icon.aliasName)
                pm.setComponentEnabledSetting(
                    component,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
        }
        val mainComponent = ComponentName(appContext, "$PKG.MainActivity")
        pm.setComponentEnabledSetting(
            mainComponent,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
        prefs.edit().putString(KEY_ICON, "custom").apply()
    }

    fun setIcon(key: String) {
        val selected = ICONS.find { it.key == key } ?: return
        val pm = appContext.packageManager

        for (icon in ICONS) {
            if (icon.aliasName.isNotEmpty()) {
                val component = ComponentName(appContext, icon.aliasName)
                pm.setComponentEnabledSetting(
                    component,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
        }

        if (selected.aliasName.isNotEmpty()) {
            val mainComponent = ComponentName(appContext, "$PKG.MainActivity")
            pm.setComponentEnabledSetting(
                mainComponent,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            val aliasComponent = ComponentName(appContext, selected.aliasName)
            pm.setComponentEnabledSetting(
                aliasComponent,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
        } else {
            val mainComponent = ComponentName(appContext, "$PKG.MainActivity")
            pm.setComponentEnabledSetting(
                mainComponent,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
        }

        prefs.edit().putString(KEY_ICON, key).apply()
    }

    // ========== 3D 空间主题调色板 ==========

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

    fun getEffectivePalette(): ThemePalette {
        val key = getCurrentPaletteKey()
        if (key == "custom") {
            return getCustomPalette() ?: ThemePalettes.findByKey(ThemePalettes.DEFAULT_KEY)!!
        }
        return ThemePalettes.findByKey(key) ?: ThemePalettes.findByKey(ThemePalettes.DEFAULT_KEY)!!
    }

    // ========== 自定义调色板 ==========

    fun setCustomPalette(
        dioramaSkyTop: String,
        dioramaSkyBottom: String,
        dioramaGround: String,
        planePrimary: String,
        planeAccent: String
    ) {
        prefs.edit()
            .putString("custom_diorama_sky_top", dioramaSkyTop)
            .putString("custom_diorama_sky_bottom", dioramaSkyBottom)
            .putString("custom_diorama_ground", dioramaGround)
            .putString("custom_plane_primary", planePrimary)
            .putString("custom_plane_accent", planeAccent)
            .putString(KEY_PALETTE, "custom")
            .apply()
    }

    private fun getCustomPalette(): ThemePalette? {
        val skyTop = prefs.getString("custom_diorama_sky_top", null) ?: return null
        val skyBottom = prefs.getString("custom_diorama_sky_bottom", null) ?: return null
        val ground = prefs.getString("custom_diorama_ground", null) ?: return null
        val planePrimary = prefs.getString("custom_plane_primary", null) ?: return null
        val planeAccent = prefs.getString("custom_plane_accent", null) ?: return null

        return try {
            ThemePalette(
                key = "custom",
                name = "自定义",
                dioramaType = DioramaType.MINIMAL_GEOMETRY,
                dioramaSkyTop = Color.parseColor(skyTop),
                dioramaSkyBottom = Color.parseColor(skyBottom),
                dioramaGround = Color.parseColor(ground),
                dioramaAccent = Color.parseColor(planeAccent),
                dioramaHighlight = Color.WHITE,
                planePrimary = Color.parseColor(planePrimary),
                planeAccent = Color.parseColor(planeAccent),
                planeShadowTint = Color.parseColor("#40000000"),
                planeAmbientLight = Color.parseColor("#20FFFFFF"),
                planeGlassAlpha = 30,
                planeBorderAlpha = 20,
                headerTextPrimary = Color.parseColor("#1A1A2E"),
                headerTextSecondary = Color.parseColor("#801A1A2E"),
                segmentBg = Color.parseColor("#15000000"),
                segmentActiveBg = Color.parseColor("#25000000"),
                segmentActiveText = Color.parseColor(planeAccent),
                segmentInactiveText = Color.parseColor("#801A1A2E"),
                expenseColor = Color.parseColor("#E87C7C"),
                incomeColor = Color.parseColor("#7CD4A8")
            )
        } catch (_: Exception) {
            null
        }
    }
}
