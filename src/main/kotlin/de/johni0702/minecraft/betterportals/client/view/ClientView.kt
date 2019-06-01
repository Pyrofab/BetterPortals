package de.johni0702.minecraft.betterportals.client.view

import de.johni0702.minecraft.betterportals.common.view.View
import net.minecraft.client.network.ClientPlayerEntity

interface ClientView : View {
    override val manager: ClientViewManager
    override val camera: ClientPlayerEntity

    fun <T> withView(block: () -> T): T = manager.withView(this, block)
}