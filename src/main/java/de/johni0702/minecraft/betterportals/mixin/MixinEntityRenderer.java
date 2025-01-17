package de.johni0702.minecraft.betterportals.mixin;

import de.johni0702.minecraft.betterportals.client.PostSetupFogEvent;
import de.johni0702.minecraft.betterportals.client.renderer.ViewRenderManager;
import de.johni0702.minecraft.betterportals.client.renderer.ViewRenderPlan;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.MinecraftForge;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class MixinEntityRenderer {
    @Shadow @Final private Minecraft mc;

    @Redirect(method = "updateCameraAndRender",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;renderWorld(FJ)V"))
    private void renderWorld(EntityRenderer entityRenderer, float partialTicks, long finishTimeNano) {
        ViewRenderManager.Companion.getINSTANCE().renderWorld(partialTicks, finishTimeNano);
    }

    // See also MixinActiveRenderInfo#disableFogInView
    @Redirect(
            method = "updateFogColor",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/WorldClient;getBlockState(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/state/IBlockState;"
            )
    )
    private IBlockState disableFogInView(WorldClient world, BlockPos blockPos) {
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
