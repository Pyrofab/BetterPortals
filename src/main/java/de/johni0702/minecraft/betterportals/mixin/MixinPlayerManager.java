package de.johni0702.minecraft.betterportals.mixin;

import de.johni0702.minecraft.betterportals.server.DimensionTransitionHandler;
import de.johni0702.minecraft.betterportals.server.view.ViewEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.dimension.DimensionType;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.ITeleporter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The player list has some methods used to send packet to all online players.
 * Instead of iterating over players via {@link net.minecraft.world.World#playerEntities}, it has its own list.
 * We can however not add view entities to that list because it is also used to reply to status messages, tab-complete
 * and probably a few other things we don't want view entities to show up in.
 * So, instead we inject into the relevant packet sending methods here and send them to our view entities as well.
 */
@Mixin(PlayerManager.class)
public abstract class MixinPlayerManager {
    @Shadow @Final private MinecraftServer server;

    @Inject(method = "sendToAll(Lnet/minecraft/network/Packet;)V", at = @At("HEAD"))
    private void sendPacketToAllViews(Packet<?> packetIn, CallbackInfo ci) {
        for (DimensionType dimension : Registry.DIMENSION) {
            sendPacketToAllViewsInDimension(packetIn, dimension, ci);
        }
    }

    @Inject(method = "sendToDimension", at = @At("HEAD"))
    private void sendPacketToAllViewsInDimension(Packet<?> packetIn, DimensionType dimension, CallbackInfo ci) {
        for (PlayerEntity entity : server.getWorld(dimension).getPlayers()) {
            if (entity instanceof ViewEntity) {
                ((ViewEntity) entity).networkHandler.sendPacket(packetIn);
            }
        }
    }

    @Inject(method = "sendToAround", at = @At("HEAD"))
    private void sendPacketToAllViewsNearExcept(PlayerEntity except, double x, double y, double z, double radius, DimensionType dimension, Packet<?> packetIn, CallbackInfo ci) {
        for (PlayerEntity entity : server.getWorld(dimension).getPlayers()) {
            if (entity instanceof ViewEntity && entity != except) {
                double dx = x - entity.x;
                double dy = y - entity.y;
                double dz = z - entity.z;
                if (dx * dx + dy * dy + dz * dz < radius * radius) {
                    ((ViewEntity) entity).networkHandler.sendPacket(packetIn);
                }
            }
        }
    }
}
