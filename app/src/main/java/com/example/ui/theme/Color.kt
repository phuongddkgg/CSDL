package com.example.ui.theme

import androidx.compose.ui.graphics.Color

val NavyPrimary = Color(0xFF0B192C)
val OnNavyPrimary = Color(0xFFFFFFFF)
val NavyPrimaryContainer = Color(0xFFD6E4FF)
val OnNavyPrimaryContainer = Color(0xFF001B3E)

val CyanSecondary = Color(0xFF0077B6)
val OnCyanSecondary = Color(0xFFFFFFFF)
val CyanSecondaryContainer = Color(0xFFCEECFF)
val OnCyanSecondaryContainer = Color(0xFF001E30)

val TealTertiary = Color(0xFF0F766E)
val OnTealTertiary = Color(0xFFFFFFFF)
val TealTertiaryContainer = Color(0xFFCCFBF1)
val OnTealTertiaryContainer = Color(0xFF00201D)

val SurfaceLight = Color(0xFFF4F7FB)
val SurfaceContainerLight = Color(0xFFFFFFFF)
val SurfaceVariantLight = Color(0xFFE6ECF5)
val OnSurfaceLight = Color(0xFF0F172A)
val OnSurfaceVariantLight = Color(0xFF475569)
val OutlineLight = Color(0xFFCBD5E1)

val SurfaceDark = Color(0xFF07111E)
val SurfaceContainerDark = Color(0xFF0F2038)
val SurfaceVariantDark = Color(0xFF1A2F4B)
val OnSurfaceDark = Color(0xFFF1F5F9)
val OnSurfaceVariantDark = Color(0xFF94A3B8)
val OutlineDark = Color(0xFF334155)

val CyanPrimaryDark = Color(0xFF38BDF8)
val OnCyanPrimaryDark = Color(0xFF002B45)
val CyanContainerDark = Color(0xFF0C4A6E)
val OnCyanContainerDark = Color(0xFFE0F2FE)

object CarrierColors {
    val Viettel = Color(0xFFD90429)
    val ViettelContainer = Color(0xFFFFE6EA)
    val VinaPhone = Color(0xFF0066FF)
    val VinaPhoneContainer = Color(0xFFE3EEFF)
    val MobiFone = Color(0xFFEA580C)
    val MobiFoneContainer = Color(0xFFFFEDD5)
    val SuccessGreen = Color(0xFF15803D)
    val SuccessContainer = Color(0xFFDCFCE7)
    val WarningAmber = Color(0xFFB45309)
    val WarningContainer = Color(0xFFFEF3C7)
    val ErrorRed = Color(0xFFB91C1C)
    val ErrorContainer = Color(0xFFFEE2E2)

    fun colorFor(carrier: String): Color = when (carrier) {
        "Viettel" -> Viettel
        "VinaPhone" -> VinaPhone
        "MobiFone" -> MobiFone
        else -> CyanSecondary
    }

    fun containerFor(carrier: String): Color = when (carrier) {
        "Viettel" -> ViettelContainer
        "VinaPhone" -> VinaPhoneContainer
        "MobiFone" -> MobiFoneContainer
        else -> CyanSecondaryContainer
    }
}
