package com.swordfish.lemuroid.app.mobile.feature.shortcuts

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.os.Build
import com.swordfish.lemuroid.app.shared.covers.CoverLoader
import com.swordfish.lemuroid.app.shared.deeplink.DeepLink
import com.swordfish.lemuroid.common.bitmap.toBitmap
import com.swordfish.lemuroid.lib.library.db.entity.Game

class ShortcutsGenerator(
    private val appContext: Context
) {

    suspend fun pinShortcutForGame(game: Game) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            return

        val shortcutManager = appContext.getSystemService(ShortcutManager::class.java)!!
        val bitmap = retrieveBitmap(game)

        val shortcutInfo = ShortcutInfo.Builder(appContext, "game_${game.id}")
            .setShortLabel(game.title)
            .setLongLabel(game.title)
            .setIntent(DeepLink.launchIntentForGame(appContext, game))
            .setIcon(Icon.createWithBitmap(bitmap))
            .build()

        shortcutManager.requestPinShortcut(shortcutInfo, null)
    }

    private fun retrieveBitmap(game: Game): Bitmap = retrieveFallbackBitmap(game)

    private fun retrieveFallbackBitmap(game: Game): Bitmap {
        val desiredIconSize = getDesiredIconSize()
        return CoverLoader.getFallbackDrawable(game).toBitmap(desiredIconSize, desiredIconSize)
    }

    private fun getDesiredIconSize(): Int {
        val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager?
        return am?.launcherLargeIconSize ?: 256
    }

    fun supportShortcuts(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            return false

        val shortcutManager = appContext.getSystemService(ShortcutManager::class.java)!!
        return shortcutManager.isRequestPinShortcutSupported
    }
}
