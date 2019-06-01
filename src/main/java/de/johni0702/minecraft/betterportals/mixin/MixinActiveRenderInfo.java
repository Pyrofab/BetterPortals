package de.johni0702.minecraft.betterportals.mixin;

import de.johni0702.minecraft.betterportals.client.renderer.ViewRenderPlan;
import net.minecraft.client.render.Camera;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class MixinActiveRenderInfo {
    // See also MixinEntityRenderer#disableFogInView
    @Inject(method = "getSubmergedFluidState", at = @At("HEAD"), cancellable = true)
    private static void disableFogInView(CallbackInfoReturnable<FluidState> ci) {
        // If we aren't currently rendering the outermost view,
        // then the camera shouldn't ever be considered to be in any fluid
        if (ViewRenderPlan.Companion.getCURRENT() != ViewRenderPlan.Companion.getMAIN()) {
            ci.setReturnValue(Fluids.EMPTY.getDefaultState());
        }
    }
}
