package de.johni0702.minecraft.betterportals.client.view

import de.johni0702.minecraft.betterportals.LOGGER
import de.johni0702.minecraft.betterportals.client.UtilsClient
import de.johni0702.minecraft.betterportals.common.popOrNull
import de.johni0702.minecraft.betterportals.common.pos
import de.johni0702.minecraft.betterportals.common.removeAtOrNull
import io.netty.buffer.ByteBuf
import net.minecraft.client.Minecraft
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.client.multiplayer.WorldClient
import net.minecraft.crash.CrashReport
import net.minecraft.network.play.client.CPacketPlayer
import net.minecraft.util.ReportedException
import net.minecraft.util.crash.CrashReport
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import net.minecraft.world.Difficulty
import net.minecraft.world.EnumDifficulty
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import net.minecraftforge.fml.common.network.FMLNetworkEvent

internal class ClientViewManagerImpl : ClientViewManager {
    val mc: MinecraftClient = MinecraftClient.getInstance()

    override val player: ClientPlayerEntity
        get() = mc.player

    override var mainView: ClientViewImpl = ClientViewImpl(this, 0, null, null, null, null).apply {
        captureState(mc)
    }

    private val unusedViews = mutableListOf<ClientViewImpl>()

    override val views = mutableListOf(mainView)

    override var activeView = mainView

    /**
     * Differs from [mainView] only after a call to [makeMainView] until the server acknowledges the switch.
     * For that duration, this is still the old mainView and is the view with the actual network connection.
     */
    internal var serverMainView = mainView

    /**
     * Queue containing the ids of views which we have made the main view but which have not yet been confirmed by
     * the server.
     * Specifically contains Triple(oldMainView, newMainView, newMainView.camera.position).
     * New ids are appended to the end. Whenever the server confirms one transaction, the first element will be removed.
     * See [ViewEntity.setPositionAndRotation] for why this is necessary.
     */
    private val serverMainViewQueue = mutableListOf<Triple<ClientViewImpl, ClientViewImpl, Vec3d>>()

    private fun reset() {
        views.remove(mainView)
        mainView = ClientViewImpl(this, 0, null, null, null, null).also { it.captureState(mc) }
        serverMainView = mainView
        activeView = mainView

        unusedViews.addAll(views)
        views.clear()
        views.add(mainView)
    }

    fun init() {
        MinecraftForge.EVENT_BUS.register(EventHandler())
    }

    fun createView(viewId: Int, world: WorldClient): ClientView? {
        if (views.find { it.id == viewId } != null) {
            throw IllegalArgumentException("View id $viewId is already taken")
        }
        return try {
            ClientViewImpl.reuseOrCreate(this, viewId, world, unusedViews.popOrNull()).also { views.add(it) }
        } catch (t: Throwable) {
            LOGGER.error("Creating world view:", t)
            views[viewId] = ClientViewImpl(this, viewId, null, null, null, null)
            null
        }
    }

    fun destroyView(view: ClientViewImpl) {
        LOGGER.debug("Removing view {}", view)
        if (activeView != mainView) throw IllegalStateException("Main view must be active")
        if (view == mainView) throw IllegalArgumentException("Cannot remove main view")

        view.withView {
            mc.renderGlobal.setWorldAndLoadRenderers(null)
        }

        if (view.camera is ViewEntity) view.world?.removeEntity(view.camera)
        if (!views.remove(view)) throw IllegalStateException("View $view has already been destroyed")
        unusedViews.add(view)
    }

    fun handleViewData(serverViewId: Int, data: ByteBuf) {
        try {
            val view = views.find { it.id == serverViewId }
            if (view == null) {
                LOGGER.warn("Received data for unknown view {}", serverViewId)
                return
            }
            val channel = view.channel
            if (channel != null) {
                view.withView {
                    data.retain()
                    channel.writeInbound(data)
                }
            } else {
                LOGGER.warn("Received data for main view {} via ViewData message", view)
            }
        } catch (t: Throwable) {
            LOGGER.error("Handling view data for view $serverViewId:", t)
        }
    }

