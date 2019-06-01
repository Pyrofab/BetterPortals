package de.johni0702.minecraft.betterportals.server.view

import com.mojang.authlib.GameProfile
import com.raphydaphy.crochet.network.PacketHandler
import de.johni0702.minecraft.betterportals.LOGGER
import de.johni0702.minecraft.betterportals.net.CreateView
import de.johni0702.minecraft.betterportals.net.DestroyView
import de.johni0702.minecraft.betterportals.net.ViewData
import de.johni0702.minecraft.betterportals.net.sendTo
import de.johni0702.minecraft.betterportals.server.NettyExceptionHandler
import io.netty.buffer.ByteBuf
import io.netty.channel.embedded.EmbeddedChannel
import net.minecraft.entity.player.ServerPlayerEntity
import net.minecraft.network.NetworkSide
import net.minecraft.network.NetworkState
import net.minecraft.network.PacketEncoder
import net.minecraft.network.SizePrepender
import net.minecraft.network.play.server.SPacketDestroyEntities
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayNetworkHandler
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.math.Vec3d
import net.minecraft.server.world.ServerWorld
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.world.WorldEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.PlayerEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import net.minecraftforge.fml.common.network.handshake.NetworkDispatcher
import org.jetbrains.annotations.NotNull
import java.util.*

internal class ServerViewManagerImpl(
    val server: @NotNull MinecraftServer,
    val connection: ServerPlayNetworkHandler
) : ServerViewManager {

    override val player: ServerPlayerEntity
        get() = connection.player

    override var mainView = ServerViewImpl(this, 0, player, null)

    override val views = mutableListOf(mainView)

    private val eventHandler = EventHandler()
    private var nextViewId = 1

    init {
        eventHandler.registered = true
    }

    override fun createView(world: ServerWorld, pos: Vec3d, beforeSendChunks: ServerPlayerEntity.() -> Unit): ServerView {
        val id = nextViewId++
        val gameProfile = GameProfile(UUID.randomUUID(), connection.player.gameProfile.name + "[view]")
        val camera = ViewEntity(world, gameProfile, connection)
        camera.setPosition(pos.x, pos.y, pos.z) // No update (networking not yet set up)

        val channel = EmbeddedChannel()
        channel.pipeline()
                .addLast("prepender", SizePrepender())
                .addLast("encoder", PacketEncoder(NetworkSide.CLIENTBOUND))
                .addLast("exception_handler", NettyExceptionHandler(connection))
                .addLast("packet_handler", camera.networkHandler.connection)
                .fireChannelActive()
        camera.networkHandler.connection.setState(NetworkState.PLAY)

/*
        val networkDispatcher = NetworkDispatcher.allocAndSet(camera.connection.networkManager, server.PlayerManager)
        channel.pipeline().addBefore("packet_handler", "fml:packet_handler", networkDispatcher)

        val stateField = NetworkDispatcher::class.java.getDeclaredField("state")
        val connectedState = stateField.type.asSubclass(Enum::class.java).enumConstants.last()
        stateField.isAccessible = true
        stateField.set(networkDispatcher, connectedState)
*/

        val view = ServerViewImpl(this, id, camera, channel)
        views.add(view)

        CreateView(id, camera.dimension, world.difficulty, world.server.playerManager.viewDistance,
                world.levelProperties.gameMode, world.generatorType).sendTo(connection.player)
        world.spawnEntity(camera)
        beforeSendChunks(camera)
//        server.playerManager.preparePlayer(camera, null)
        server.playerManager.sendWorldInfo(camera, world)
        camera.networkHandler.requestTeleport(camera.x, camera.y, camera.z, camera.yaw, camera.pitch)

        // Ensure the view entity position and world is synced to the client
        flushPackets()
        return view
    }

    internal fun destroyView(view: ServerViewImpl) {
        if (!connection.client.isOpen) return

        // Flush packets before actually removing the view,
        // otherwise entities referencing the view (e.g. portals) might not yet have been removed on the client
        flushPackets()

        if (!views.remove(view)) {
            throw RuntimeException("unknown view $view")
        }
        DestroyView(view.id).sendTo(connection.player)

        val camera = view.camera
        val world = camera.serverWorld
        world.removeEntity(camera)
        world.playerChunkMap.removePlayer(camera)
    }

    private fun destroy() {
        eventHandler.registered = false

        views.forEach { view ->
            if (view.isMainView) return@forEach
            val camera = view.camera
            val world = camera.serverWorld
            world.removeEntity(camera)
            world.playerChunkMap.removePlayer(camera)
        }
        views.clear()
    }

    private fun tick() {
        views.filter { it.refCnt == 0 }.forEach {
            if (it.isMainView) {
                LOGGER.warn("Main view of $player somehow reached a refCnt of 0!")
                it.retain()
                return@forEach
            }
            destroyView(it)
        }

        flushPackets()
    }

    override fun flushPackets() {
        // For some reason MC queues up removed entity ids instead of sending them directly (maybe to save packets?).
        // Anyhow, we need them sent out right now.
        val flushEntityPackets = { player: ServerPlayerEntity ->
            if (player.entityRemoveQueue.isNotEmpty()) {
                player.connection.sendPacket(SPacketDestroyEntities(*(player.entityRemoveQueue.toIntArray())))
                player.entityRemoveQueue.clear()
            }
        }
        flushEntityPackets(connection.player)
        views.forEach { flushEntityPackets(it.camera) }

        // Flush view packets via main connection
        views.forEach { view ->
            view.channel?.outboundMessages()?.onEach {
                ViewData(view.id, it as ByteBuf).sendTo(connection.player)
            }?.clear()
        }
    }

    private inner class EventHandler {
        var registered by MinecraftForge.EVENT_BUS

        @SubscribeEvent
        fun onPlayerLeft(event: PlayerEvent.PlayerLoggedOutEvent) {
            if ((event.player as? ServerPlayerEntity)?.connection === connection) {
                destroy()
            }
        }

        @SubscribeEvent
        fun postTick(event: TickEvent.ServerTickEvent) {
            if (event.phase != TickEvent.Phase.END) return

            tick()
        }

        @SubscribeEvent
        fun onWorldUnload(event: WorldEvent.Unload) {
            views.filter { !it.isMainView && it.camera.world === event.world }.forEach {
                if (it.refCnt > 0) {
                    LOGGER.warn("View $it has a refCnt of ${it.refCnt} even though its world is unloaded!")
                }
                destroyView(it)
            }
        }

        @SubscribeEvent
        fun onPlayerRespawn(event: PlayerEvent.PlayerRespawnEvent) {
            val player = event.player
            if (player is ServerPlayerEntity && player.connection === connection) {
                mainView.camera = player
            }
        }
    }
}