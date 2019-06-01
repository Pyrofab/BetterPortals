package de.johni0702.minecraft.betterportals.client.renderer

import com.mojang.blaze3d.platform.GlStateManager
import de.johni0702.minecraft.betterportals.BPConfig
import de.johni0702.minecraft.betterportals.BetterPortalsMod
import de.johni0702.minecraft.betterportals.client.*
import de.johni0702.minecraft.betterportals.client.view.ClientView
import de.johni0702.minecraft.betterportals.client.view.ClientViewImpl
import de.johni0702.minecraft.betterportals.common.*
import de.johni0702.minecraft.betterportals.common.entity.AbstractPortalEntity
import net.minecraft.client.MinecraftClient
import net.minecraft.client.world.ClientWorld
import net.minecraft.util.math.BoundingBox
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World
import org.lwjgl.opengl.GL11
import java.util.*

class ViewRenderManager {
    companion object {
        val INSTANCE = ViewRenderManager()
    }
    private var frameWidth = 0
    private var frameHeight = 0
    private val framebufferPool = mutableListOf<FramebufferD>()
    private val eventHandler = EventHandler()
    init {
        eventHandler.registered = true
    }
    private val occlusionQueries = mutableMapOf<AbstractPortalEntity, OcclusionQuery>()
    private val disposedOcclusionQueries = mutableListOf<OcclusionQuery>()
    var fogOffset: Float
        get() = eventHandler.fogOffset
        set(value) {
            eventHandler.fogOffset = value
        }

    fun allocFramebuffer() = framebufferPool.popOrNull() ?: FramebufferD(frameWidth, frameHeight)

    fun releaseFramebuffer(framebuffer: FramebufferD) {
        framebufferPool.add(framebuffer)
    }

    fun getOcclusionQuery(entity: AbstractPortalEntity) = occlusionQueries.getOrPut(entity, ::OcclusionQuery)

    /**
     * Determine the camera's current world, prepare all portals and render the world.
     */
    fun renderWorld(partialTicks: Float, finishTimeNano: Long) {
        val mc = MinecraftClient.getInstance()

        if (mc.window.width != frameWidth || mc.window.height != frameHeight) {
            frameWidth = mc.window.width
            frameHeight = mc.window.height
            framebufferPool.forEach { it.delete() }
            framebufferPool.clear()
        }

        mc.profiler.startSection("determineVisiblePortals")
        val viewEntity = mc.cameraEntity ?: mc.player
        var view = BetterPortalsMod.viewManager.mainView
        (view as ClientViewImpl).captureState(mc) // capture main view camera
        val entityPos = viewEntity.syncPos + viewEntity.eyeOffset
        val interpEntityPos = viewEntity.getEyeHeight(partialTicks)
        // TODO do third person camera
        var cameraPos = interpEntityPos
        var cameraYaw = viewEntity.prevYaw + (viewEntity.yaw - viewEntity.prevYaw) * partialTicks.toDouble()

        var parentPortal: AbstractPortalEntity? = null

        // Ray trace from the entity (eye) position backwards to the camera position, following any portals which therefore
        // the camera must be looking through.
        // First back through time from the actual entity position to the interpolated (visual) position in this frame
        // (which may very well still be in the previous world, then through space from the visual position to where
        // the camera is positioned.
        var pos = entityPos
        var target = interpEntityPos
        var hitVisualPosition = false
        while (true) {
            val hitInfo = (view.camera.world as ClientWorld).entities
                .filterIsInstance<AbstractPortalEntity>()
                .filter {
                    val view = it?.view
                    // FIXME handle one-way portals
                    // Ignore portals which haven't yet been loaded or have already been destroyed
                    view != null && !it.invalid
                            // or have already been used in the previous iteration
                            && (view.camera.world != parentPortal?.world || it.localPosition != parentPortal?.remotePosition)
                }.flatMap { portal ->
                    // For each portal, find the point intercepting the line between entity and camera
                    val vec = portal.localFacing.vector.to3d() * 0.5
                    val negVec = vec * -1
                    portal.localBlocks.map {
                        // contract BB to only detect changes crossing 0.5 on the portal axis instead of hitting anywhere in the block
                        val trace = BoundingBox(it).contract(vec).contract(negVec).calculateIntercept(pos, target)
                        Pair(portal, trace)
                    }.filter {
                        it.second != null
                    }.map { (portal, trace) ->
                        // and calculate its distance to the entity
                        val hitVec = trace!!.hitVec
                        Pair(Pair(portal, hitVec), (hitVec - pos).lengthSquared())
                    }
                }.minBy {
                    // then get the one which is closest to the entity
                    it.second
                }?.first

            if (hitInfo != null) {
                val (portal, hitVec) = hitInfo

                // If we hit a portal, switch to its view and transform the camera/entity positions accordingly
                // also change the current position to be in the portal so we don't accidentally match any portals
                // behind the one we're looking through.
                view = portal.view!!
                target = (portal.localToRemoteMatrix * target.toPoint()).toMC()
                cameraPos = (portal.localToRemoteMatrix * cameraPos.toPoint()).toMC()
                cameraYaw += (portal.remoteRotation - portal.localRotation).degrees.toDouble()
                pos = (portal.localToRemoteMatrix * hitVec.toPoint()).toMC()
                parentPortal = portal
            } else if (!hitVisualPosition) {
                hitVisualPosition = true
                pos = target
                target = cameraPos
            } else {
                break
            }
        }

        mc.mcProfiler.endSection()

        // Capture camera properties (rotation, fov)
        GlStateManager.pushMatrix()
        eventHandler.capture = true
        eventHandler.mainCameraYaw = cameraYaw.toFloat()
        val camera = view.withView {
            mc.entityRenderer.setupCameraTransform(partialTicks, 0)
            val entity = mc.renderViewEntity!!
            val entityPos = entity.lastTickPos + (entity.pos - entity.lastTickPos) * partialTicks.toDouble()
            FrustumWithOrigin().apply { setOrigin(entityPos.x, entityPos.y, entityPos.z) }
        }
        eventHandler.capture = false
        GlStateManager.popMatrix()

        val maxRecursions = if (BPConfig.seeThroughPortals) 5 else 0

        // Build render plan
        val plan = ViewRenderPlan(this, null, view, camera, cameraPos, cameraYaw.toFloat(), maxRecursions)

        // Update occlusion queries
        occlusionQueries.values.forEach { it.update() }

        // Cleanup occlusion queries for portals which are no longer visible
        val knownPortals = plan.allPortals.toSet()
        occlusionQueries.entries.removeIf { (portal, query) ->
            if (knownPortals.contains(portal)) {
                false
            } else {
                disposedOcclusionQueries.add(query)
                true
            }
        }
        disposedOcclusionQueries.removeIf { it.update() }

        // execute
        mc.framebuffer.unbindFramebuffer()
        ViewRenderPlan.MAIN = plan
        val framebuffer = plan.render(partialTicks, finishTimeNano)
        ViewRenderPlan.MAIN = null
        mc.framebuffer.bindFramebuffer(true)

        mc.mcProfiler.startSection("renderFramebuffer")
        framebuffer.framebufferRender(frameWidth, frameHeight)
        mc.mcProfiler.endSection()

        releaseFramebuffer(framebuffer)
    }

