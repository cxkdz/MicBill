package com.billbook.app.model

data class ThemePalette(
    val key: String,
    val name: String,
    val dioramaType: DioramaType,
    // Diorama (Layer 1) colors
    val dioramaSkyTop: Int,
    val dioramaSkyBottom: Int,
    val dioramaGround: Int,
    val dioramaAccent: Int,
    val dioramaHighlight: Int,
    // Background Plane (Layer 2) colors
    val planePrimary: Int,
    val planeAccent: Int,
    val planeShadowTint: Int,
    val planeAmbientLight: Int,
    val planeGlassAlpha: Int,     // 0-255
    val planeBorderAlpha: Int,    // 0-255
    // Header (Layer 3) text colors
    val headerTextPrimary: Int,
    val headerTextSecondary: Int,
    // Segment control
    val segmentBg: Int,
    val segmentActiveBg: Int,
    val segmentActiveText: Int,
    val segmentInactiveText: Int,
    // Financial semantic colors
    val expenseColor: Int,
    val incomeColor: Int
)

enum class DioramaType {
    FOREST, SNOW_MOUNTAIN, DESERT, OCEAN, STARRY_SKY,
    CITY, CYBERPUNK, MINIMAL_GEOMETRY, ANIME, ILLUSTRATION
}
