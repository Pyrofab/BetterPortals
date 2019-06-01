package de.johni0702.minecraft.betterportals.server

import de.johni0702.minecraft.betterportals.common.pos
import de.johni0702.minecraft.betterportals.net.Transaction
import de.johni0702.minecraft.betterportals.net.TransferToDimension
import de.johni0702.minecraft.betterportals.net.sendTo
import de.johni0702.minecraft.betterportals.server.view.viewManager
import net.minecraft.entity.player.ServerPlayerEntity
import net.minecraft.server.PlayerManager
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.world.dimension.DimensionType
import net.minecraftforge.common.util.ITeleporter

object DimensionTransitionHandler {
    fun transferPlayerToDimension(player: ServerPlayerEntity, dimension: DimensionType) {
        val world = player.server!!.getWorld(dimension)
        val viewManager = player.viewManager

        // Hold on to the old main view until the client has finished the transition
        // (released in TransferToDimensionDone#Handler)
        viewManager.mainView.retain()

        // Create a new view entity in the destination dimension
        val view = viewManager.createView(world, player.pos) {
            // Let the teleporter position the view entity
            this.changeDimension(player.dimension)
        }

        // Start transaction to allow the handler of TransferToDimension to update the camera in the target dimension before switching to it
        Transaction.start(player)

        // Inform the client of the transition to allow it to prepare any graphical transitions
        TransferToDimension(viewManager.mainView.id, view.id).sendTo(player)

        // And immediately swap it with the main view (calling code expects the player to have been transferred when the method returns)
        // This will inform the client that the server main view has changed and it'll adapt accordingly
        view.makeMainView()

        // Release our claim on the new view (it's the main view now, no need for us to keep it alive)
        view.release()

        Transaction.end(player)

        // Finally send a poslook packet to have the client confirm the teleport (and to be able to discard any UsePortal messages until the confirmation)
        player.connection.setPlayerLocation(player.posX, player.y, player.z, player.rotationYaw, player.rotationPitch)
        viewManager.flushPackets()
    }
}