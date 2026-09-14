package fr.bonamy.sports

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Timeline

internal enum class LiveState { HIDDEN, LIVE, BEHIND }

internal object LivePlayback {
    fun state(player: Player): LiveState {
        if (player.playbackState != Player.STATE_READY || player.currentTimeline.isEmpty ||
            !player.isCurrentMediaItemLive || !player.isCurrentMediaItemDynamic ||
            !player.isCommandAvailable(Player.COMMAND_SEEK_TO_DEFAULT_POSITION)) return LiveState.HIDDEN
        val window = player.currentTimeline.getWindow(player.currentMediaItemIndex, Timeline.Window())
        val target = window.defaultPositionMs
        if (target == C.TIME_UNSET) return LiveState.HIDDEN
        // The provider's default position includes its normal live delay. Allow small
        // playlist-refresh differences rather than treating that delay as time-shifting.
        return if (player.playWhenReady && target - player.currentPosition <= 5_000) LiveState.LIVE else LiveState.BEHIND
    }

    fun goLive(player: Player) {
        if (state(player) != LiveState.BEHIND) return
        player.seekToDefaultPosition()
        player.play()
    }
}
