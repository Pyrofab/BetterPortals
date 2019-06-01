package de.johni0702.minecraft.betterportals.net

import com.raphydaphy.crochet.network.IPacket
import com.raphydaphy.crochet.network.MessageHandler
import de.johni0702.minecraft.betterportals.BetterPortalsMod
import de.johni0702.minecraft.betterportals.LOGGER
import de.johni0702.minecraft.betterportals.MOD_ID
import de.johni0702.minecraft.betterportals.common.entity.AbstractPortalEntity
import de.johni0702.minecraft.betterportals.common.sync
import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.network.PacketContext
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.PacketBuffer
import net.minecraft.util.Identifier
import net.minecraft.util.PacketByteBuf
import net.minecraftforge.fml.common.network.simpleimpl.IPacket
import net.minecraftforge.fml.common.network.simpleimpl.IPacketHandler
import net.minecraftforge.fml.common.network.simpleimpl.PacketContext

class LinkPortal(
        var entityId: Int = 0,
        var nbt: CompoundTag? = null,
        var viewId: Int? = 0
) : IPacket {

    override fun getID() = ID

    override fun read(buf: PacketByteBuf) {
        with(buf) {
            entityId = readVarInt()
            nbt = readCompoundTag()
            viewId = if (readBoolean()) {
                readVarInt()
            } else {
                null
            }
        }
    }

    override fun write(buf: PacketByteBuf) {
        with(buf) {
            writeVarInt(entityId)
            writeCompoundTag(nbt)
            val viewId = viewId
            if (viewId != null) {
                writeBoolean(true)
                writeVarInt(viewId)
            } else {
                writeBoolean(false)
            }
            return
        }
    }

    internal companion object Handler : MessageHandler<LinkPortal>() {
        val ID = Identifier(MOD_ID, "link_portal")
        override fun create() = LinkPortal()
        override fun handle(ctx: PacketContext, message: LinkPortal) {
            ctx.sync {
                val world = ctx.clientHandler.clientWorldController
                val entity = world.getEntityByID(message.entityId) as? AbstractPortalEntity
                if (entity == null) {
                    LOGGER.warn("Received sync message for unknown portal entity ${message.entityId}")
                    return@sync
                }
                message.nbt?.let { entity.readPortalFromNBT(it) }

                val viewId = message.viewId
                if (viewId != null) {
                    val view = BetterPortalsMod.viewManager.views.find { it.id == message.viewId }
                    if (view == null) {
                        LOGGER.warn("Received sync message with unknown view id ${message.viewId} for portal $entity")
                        return@sync
                    }
                    entity.view = view
                } else {
                    entity.view = null
                }
            }
        }
    }
}