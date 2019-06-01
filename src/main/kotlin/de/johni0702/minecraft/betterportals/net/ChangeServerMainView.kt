package de.johni0702.minecraft.betterportals.net

import com.raphydaphy.crochet.network.IPacket
import com.raphydaphy.crochet.network.MessageHandler
import de.johni0702.minecraft.betterportals.BetterPortalsMod
import de.johni0702.minecraft.betterportals.MOD_ID
import de.johni0702.minecraft.betterportals.common.sync
import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.network.PacketContext
import net.minecraft.util.Identifier
import net.minecraft.util.PacketByteBuf
import net.minecraftforge.fml.common.network.simpleimpl.IPacket
import net.minecraftforge.fml.common.network.simpleimpl.IPacketHandler
import net.minecraftforge.fml.common.network.simpleimpl.PacketContext

internal class ChangeServerMainView(
        var viewId: Int = 0
) : IPacket {

    override fun read(buf: PacketByteBuf) {
        viewId = buf.readInt()
    }

    override fun write(buf: PacketByteBuf) {
        buf.writeInt(viewId)
    }

    internal companion object Handler : MessageHandler<ChangeServerMainView>() {
        val ID = Identifier(MOD_ID, "change_server_main_view")
        override fun create() = ChangeServerMainView()
        override fun handle(message: ChangeServerMainView, ctx: PacketContext) {
            ctx.sync { BetterPortalsMod.viewManagerImpl.makeMainViewAck(message.viewId) }
            return null
        }
    }
}