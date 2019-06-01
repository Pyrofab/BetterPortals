package de.johni0702.minecraft.betterportals.net

import com.raphydaphy.crochet.network.IPacket
import com.raphydaphy.crochet.network.MessageHandler
import de.johni0702.minecraft.betterportals.BetterPortalsMod
import de.johni0702.minecraft.betterportals.LOGGER
import de.johni0702.minecraft.betterportals.MOD_ID
import de.johni0702.minecraft.betterportals.common.sync
import net.fabricmc.fabric.api.network.PacketContext
import net.minecraft.util.Identifier
import net.minecraft.util.PacketByteBuf

class DestroyView(
        var viewId: Int = 0
) : IPacket {
    override fun getID() = ID

    override fun read(buf: PacketByteBuf) {
        viewId = buf.readInt()
    }

    override fun write(buf: PacketByteBuf) {
        buf.writeInt(viewId)
    }

    internal companion object Handler : MessageHandler<DestroyView>() {
        val ID = Identifier(MOD_ID, "destroy_view")
        override fun handle(ctx: PacketContext, message: DestroyView) {
            ctx.sync {
                val manager = BetterPortalsMod.viewManagerImpl
                val view = manager.views.find { it.id == message.viewId }
                if (view == null) {
                    LOGGER.warn("Received destroy view message for unknown view with id ${message.viewId}")
                    return@sync
                }
                manager.destroyView(view)
            }
        }

        override fun create() = DestroyView()
    }
}
