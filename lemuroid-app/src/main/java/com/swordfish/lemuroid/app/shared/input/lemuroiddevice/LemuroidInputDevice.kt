package com.swordfish.lemuroid.app.shared.input.lemuroiddevice

import android.content.Context
import android.view.InputDevice
import android.view.KeyEvent
import com.swordfish.lemuroid.app.shared.input.InputKey
import com.swordfish.lemuroid.app.shared.input.RetroKey
import com.swordfish.lemuroid.app.shared.settings.GameMenuShortcut

interface LemuroidInputDevice {

    fun getCustomizableKeys(): List<RetroKey>

    fun getDefaultBindings(): Map<InputKey, RetroKey>

    fun isSupported(): Boolean

    fun isEnabledByDefault(appContext: Context): Boolean

    fun getSupportedShortcuts(): List<GameMenuShortcut>
}

fun InputDevice?.getLemuroidInputDevice(): LemuroidInputDevice {
    return when {
        this == null -> LemuroidInputDeviceUnknown
        this.isHybridGamePad() -> LemuroidInputDeviceGamePad(this)
        (sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD -> LemuroidInputDeviceGamePad(this)
        (sources and InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD -> LemuroidInputDeviceKeyboard(this)
        else -> LemuroidInputDeviceUnknown
    }
}

fun InputDevice.isHybridGamePad(): Boolean {
    val hasGamePadButtons = hasKeys(
        KeyEvent.KEYCODE_BUTTON_A,
        KeyEvent.KEYCODE_BUTTON_B,
        KeyEvent.KEYCODE_BUTTON_X,
        KeyEvent.KEYCODE_BUTTON_Y
    ).all { it }

    val hasDirectionalPad = hasKeys(
        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT
    ).all { it }

    val hasJoystickAxes = motionRanges.any {
        it.axis == android.view.MotionEvent.AXIS_X || it.axis == android.view.MotionEvent.AXIS_HAT_X
    }

    return isVirtual.not() && hasGamePadButtons && hasDirectionalPad && hasJoystickAxes
}