    private inner class EventHandler {
        var registered by MinecraftForge.EVENT_BUS

        var capture = false
        var mainCameraYaw = 0.toFloat()
        var fogOffset = 0.toFloat()
        private var yaw = 0.toFloat()
        private var pitch = 0.toFloat()
        private var roll = 0.toFloat()

        @SubscribeEvent(priority = EventPriority.LOWEST)
        fun onCameraSetup(event: EntityViewRenderEvent.CameraSetup) {
            if (capture) {
                yaw = event.yaw
                pitch = event.pitch
                roll = event.roll
            } else {
                val plan = ViewRenderPlan.CURRENT ?: return
                event.yaw = yaw - mainCameraYaw + plan.cameraYaw
                event.pitch = pitch
                event.roll = roll
            }
        }

        private var fov: Float = 0.toFloat()
        @SubscribeEvent(priority = EventPriority.LOWEST)
        fun onFOVSetup(event: EntityViewRenderEvent.FOVModifier) {
            if (capture) {
                fov = event.fov
            } else {
                event.fov = fov
            }
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        fun postSetupFog(event: PostSetupFogEvent) {
            if (fogOffset != 0f) {
                GlStateManager.setFogStart(GlStateManager.fogState.start + fogOffset)
                GlStateManager.setFogEnd(GlStateManager.fogState.end + fogOffset)
            }
        }

        @SubscribeEvent(priority = EventPriority.LOW)
        fun onRenderBlockHighlights(event: DrawBlockHighlightEvent) {
            val plan = ViewRenderPlan.CURRENT ?: return
            // Render block outlines only in main view (where the player entity is located)
            if (!plan.view.isMainView) {
                event.isCanceled = true
            }
        }
    }
}

class ViewRenderPlan(
        val manager: ViewRenderManager,
        val parentPortal: AbstractPortalEntity?,
        val view: ClientView,
        val camera: Frustum,
        val cameraPos: Vec3d,
        val cameraYaw: Float,
        val maxRecursions: Int
) {
    companion object {
        var MAIN: ViewRenderPlan? = null
        var CURRENT: ViewRenderPlan? = null
    }
    val world: World = view.camera.world

    val dependencies = if (maxRecursions > 0)
        world.getEntities(AbstractPortalEntity::class.java) {
            // portal must be visible (i.e. must not be frustum culled)
            it!!.canBeSeen(camera)
                    // its view must have been loaded (otherwise there's nothing to render)
                    && it.view != null
                    // it must not be our parent (the portal from which this world is being viewed)
                    // that is, it must either link to a different world or to a different place than our parent portal
                    && (it.view != parentPortal?.view || it.remotePosition != parentPortal?.localPosition)
                    // it must not be occluded by blocks
                    && !manager.getOcclusionQuery(it).occluded
        }.map { portal ->
            val rotation = portal.remoteRotation - portal.localRotation
            val cameraYaw = this.cameraYaw + rotation.degrees.toFloat()
            val cameraPos = with(this.cameraPos) { portal.localToRemoteMatrix * Point3d(x, y, z) }.toMC()
            val camera = PortalCamera(portal, cameraPos, camera)
            val plan = ViewRenderPlan(manager, portal, portal.view!!, camera, cameraPos, cameraYaw, maxRecursions - 1)
            Pair(portal, plan)
        }
    else
        Collections.emptyList()

    val framebuffers = mutableMapOf<AbstractPortalEntity, FramebufferD>()

    val allPortals: List<AbstractPortalEntity>
        get() = listOfNotNull(parentPortal) + dependencies.flatMap { it.second.allPortals }

    /**
     * Render all dependencies of this view (including transitive ones).
     */
    private fun renderDeps(partialTicks: Float) = dependencies.map { (portal, plan) ->
        portal.onUpdate() // Update position (and other state) of the view entity
        val framebuffer = plan.render(partialTicks, 0)
        framebuffers[portal] = framebuffer
        framebuffer
    }

    /**
     * Render this view.
     * Requires all dependencies to have previously been rendered (e.g. by calling [renderDeps]), otherwise their
     * portals will be empty.
     */
    private fun renderSelf(partialTicks: Float, finishTimeNano: Long): FramebufferD {
        if (view.manager.activeView != view) {
            return view.withView { renderSelf(partialTicks, finishTimeNano) }
        }
        val mc = MinecraftClient.getMinecraft()
        val framebuffer = manager.allocFramebuffer()

        world.profiler.startSection("renderView" + view.id)
        framebuffer.bindFramebuffer(false)
        GlStateManager.pushMatrix()

        // Clear framebuffer
        GlStateManager.disableFog()
        GlStateManager.disableLighting()
        mc.entityRenderer.disableLightmap()
        mc.entityRenderer.updateFogColor(partialTicks)
        GL11.glClearDepth(1.0)
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT or GL11.GL_DEPTH_BUFFER_BIT)

        if (parentPortal != null) {
            // Setup clipping plane for parent portal
            // The render is supposed to look like this from the parent (we are currently rendering Remote World):
            // Camera -> Local World -> Portal -> Remote World
            // However, we are actually rendering:
            // Camera -> Remote World -> Portal -> Remote World
            // so we need to clip away the remote world on the camera side of the portal, otherwise blocks in the
            // Remote World which are between the camera and the portal will show up in the final composed render.
            val portalPos = parentPortal.remotePosition.to3dMid()
            val cameraSide = parentPortal.remoteAxis.toFacing(cameraPos - portalPos)
            // Position clipping plane on the camera side of the portal such that the portal frame is fully rendered
            val planePos = parentPortal.remotePosition.to3dMid() + cameraSide.directionVec.to3d().scale(0.5)
            // glClipPlane uses the current ModelView matrix to transform the given coordinates to view space
            // so we need to have the camera setup before calling it
            mc.entityRenderer.setupCameraTransform(partialTicks, 0)
            // setupCameraTransform configures world space with the origin at the camera's feet.
            // planePos however is currently absolute world space, so we need to convert it
            val relPlanePos = planePos - cameraPos + mc.renderViewEntity!!.eyeOffset
            glClipPlane(GL11.GL_CLIP_PLANE5, cameraSide.directionVec.to3d().scale(-1.0), relPlanePos)
            GL11.glEnable(GL11.GL_CLIP_PLANE5) // FIXME don't hard-code clipping plane id

            // Reduce fog by distance between camera and portal, we will later re-apply this distance worth of fog
            // to the rendered portal but then with the fog of the correct dimension.
            // This won't give quite correct results for large portals but far better ones than using the incorrect fog.
            val dist = (cameraPos - portalPos).lengthVector().toFloat()
            when (GlStateManager.FogMode.values().find { it.capabilityId == GlStateManager.fogState.mode }) {
                GlStateManager.FogMode.LINEAR -> manager.fogOffset = dist
                // TODO
                else -> manager.fogOffset = 0f
            }
        }

        // Actually render the world
        val prevRenderPlan = ViewRenderPlan.CURRENT
        ViewRenderPlan.CURRENT = this
        mc.entityRenderer.renderWorld(partialTicks, finishTimeNano)
        ViewRenderPlan.CURRENT = prevRenderPlan

        manager.fogOffset = 0f
        GlStateManager.popMatrix()
        GL11.glDisable(GL11.GL_CLIP_PLANE5) // FIXME don't hard-code clipping plane id
        framebuffer.unbindFramebuffer()
        world.profiler.endSection()
        return framebuffer
    }

    /**
     * Render this view and all of its dependencies.
     */
    fun render(partialTicks: Float, finishTimeNano: Long): FramebufferD = try {
        renderDeps(partialTicks)
        MinecraftForge.EVENT_BUS.post(PreRenderView(this, partialTicks))
        renderSelf(partialTicks, finishTimeNano)
    } finally {
        framebuffers.values.forEach(manager::releaseFramebuffer)
        framebuffers.clear()
    }
}