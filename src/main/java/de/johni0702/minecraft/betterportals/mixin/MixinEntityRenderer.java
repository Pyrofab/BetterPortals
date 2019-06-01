package de.johni0702.minecraft.betterportals.mixin;

import de.johni0702.minecraft.betterportals.client.PostSetupFogEvent;
import de.johni0702.minecraft.betterportals.client.renderer.AbstractRenderPortal;
import de.johni0702.minecraft.betterportals.client.renderer.ViewRenderManager;
import de.johni0702.minecraft.betterportals.client.renderer.ViewRenderPlan;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.FrustumWithOrigin;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.MinecraftForge;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class MixinEntityRenderer {

    @Redirect(method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/GameRenderer;renderWorld(FJ)V"))
    private void renderWorld(GameRenderer entityRenderer, float partialTicks, long finishTimeNano) {
        ViewRenderManager.Companion.getINSTANCE().renderWorld(partialTicks, finishTimeNano);
    }

    @Redirect(method = "renderCenter",
            at = @At(value = "NEW", target = "net/minecraft/client/render/FrustumWithOrigin"))
    private FrustumWithOrigin createCamera(Frustum frustum) {
        FrustumWithOrigin camera = AbstractRenderPortal.Companion.createCamera();
        if (camera == null) {
            camera = new FrustumWithOrigin(frustum);
        }
        return camera;
    }

    // See also MixinActiveRenderInfo#disableFogInView
    @Redirect(
            method = "updateFogColor",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/WorldClient;getBlockState(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/state/BlockState;"
            )
    )
    private BlockState disableFogInView(WorldClient world, BlockPos blockPos) {
        // If we aren't currently rendering the outermost view,
        // then the camera shouldn't ever be considered to be in any blocks
        if (ViewRenderPlan.Companion.getCURRENT() != ViewRenderPlan.Companion.getMAIN()) {
            return Blocks.AIR.getDefaultState();
        }
        return world.getBlockState(blockPos);
    }

    @Inject(method = "setupFog", at = @At("RETURN"))
    private void postSetupFogInView(int start, float partialTicks, CallbackInfo ci) {
        MinecraftForge.EVENT_BUS.post(new PostSetupFogEvent());
    }
}
