package de.johni0702.minecraft.betterportals.mixin;

import de.johni0702.minecraft.betterportals.common.entity.AbstractPortalEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class MixinPlayerEntity extends LivingEntity {
    public MixinPlayerEntity(EntityType<LivingEntity> t) {
        super(t, null);
    }

    @Inject(
            method = "doesNotSuffocate",
            at = @At(value = "HEAD"),
            cancellable = true
    )
    private void isOpenBlockSpaceCheckPortals(BlockPos pos, CallbackInfoReturnable<Boolean> ci) {
        ci.setReturnValue(AbstractPortalEntity.EventHandler.INSTANCE.onIsOpenBlockSpace(this, pos));
    }
}
