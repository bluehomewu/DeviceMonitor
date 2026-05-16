package tw.bluehomewu.devicemonitor.data.local

import android.content.SharedPreferences

class PartnerNamingManager(private val prefs: SharedPreferences) {

    fun getPartnerName(partnershipId: String): String? =
        prefs.getString("pname_$partnershipId", null)

    fun setPartnerName(partnershipId: String, name: String?) {
        prefs.edit().apply {
            if (name.isNullOrBlank()) remove("pname_$partnershipId")
            else putString("pname_$partnershipId", name.trim())
        }.apply()
    }

    fun getDeviceAlias(deviceId: String): String? =
        prefs.getString("dalias_$deviceId", null)

    fun setDeviceAlias(deviceId: String, alias: String?) {
        prefs.edit().apply {
            if (alias.isNullOrBlank()) remove("dalias_$deviceId")
            else putString("dalias_$deviceId", alias.trim())
        }.apply()
    }
}