    private var withViewDepth = 0

    override fun <T> withView(view: ClientView, block: () -> T): T {
        if (view == activeView) return block()
        if (view !is ClientViewImpl) throw UnsupportedOperationException("Unsupported ClientView impl: ${view::class}")
        val previousView = activeView
        previousView.captureState(mc)
        view.restoreState(mc)
        activeView = view
        withViewDepth++
        try {
            return block()
        } finally {
            if (withViewDepth > 0) {
                withViewDepth--
                activeView = previousView
                view.captureState(mc)
                previousView.restoreState(mc)
            }
        }
    }

    internal fun makeMainView(newMainView: ClientViewImpl) {
        with(mainView.camera) {
            connection.sendPacket(CPacketPlayer.PositionRotation(
                    posX, entityBoundingBox.minY, z, rotationYaw, rotationPitch, onGround))
        }
        val oldMainView = mainView

        makeClientMainView(newMainView)

        serverMainViewQueue.add(Triple(oldMainView, newMainView, oldMainView.camera.pos))

        // TODO we might be sending the wrong packets from here till the ack (the ones from the view entity)
    }

    /**
     * Rewinds all changes of main view which haven't been confirmed by the server.
     * Must only be called when it is safe to discard withView stack and call makeMainView (see comments in method body)
     */
    internal fun rewindMainView() {
        if (serverMainViewQueue.isEmpty()) return

        LOGGER.warn("Got teleport in old main view, rewinding main view changes to before that change..")

        // Usually the main view can only be safely changed if it is currently active with no withView calls (otherwise
        // withView will restore the wrong state once it's finished and not end up with the mainView in the end).
        // Here however, we know the call path (exactly one withView(serverMainView)) and therefore also know that
        // no significant changes to any withView views will be made before returning to the mainView after exiting this
        // method. We can therefore just store the active view state here and discard the withView stack.
        activeView.captureState(mc)
        withViewDepth = 0
        // Switch to main view here and now
        activeView = mainView
        activeView.restoreState(mc)

        // Undo all previous changes in reverse order, this should always leave us with [mainView] == [serverMainView].
        serverMainViewQueue.asReversed().forEach { (oldMainView, newMainView, newCamPos) ->
            makeClientMainView(oldMainView)
            newMainView.camera.setPosition(newCamPos.x, newCamPos.y, newCamPos.z)
        }

        // Clear change queue
        serverMainViewQueue.clear()
    }

    private fun makeClientMainView(newMainView: ClientViewImpl) {
        if (withViewDepth > 0) throw IllegalStateException("Cannot change main view while inside ClientView.withView")
        if (activeView != mainView) throw IllegalStateException("Needs to be called with the current main view active")

        val oldMainView = mainView

        val viewPlayer = newMainView.camera
        val mainPlayer = oldMainView.camera

        LOGGER.info("Swapping main view $oldMainView with $newMainView")

        val viewWorld = viewPlayer.world
        val mainWorld = mainPlayer.world

        // Capture all (possibly modified) state into the main view
        oldMainView.captureState(mc)

        // Transfer camera entities from their world to the other one
        viewWorld?.removeEntityDangerously(viewPlayer)
        mainWorld?.removeEntityDangerously(mainPlayer)

        mainPlayer.isDead = false
        viewPlayer.isDead = false

        mainPlayer.setWorld(viewWorld)
        viewPlayer.setWorld(mainWorld)

        mainPlayer.dimension = viewPlayer.dimension.also { viewPlayer.dimension = mainPlayer.dimension }

        UtilsClient.swapPosRot(mainPlayer, viewPlayer)

        viewWorld?.spawnEntity(mainPlayer)
        mainWorld?.spawnEntity(viewPlayer)

        // And swap the views they belong to
        newMainView.player = mainPlayer
        oldMainView.player = viewPlayer

        // Sync some additional state from the old main view to the new one
        newMainView.copyRenderState(oldMainView)

        // Switch main view
        mainView = newMainView
        activeView = newMainView
        newMainView.restoreState(mc)
    }

