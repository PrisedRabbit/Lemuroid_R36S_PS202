package com.swordfish.lemuroid.app.shared.input.inputclass

import android.view.InputDevice
import com.swordfish.lemuroid.app.shared.input.InputKey
import com.swordfish.lemuroid.app.shared.input.lemuroiddevice.isHybridGamePad

interface InputClass {

    fun getInputKeys(): Set<InputKey>

    fun getAxesMap(): Map<Int, Int>
}

fun InputDevice?.getInputClass(): InputClass {
    return when {
        this == null -> InputClassUnknown
        this.isHybridGamePad() -> InputClassGamePad
        (sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD -> InputClassGamePad
        (sources and InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD -> InputClassKeyboard
        else -> InputClassUnknown
    }
}
