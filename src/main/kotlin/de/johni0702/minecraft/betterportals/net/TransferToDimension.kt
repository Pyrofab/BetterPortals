package de.johni0702.minecraft.betterportals.net

import com.raphydaphy.crochet.network.IPacket
import com.raphydaphy.crochet.network.MessageHandler
import com.raphydaphy.crochet.network.PacketHandler
import de.johni0702.minecraft.betterportals.BetterPortalsMod.Companion.viewManager
import de.johni0702.minecraft.betterportals.LOGGER
import de.johni0702.minecraft.betterportals.MOD_ID
import de.johni0702.minecraft.betterportals.client.renderer.TransferToDimensionRenderer
import de.johni0702.minecraft.betterportals.common.sync
import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.network.PacketContext
import net.minecraft.util.Identifier
import net.minecraft.util.PacketByteBuf
import net.minecraftforge.fml.common.network.simpleimpl.IPacket
import net.minecraftforge.fml.common.network.simpleimpl.IPacketHandler
import net.minecraftforge.fml.common.network.simpleimpl.PacketContext

/**
 * Sent to the client when [net.minecraft.server.PlayerManager.transferPlayerToDimension] is called.
 * The [toId] is the soon-to-be main view of the new dimension. It is up to the client to notify the server
 * that the transition is complete and that the current/soon-to-be-old main view with id [fromId] is no longer needed
 * by sending a [TransferToDimensionDone] message.
 */
internal class TransferToDimension(
        var fromId: Int = 0,
        var toId: Int = 0
) : IPacket {

    override fun getID() = ID

    override fun read(buf: PacketByteBuf) {
        fromId = buf.readInt()
        toId = buf.readInt()
    }

    override fun write(buf: PacketByteBuf) {
        buf.writeInt(fromId)
        buf.writeInt(toId)
    }

    internal companion object Handler : MessageHandler<TransferToDimension>() {
        val ID = Identifier(MOD_ID, "transfer_to_dimension")

        override fun create() = TransferToDimension()

        override fun handle(ctx: PacketContext, message: TransferToDimension) {
            ctx.sync {
                val whenDone = {
                    PacketHandler.sendToServer(TransferToDimensionDone(message.fromId))
                }
                val fromView = viewManager.views.find { it.id == message.fromId }
                if (fromView == null) {
                    LOGGER.warn("Got TransferToDimension message for non-existent source view with id {}", message.fromId)
                    return@sync whenDone()
                }
                val toView = viewManager.views.find { it.id == message.toId }
                if (toView == null) {
                    LOGGER.warn("Got TransferToDimension message for non-existent destination view with id {}", message.toId)
                    return@sync whenDone()
                }
                TransferToDimensionRenderer(fromView, toView, whenDone)
            }
        }
    }
}