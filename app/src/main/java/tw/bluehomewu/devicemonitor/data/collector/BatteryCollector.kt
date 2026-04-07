package tw.bluehomewu.devicemonitor.data.collector

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class BatteryInfo(val level: Int, val isCharging: Boolean)

class BatteryCollector(private val context: Context) {

    fun observe(): Flow<BatteryInfo> = callbackFlow {
        fun parseIntent(intent: Intent): BatteryInfo {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val pct = if (scale > 0) (level * 100 / scale) else level
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
            return BatteryInfo(pct, isCharging)
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                trySend(parseIntent(intent))
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        // ACTION_BATTERY_CHANGED is sticky — registerReceiver returns the last broadcast
        val stickyIntent = context.registerReceiver(receiver, filter)
        stickyIntent?.let { trySend(parseIntent(it)) }

        awaitClose { context.unregisterReceiver(receiver) }
    }
}
