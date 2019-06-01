package de.johni0702.minecraft.betterportals.net

import com.raphydaphy.crochet.network.IPacket
import com.raphydaphy.crochet.network.MessageHandler
import de.johni0702.minecraft.betterportals.LOGGER
import de.johni0702.minecraft.betterportals.MOD_ID
import de.johni0702.minecraft.betterportals.common.entity.AbstractPortalEntity
import de.johni0702.minecraft.betterportals.common.sync
import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.network.PacketContext
import net.minecraft.util.Identifier
import net.minecraft.util.PacketByteBuf
import net.minecraftforge.fml.common.network.simpleimpl.IPacket
import net.minecraftforge.fml.common.network.simpleimpl.IPacketHandler
import net.minecraftforge.fml.common.network.simpleimpl.PacketContext

class UsePortal(
        var entityId: Int = 0
) : IPacket {

    override fun getID() = ID

    override fun read(buf: PacketByteBuf) {
        entityId = buf.readInt()
    }

    override fun write(buf: PacketByteBuf) {
        buf.writeInt(entityId)
    }

    internal companion object Handler : MessageHandler<UsePortal>() {
        val ID = Identifier(MOD_ID, "use_portal")

        override fun create() = UsePortal()

        override fun handle(ctx: PacketContext, message: UsePortal) {
            ctx.sync {
                val player = ctx.serverHandler.player
                if (player.connection.targetPos != null) {
                    LOGGER.warn("Ignoring use portal request from $player because they have an outstanding teleport.")
                    return@sync
                }
                val portalEntity = player.world.getEntityByID(message.entityId) as? AbstractPortalEntity
                if (portalEntity == null) {
                    LOGGER.warn("Received use portal request from $player for unknown entity ${message.entityId}.")
                    return@sync
                }
                portalEntity.usePortal(player)
            }
        }
    }
}