package de.johni0702.minecraft.betterportals.net

import com.raphydaphy.crochet.network.IPacket
import com.raphydaphy.crochet.network.MessageHandler
import de.johni0702.minecraft.betterportals.BetterPortalsMod
import de.johni0702.minecraft.betterportals.MOD_ID
import de.johni0702.minecraft.betterportals.common.sync
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.util.AbstractReferenceCounted
import io.netty.util.ReferenceCounted
import net.fabricmc.fabric.api.network.PacketContext
import net.minecraft.network.PacketBuffer
import net.minecraft.util.Identifier
import net.minecraft.util.PacketByteBuf
import net.minecraftforge.fml.common.network.simpleimpl.IPacket
import net.minecraftforge.fml.common.network.simpleimpl.IPacketHandler
import net.minecraftforge.fml.common.network.simpleimpl.PacketContext

class ViewData(
        var viewId: Int = 0,
        var data: ByteBuf = Unpooled.EMPTY_BUFFER
) : IPacket, AbstractReferenceCounted() {

    override fun read(buf: PacketByteBuf) {
        with(buf) {
            viewId = readVarInt()
            data = readBytes(buf.readableBytes())
        }
    }

    override fun write(buf: PacketByteBuf) {
        with(buf) {
            writeVarInt(viewId)
            writeBytes(data)
        }
    }

    override fun touch(hint: Any?): ReferenceCounted {
        data.touch(hint)
        return this
    }

    override fun deallocate() {
        data.release()
    }

    internal companion object Handler : MessageHandler<ViewData>() {
        val ID = Identifier(MOD_ID, "view_data")
        override fun create() = ViewData()
        override fun handle(ctx: PacketContext, message: ViewData) {
            message.retain()
            ctx.sync {
                try {
                    BetterPortalsMod.viewManagerImpl.handleViewData(message.viewId, message.data)
                } finally {
                    message.release()
                }
            }
        }
    }
}
