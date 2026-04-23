package com.swordfish.lemuroid.lib.android

import android.os.Build

object SupportedAbis {
    fun current(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Build.SUPPORTED_ABIS.toList()
        } else {
            listOfNotNull(Build.CPU_ABI, Build.CPU_ABI2)
                .filter { it.isNotBlank() }
                .distinct()
        }
    }
}
