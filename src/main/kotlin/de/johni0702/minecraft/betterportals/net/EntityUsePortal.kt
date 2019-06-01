package de.johni0702.minecraft.betterportals.net

import com.raphydaphy.crochet.network.IPacket
import com.raphydaphy.crochet.network.MessageHandler
import de.johni0702.minecraft.betterportals.LOGGER
import de.johni0702.minecraft.betterportals.MOD_ID
import de.johni0702.minecraft.betterportals.common.entity.AbstractPortalEntity
import de.johni0702.minecraft.betterportals.common.readEnum
import de.johni0702.minecraft.betterportals.common.sync
import de.johni0702.minecraft.betterportals.common.writeEnum
import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.network.PacketContext
import net.minecraft.network.PacketBuffer
import net.minecraft.util.Identifier
import net.minecraft.util.PacketByteBuf
import net.minecraftforge.fml.common.network.simpleimpl.IPacket
import net.minecraftforge.fml.common.network.simpleimpl.IPacketHandler
import net.minecraftforge.fml.common.network.simpleimpl.PacketContext

class EntityUsePortal(
        var phase: Phase = Phase.BEFORE,
        var entityId: Int = 0,
        var portalId: Int = 0
) : IPacket {

    override fun getID() = ID

    override fun read(buf: PacketByteBuf) {
        with(buf) {
            phase = readEnum()
            entityId = readVarInt()
            portalId = readVarInt()
        }
    }

    override fun write(buf: PacketByteBuf) {
        with(buf) {
            writeEnum(phase)
            writeVarInt(entityId)
            writeVarInt(portalId)
        }
    }

    internal companion object Handler : MessageHandler<EntityUsePortal>() {
        val ID = Identifier(MOD_ID, "entity_use_portal")
        override fun create() = EntityUsePortal()
        override fun handle(ctx: PacketContext, message: EntityUsePortal) {
            ctx.sync {
                val world = ctx.clientHandler.clientWorldController
                val portal = world.getEntityByID(message.portalId) as? AbstractPortalEntity
                if (portal == null) {
                    LOGGER.warn("Received EntityUsePortal for unknown portal with id ${message.portalId}")
                    return@sync
                }

                when(message.phase) {
                    Phase.BEFORE -> {
                        val entity = world.getEntityByID(message.entityId)
                        if (entity == null) {
                            LOGGER.warn("Received EntityUsePortal for unknown entity with id ${message.entityId}")
                            return@sync
                        }
                        portal.beforeUsePortal(entity)
                    }
                    Phase.AFTER -> portal.afterUsePortal(message.entityId)
                }
            }
        }
    }

    enum class Phase {
        BEFORE, AFTER
    }
}