package de.johni0702.minecraft.betterportals.net

import com.raphydaphy.crochet.network.IPacket
import com.raphydaphy.crochet.network.PacketHandler
import de.johni0702.minecraft.betterportals.MOD_ID
import net.fabricmc.fabric.api.network.ClientSidePacketRegistry
import net.fabricmc.fabric.api.network.ServerSidePacketRegistry
import net.minecraft.entity.player.ServerPlayerEntity
import net.minecraft.network.Packet
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraftforge.fml.common.network.NetworkRegistry
import net.minecraftforge.fml.common.network.simpleimpl.IPacket
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper
import net.minecraftforge.fml.relauncher.Side

object Net {
    init {
        with (ClientSidePacketRegistry.INSTANCE) {
            register(CreateView.ID, CreateView)
            register(ViewData.ID, ViewData)
            register(DestroyView.ID, DestroyView)
            register(ChangeServerMainView.ID, ChangeServerMainView)
            register(LinkPortal.ID, LinkPortal)
            register(EntityUsePortal.ID, EntityUsePortal)
            register(Transaction.ID, Transaction.Handler)
            register(TransferToDimension.ID, TransferToDimension)
        }
        with(ServerSidePacketRegistry.INSTANCE) {
            register(UsePortal.ID, UsePortal)
            register(TransferToDimensionDone.ID, TransferToDimensionDone)
        }
    }
}

fun IPacket.sendTo(players: Iterable<ServerPlayerEntity>) { players.forEach { PacketHandler.sendToClient(this, it) } }
fun IPacket.sendTo(vararg players: ServerPlayerEntity) { players.forEach { PacketHandler.sendToClient(this, it) } }
