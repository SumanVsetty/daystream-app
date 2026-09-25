package com.ivy.planner.ui.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.annotation.CallSuper

/**
 * Hilt injects a receiver's fields in onReceive, so receivers must call super.onReceive().
 * Android's BroadcastReceiver.onReceive is abstract, which Kotlin won't let us call;
 * this empty base makes the super call valid.
 */
abstract class HiltBroadcastReceiver : BroadcastReceiver() {
    @CallSuper
    override fun onReceive(context: Context, intent: Intent) = Unit
}
