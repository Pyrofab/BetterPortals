package de.johni0702.minecraft.betterportals.client

import com.mojang.blaze3d.platform.GLX
import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.platform.TextureUtil
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gl.GlFramebuffer
import org.lwjgl.opengl.GL11

/**
 * Regular [GlFramebuffer] but with a depth texture.
 */
class FramebufferD(width: Int, height: Int): GlFramebuffer(width, height, false, MinecraftClient.IS_SYSTEM_MAC) {
    // Workaround createFramebuffer being called during the super constructor before any initializer would run
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    lateinit var depthTex: Integer
    var depthTexture: Int
        get() = depthTex.toInt()
        private set(value) {
            depthTex = Integer(value)
        }

    override fun initFbo(width: Int, height: Int, flushErrors: Boolean) {
        super.initFbo(width, height, flushErrors)

        depthTexture = TextureUtil.generateTextureId()
        GlStateManager.bindTexture(depthTexture)
        GlStateManager.texImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_DEPTH_COMPONENT, width, height, 0, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, null)
        GLX.glBindFramebuffer(GLX.GL_FRAMEBUFFER, this.fbo)
        GLX.glFramebufferTexture2D(GLX.GL_FRAMEBUFFER, GLX.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, depthTexture, 0)
    }

    override fun delete() {
        endRead()
        endWrite()

        if (depthTexture > -1) {
            TextureUtil.releaseTextureId(depthTexture)
            depthTexture = -1
        }

        super.delete()
    }
}