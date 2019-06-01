package de.johni0702.minecraft.betterportals.net

import com.raphydaphy.crochet.network.IPacket
import com.raphydaphy.crochet.network.MessageHandler
import de.johni0702.minecraft.betterportals.BetterPortalsMod
import de.johni0702.minecraft.betterportals.LOGGER
import de.johni0702.minecraft.betterportals.MOD_ID
import de.johni0702.minecraft.betterportals.common.sync
import de.johni0702.minecraft.betterportals.server.view.viewManager
import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.network.PacketContext
import net.minecraft.util.Identifier
import net.minecraft.util.PacketByteBuf
import net.minecraftforge.fml.common.network.simpleimpl.IPacket
import net.minecraftforge.fml.common.network.simpleimpl.IPacketHandler
import net.minecraftforge.fml.common.network.simpleimpl.PacketContext

/**
 * Sent from the client when a dimension change initiated by a [TransferToDimension] message has been completed.
 * The view with Id [viewId] will subsequently be destroyed unless it is used elsewhere.
 */
internal class TransferToDimensionDone(
        var viewId: Int = 0
) : IPacket {

    override fun getID() = ID

    override fun read(buf: PacketByteBuf) {
        viewId = buf.readInt()
    }

    override fun write(buf: PacketByteBuf) {
        buf.writeInt(viewId)
    }

    internal companion object Handler : MessageHandler<TransferToDimensionDone>() {
        val ID = Identifier(MOD_ID)

        override fun create() = TransferToDimensionDone()

        override fun handle(ctx: PacketContext, message: TransferToDimensionDone) {
            ctx.sync {
                val view = ctx.serverHandler.player.viewManager.views.find { it.id == message.viewId }
                if (view == null) {
                    LOGGER.warn("Got TransferToDimensionDone message for unknown source view with id {}", message.viewId)
                    return@sync
                }
                view.release()
            }
        }
    }
}