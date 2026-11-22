package dev.titan2.keys

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Only here so Android finds res/raw/titan2_custom.kcm through the manifest metadata
 * (Settings → Physical keyboard). It never receives anything.
 */
class KeyboardLayoutReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {}
}
