package de.johni0702.minecraft.betterportals.server.view

import com.mojang.authlib.GameProfile
import net.minecraft.entity.Entity
import net.minecraft.entity.damage.DamageSource
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.network.ClientConnection
import net.minecraft.network.NetworkSide
import net.minecraft.network.chat.ChatMessageType
import net.minecraft.network.chat.Component
import net.minecraft.server.network.ServerPlayNetworkHandler
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.network.ServerPlayerInteractionManager
import net.minecraft.server.network.packet.ClientSettingsC2SPacket
import net.minecraft.server.world.ServerWorld
import net.minecraft.stat.Stat
import net.minecraft.world.GameMode
import net.minecraft.world.dimension.DimensionType

internal class ViewEntity(world: ServerWorld, profile: GameProfile, val parentConnection: ServerPlayNetworkHandler)
    : ServerPlayerEntity(world.server, world, profile, ServerPlayerInteractionManager(world)) {
    init {
        interactionManager.gameMode = GameMode.SPECTATOR
        this.networkHandler = ServerPlayNetworkHandler(world.server, ClientConnection(NetworkSide.SERVERBOUND), this)
    }

    override fun canBeSpectated(player: ServerPlayerEntity): Boolean = false
    override fun getPermissionLevel(): Int = 0
    override fun addChatMessage(chatComponent: Component?, actionBar: Boolean) {}
    override fun sendChatMessage(message: Component?, type: ChatMessageType?) {}
    override fun increaseStat(stat: Stat<*>?, amount: Int) {}
//    override fun openGui(mod: Any?, modGuiId: Int, world: World?, x: Int, y: Int, z: Int) {}
    override fun isInvulnerableTo(source: DamageSource?): Boolean = true
    override fun shouldDamagePlayer(player: PlayerEntity?): Boolean = false
    override fun onDeath(source: DamageSource?) {}
    override fun tick() {}
    override fun changeDimension(dim: DimensionType): Entity? = this
    override fun setClientSettings(pkt: ClientSettingsC2SPacket?) {}
}
