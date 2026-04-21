package com.swordfish.lemuroid.app.shared.deeplink

import android.content.Intent
import android.net.Uri
import com.swordfish.lemuroid.lib.library.db.entity.Game

object DeepLink {

    private fun uriForGame(appContext: android.content.Context, game: Game): Uri {
        return Uri.parse("lemuroid://${appContext.packageName}/play-game/id/${game.id}")
    }

    fun launchIntentForGame(appContext: android.content.Context, game: Game) =
        Intent(Intent.ACTION_VIEW, uriForGame(appContext, game))
}
