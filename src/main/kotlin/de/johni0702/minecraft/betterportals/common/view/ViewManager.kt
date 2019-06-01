package de.johni0702.minecraft.betterportals.common.view

import net.minecraft.entity.player.PlayerEntity

/**
 * Manages [View]s for a player.
 */
interface ViewManager {
    /**
     * The player whose views are managed.
     */
    val player: PlayerEntity

    /**
     * All views which currently exist for the player.
     */
    val views: List<View>

    /**
     * The main view.
     */
    val mainView: View
}