    fun makeMainViewAck(viewId: Int) {
        LOGGER.info("Ack for swap of {}", viewId)


        val expectedId = serverMainViewQueue.getOrNull(0)?.second?.id
        if (expectedId != viewId) {
            // We haven't requested this change, someone must have called `PlayerManager.transferPlayerToDimension` on the server.
            // Rewind all view changes which haven't yet been confirmed by the server (it'll ignore them because it decided for us to go elsewhere)
            rewindMainView()
            // So, let's act as if we did request it
            makeClientMainView(views.find { it.id == viewId }!!)
        }
        serverMainViewQueue.removeAtOrNull(0)

        val newMainView = views.find { it.id == viewId }!!
        val oldMainView = serverMainView

        activeView.captureState(mc)

        oldMainView.channel = newMainView.channel.also { newMainView.channel = oldMainView.channel }
        oldMainView.netManager = newMainView.netManager.also { newMainView.netManager = oldMainView.netManager }

        serverMainView = newMainView

        activeView.restoreState(mc)
    }

    private fun tickViews() {
        if (mc.isGamePaused) {
            return
        }

        mc.profiler.push("tickViews")

        views.filter { !it.isMainView }.forEach { view ->
            view.withView {
                tickView()
            }
        }

        mc.profiler.endSection()
    }

    private fun tickView() {
        if (mc.entityRenderer == null) return

        mc.profiler.push(activeView.id.toString())

        mc.entityRenderer.getMouseOver(1.0F)

        mc.profiler.push("gameRenderer")

        mc.entityRenderer.updateRenderer()

        mc.profiler.swap("levelRenderer")

        mc.renderGlobal.updateClouds()

        mc.profiler.swap("level")

        if (mc.world.lastLightningBolt > 0) {
            mc.world.lastLightningBolt = mc.world.lastLightningBolt - 1
        }

        mc.world.updateEntities()

        mc.world.setAllowedSpawnTypes(mc.world.difficulty != Difficulty.PEACEFUL, true)

        try {
            mc.world.tick()
        } catch (t: Throwable) {
            val crash = CrashReport.makeCrashReport(t, "Exception in world tick")
            mc.world.addWorldInfoToCrashReport(crash)
            throw ReportedException(crash)
        }

        mc.profiler.swap("animateTick")

        mc.world.doVoidFogParticles(MathHelper.floor(mc.player.posX), MathHelper.floor(mc.player.y), MathHelper.floor(mc.player.z))

        mc.profiler.swap("particles")

        mc.effectRenderer.updateEffects()

        mc.profiler.endSection()
        mc.profiler.endSection()
    }

    private fun preRender() {
        // Make sure the stencil buffer is enabled
        if (!mc.framebuffer.isStencilEnabled) {
            mc.framebuffer.enableStencil()
        }
        // Disable portal animation
        mc.player?.timeInPortal = 0f
        mc.player?.prevTimeInPortal = 0f
        mc.player?.timeUntilPortal = 200
    }

    private inner class EventHandler {

        @SubscribeEvent(priority = EventPriority.LOWEST)
        fun postClientTick(event: TickEvent.ClientTickEvent) {
            if (event.phase != TickEvent.Phase.END) return

            tickViews()
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        fun preClientRender(event: TickEvent.RenderTickEvent) {
            if (event.phase != TickEvent.Phase.START) return

            preRender()
        }

        @SubscribeEvent(priority = EventPriority.LOW)
        fun onDisconnect(event: FMLNetworkEvent.ClientDisconnectionFromServerEvent) {
            reset()
        }
    }
}