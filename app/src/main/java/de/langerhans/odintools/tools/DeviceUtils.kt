package de.langerhans.odintools.tools

import de.langerhans.odintools.tools.DeviceType.ODIN2
import de.langerhans.odintools.tools.DeviceType.ODIN3
import de.langerhans.odintools.tools.DeviceType.OTHER
import de.langerhans.odintools.tools.DeviceType.RP4
import javax.inject.Inject

class DeviceUtils @Inject constructor(
    private val executor: ShellExecutor,
) {

    fun getDeviceVersion() = executor.getStringProperty(SettingsRepo.KEY_BUILD_VERSION, "")

    fun getDeviceCodename() = executor.getStringProperty(SettingsRepo.KEY_DEVICE_NAME, "")

    fun getDeviceType(): DeviceType {
        return when (getDeviceCodename()) {
            "Q9" -> ODIN2
            "Odin3" -> ODIN3
            "4.0", "4.0P" -> RP4
            else -> OTHER
        }
    }
}

enum class DeviceType {
    ODIN2,
    ODIN3,
    RP4,
    OTHER,
}
