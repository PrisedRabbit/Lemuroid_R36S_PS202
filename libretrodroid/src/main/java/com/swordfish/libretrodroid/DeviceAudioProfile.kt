package com.swordfish.libretrodroid

import android.os.Build

enum class DeviceAudioProfile(val nativeValue: Int) {
    DEFAULT(0),
    PS202(1);

    companion object {
        fun resolveCurrent(): DeviceAudioProfile {
            return if (Build.MODEL == "PS202") PS202 else DEFAULT
        }
    }
}
