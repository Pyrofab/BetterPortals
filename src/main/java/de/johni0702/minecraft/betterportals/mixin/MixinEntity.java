package de.johni0702.minecraft.betterportals.mixin;

import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Entity.class)
public abstract class MixinEntity {
    // block push logic seems to not involve World#getCollisionBoxes in 1.14

/*
    @Inject(
            method = "pushOutOfBlocks",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/World;collidesWithAnyBlock(Lnet/minecraft/util/math/BoundingBox;)Z"
            )
    )
    private void beforeCollidesWithAnyBlock(double x, double y, double z, CallbackInfoReturnable<Boolean> ci) {
        AbstractPortalEntity.EventHandler.INSTANCE.setCollisionBoxesEntity((Entity) (Object) this);
    }

    @Inject(
            method = "pushOutOfBlocks",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/World;collidesWithAnyBlock(Lnet/minecraft/util/math/BoundingBox;)Z",
                    shift = At.Shift.AFTER
            )
    )
    private void afterCollidesWithAnyBlock(double x, double y, double z, CallbackInfoReturnable<Boolean> ci) {
        AbstractPortalEntity.EventHandler.INSTANCE.setCollisionBoxesEntity(null);
    }
*/

// lava check is done in FluidBlock now

/*
    @Inject(method = "isInLava", at = @At("HEAD"), cancellable = true)
    private void isInLava(CallbackInfoReturnable<Boolean> ci) {
        Boolean result = AbstractPortalEntity.EventHandler.INSTANCE.isInMaterial((Entity) (Object) this, Material.LAVA);
        if (result != null) {
            ci.setReturnValue(result);
        }
    }
*/
}
