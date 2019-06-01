package de.johni0702.minecraft.betterportals.client.renderer

import de.johni0702.minecraft.betterportals.client.FramebufferD
import de.johni0702.minecraft.betterportals.client.PreRenderView
import de.johni0702.minecraft.betterportals.client.UtilsClient
import de.johni0702.minecraft.betterportals.client.view.ClientView
import de.johni0702.minecraft.betterportals.common.*
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraft.client.shader.ShaderManager
import net.minecraftforge.client.event.RenderWorldLastEvent
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import org.lwjgl.opengl.GL11
import java.time.Duration

class TransferToDimensionRenderer(
        val fromView: ClientView,
        toView: ClientView,
        private val whenDone: () -> Unit,
        val duration: Duration = Duration.ofSeconds(10)
) {
    private val mc = MinecraftClient.getMinecraft()

    private val shader = ShaderManager(mc.resourceManager, "betterportals:dimension_transition")
    private val eventHandler = EventHandler()

    private val cameraYawOffset = fromView.camera.rotationYaw - toView.camera.rotationYaw
    private val cameraPosOffset =
            Mat4d.add(fromView.camera.pos.toJavaX()) * Mat4d.rotYaw(cameraYawOffset) * Mat4d.sub(toView.camera.pos.toJavaX())
    private var ticksPassed = 0
    private var framebuffer: FramebufferD? = null

    init {
        // Copy current pitch onto new camera to prevent sudden camera jumps when switching views
        UtilsClient.transformPosition(fromView.camera, toView.camera, Mat4d.inverse(cameraPosOffset), -cameraYawOffset)
    }

    private fun getProgress(partialTicks: Float) = ((ticksPassed + partialTicks) * 50 / duration.toMillis()).coerceIn(0f, 1f)

    init {
        eventHandler.registered = true
    }

    private fun renderOldView(mainPlan: ViewRenderPlan, partialTicks: Float) {
        framebuffer?.let { ViewRenderManager.INSTANCE.releaseFramebuffer(it) } // just in case

        // TODO this isn't quite right once we use a portal in the new view while the transition is still active
        UtilsClient.transformPosition(mainPlan.view.camera, fromView.camera, cameraPosOffset, cameraYawOffset)
        val plan = ViewRenderPlan(ViewRenderManager.INSTANCE, null, fromView, mainPlan.camera, fromView.camera.pos, mainPlan.cameraYaw + cameraYawOffset, 0)
        framebuffer = plan.render(partialTicks, 0)
    }

    private fun renderTransition(partialTicks: Float) {
        val framebuffer = framebuffer.also { framebuffer = null } ?: return

        shader.addSamplerTexture("sampler", framebuffer)
        shader.getShaderUniformOrDefault("screenSize")
                .set(framebuffer.framebufferWidth.toFloat(), framebuffer.framebufferHeight.toFloat())
        shader.getShaderUniformOrDefault("progress").set(getProgress(partialTicks))
        shader.useShader()

        val tessellator = Tessellator.getInstance()
        tessellator.buffer.apply {
            begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION)
            pos( 1.0,  1.0,  1.0).endVertex()
            pos(-1.0,  1.0,  1.0).endVertex()
            pos(-1.0, -1.0,  1.0).endVertex()
            pos( 1.0, -1.0,  1.0).endVertex()
        }
        tessellator.draw()

        shader.endShader()

        ViewRenderManager.INSTANCE.releaseFramebuffer(framebuffer)
    }

    private inner class EventHandler {
        var registered by MinecraftForge.EVENT_BUS

        @SubscribeEvent
        fun preClientTick(event: TickEvent.ClientTickEvent) {
            if (event.phase != TickEvent.Phase.START) return
            ticksPassed++
        }

        @SubscribeEvent
        fun preRenderWorld(event: PreRenderView) {
            if (event.plan == ViewRenderPlan.MAIN) {
                renderOldView(event.plan, event.partialTicks)
            }
        }

        @SubscribeEvent(priority = EventPriority.LOW)
        fun onRenderWorldLast(event: RenderWorldLastEvent) {
            if (ViewRenderPlan.CURRENT == ViewRenderPlan.MAIN) {
                renderTransition(event.partialTicks)
            }

            if (getProgress(event.partialTicks) >= 1) {
                framebuffer?.let { ViewRenderManager.INSTANCE.releaseFramebuffer(it) } // just in case
                registered = false
                shader.deleteShader()
                whenDone()
            }
        }
    }
}