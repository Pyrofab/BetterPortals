package de.johni0702.minecraft.betterportals.common.entity

import de.johni0702.minecraft.betterportals.common.pos
import net.minecraft.block.Block
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.client.renderer.culling.ICamera
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.block.Blocks
import net.minecraft.nbt.NBTBase
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import net.minecraft.util.math.Direction
import net.minecraft.util.BlockRotation
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

/**
 * A portal which really only exists at one end.
 * At the other end, it'll seem to exist while traveling through it but cannot be used to go back and disappear when
 * moving sufficiently far away.
 */
abstract class OneWayPortalEntity(
        /**
         * Whether this portal instance is the tail/exit end of a pair of portals.
         * Not to be confused with the exit portal which spawns after the dragon fight; its tail end is in the overworld.
         * A pair of one-way portals cannot be entered from the tail end.
         */
        var isTailEnd: Boolean,

        world: World, plane: Direction.Type, relativeBlocks: Set<BlockPos>,
        localDimension: Int, localPosition: BlockPos, localRotation: BlockRotation,
        remoteDimension: Int?, remotePosition: BlockPos, remoteRotation: BlockRotation
) : AbstractPortalEntity(
        world, plane, relativeBlocks,
        localDimension, localPosition, localRotation,
        remoteDimension,remotePosition, remoteRotation
) {
    override fun writePortalToNBT(): CompoundTag =
            super.writePortalToNBT().apply { putBoolean("IsTailEnd", isTailEnd) }

    override fun readPortalFromNBT(nbt: Tag?) {
        super.readPortalFromNBT(nbt)
        (nbt as? CompoundTag)?.apply {
            isTailEnd = getBoolean("IsTailEnd")
        }
    }

    /**
     * When the player has just passed through the portal, the other end will still be rendered while the player
     * hasn't moved away from it.
     * This is to prevent the portal from disappearing off of half of the screen.
     */
    var isTravelingInProgress = false
        set(value) {
            if (field == value) return
            field = value
            val newState = (if (value) portalFrameBlock else Blocks.AIR).defaultState
            val oldState = (if (value) Blocks.AIR else portalFrameBlock).defaultState
            val portalBlocks = localBlocks
            portalBlocks.forEach { pos ->
                Direction.HORIZONTAL.forEach { facing ->
                    val neighbour = pos.offset(facing)
                    if (neighbour !in portalBlocks) {
                        if (world.getBlockState(neighbour) == oldState) {
                            world.setBlockState(neighbour, newState)
                        }
                    }
                }
            }
        }

    /**
     * The type of blocks which form the fake, client-side frame at the tail end of the portal.
     */
    abstract val portalFrameBlock: Block

    override fun checkTeleportees() {
        if (isTailEnd) return // Cannot use portal from the tail end
        super.checkTeleportees()
    }

    override fun onClientUpdate() {
        super.onClientUpdate()

        if (isTravelingInProgress && isTailEnd) {
            // Check whether the player has moved away from the tail end of the portal far enough so we can hide it
            isTravelingInProgress = world.players.filter { it is ClientPlayerEntity }.any {
                // Traveling is still considered in progress if the distance to the portal center is less than 10 blocks
                localBoundingBox.center.squaredDistanceTo(it.pos) < 100.0
            }
        }
    }

    override fun teleportPlayer(player: PlayerEntity, from: Direction): Boolean {
        val remotePortal = getRemotePortal() // FIXME for some reason this call fails after the teleport
        val success = super.teleportPlayer(player, from)
        if (success) {
            (remotePortal as OneWayPortalEntity).isTravelingInProgress = true
        }
        return success
    }

    override fun canBeSeen(camera: ICamera): Boolean = (!isTailEnd || isTravelingInProgress) && super.canBeSeen(camera)
}
