package de.johni0702.minecraft.betterportals.mixin;

import de.johni0702.minecraft.betterportals.server.DimensionTransitionHandler;
import de.johni0702.minecraft.betterportals.server.view.ServerViewManager;
import de.johni0702.minecraft.betterportals.server.view.ServerViewManagerImpl;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionType;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerEntity.class)
public abstract class MixinServerPlayerEntity extends Entity implements ServerViewManager.Provider {
    @Shadow public ServerPlayNetworkHandler networkHandler;
    @Shadow @Final public MinecraftServer server;
    private ServerViewManager viewManager = new ServerViewManagerImpl(this.server, this.networkHandler);

    public MixinServerPlayerEntity(EntityType<?> entityType, World world) {
        super(entityType, world);
    }

    @NotNull
    @Override
    public ServerViewManager getCapability() {
        return this.viewManager;
    }

    @Inject(method = "changeDimension",
            at = @At("HEAD"),
            cancellable = true)
    private void betterPortalPlayerToDimension(DimensionType dimension, CallbackInfoReturnable<Entity> cir) {
        DimensionTransitionHandler.INSTANCE.transferPlayerToDimension((ServerPlayerEntity)(Object)this, dimension);
        cir.setReturnValue(this);
    }
}
