package tw.bluehomewu.devicemonitor.widget

import android.content.Context
import android.provider.Settings
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.action.clickable
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import kotlinx.serialization.json.Json
import tw.bluehomewu.devicemonitor.MainActivity
import tw.bluehomewu.devicemonitor.data.remote.DeviceRecord

class DeviceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val json = prefs.getString("cached_devices", null)
        val thisAndroidId = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ANDROID_ID
        )
        val device = json?.let {
            runCatching {
                Json { ignoreUnknownKeys = true }
                    .decodeFromString<List<DeviceRecord>>(it)
                    .firstOrNull { r -> r.deviceId == thisAndroidId }
            }.getOrNull()
        }

        provideContent {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ColorProvider(Color(0xFFF5F5F5)))
                    .clickable(actionStartActivity<MainActivity>())
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (device == null) {
                    Text(
                        text = "DeviceMonitor",
                        style = TextStyle(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = GlanceModifier.height(4.dp))
                    Text(text = "開啟 App 以載入資料")
                } else {
                    Text(
                        text = device.alias ?: device.deviceName,
                        style = TextStyle(fontWeight = FontWeight.Bold),
                        maxLines = 1
                    )
                    Spacer(modifier = GlanceModifier.height(6.dp))
                    val prefix = if (device.isCharging) "⚡ " else ""
                    Text(
                        text = "$prefix${device.batteryLevel}%",
                        style = TextStyle(
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = GlanceModifier.height(2.dp))
                    Text(
                        text = if (device.isOnline) "● 上線" else "○ 離線",
                        style = TextStyle(fontSize = 11.sp)
                    )
                }
            }
        }
    }
}
