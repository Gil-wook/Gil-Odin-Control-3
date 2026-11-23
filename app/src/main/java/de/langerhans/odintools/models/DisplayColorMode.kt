package de.langerhans.odintools.models

import androidx.annotation.StringRes
import de.langerhans.odintools.R
import de.langerhans.odintools.tools.ShellExecutor

sealed class DisplayColorMode(
    val id: String,
    val settingsValue: Int,
    @StringRes val textRes: Int,
) {
    data object Standard : DisplayColorMode("standard", 0, R.string.standard)
    data object Enhanced : DisplayColorMode("enhanced", 1, R.string.enhanced)
    data object Intelligent : DisplayColorMode("intelligent", 3, R.string.intelligent)
    data object Unknown : DisplayColorMode("unknown", -1, R.string.unknown)

    fun enable(executor: ShellExecutor) {
        if (this != Unknown) {
            executor.setIntSystemSetting(DISPLAY_COLOR_MODE, settingsValue)
        }
    }

    companion object {
        private const val DISPLAY_COLOR_MODE = "display_color_mode"

        fun getMode(executor: ShellExecutor) = when (executor.getIntSystemSetting(DISPLAY_COLOR_MODE, Standard.settingsValue)) {
            Standard.settingsValue -> Standard
            Enhanced.settingsValue -> Enhanced
            Intelligent.settingsValue -> Intelligent
            else -> Unknown
        }

        fun getById(id: String?) = when (id) {
            Standard.id -> Standard
            Enhanced.id -> Enhanced
            Intelligent.id -> Intelligent
            else -> Unknown
        }
    }
}
