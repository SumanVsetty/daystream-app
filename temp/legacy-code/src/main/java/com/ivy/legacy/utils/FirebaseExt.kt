package com.ivy.legacy.utils

import android.util.Log

// Apeiro: Firebase Crashlytics was removed. These helpers now only write to the
// local Android log (visible in Android Studio's Logcat), so nothing leaves the device.

private const val TAG = "Apeiro"

fun sendToCrashlytics(
    msg: String
) {
    DeveloperException(msg).sendToCrashlytics(msg)
}

fun Exception.sendToCrashlytics(
    clarification: String? = null
) {
    clarification?.let {
        logToCrashlytics("Log: $it")
    }
    Log.e(TAG, "Recorded exception", this)
}

fun logToCrashlytics(msg: String) {
    Log.d(TAG, msg)
}

class DeveloperException(msg: String) : Exception(msg)
