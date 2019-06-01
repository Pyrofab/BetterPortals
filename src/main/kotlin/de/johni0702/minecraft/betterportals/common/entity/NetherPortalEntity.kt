package de.johni0702.minecraft.betterportals.common.entity

import net.minecraft.util.math.Direction
import net.minecraft.util.Rotation
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

class NetherPortalEntity(
        world: World,
        plane: Direction.Type,
        portalBlocks: Set<BlockPos>,
        localDimension: Int, localPosition: BlockPos, localRotation: Rotation
) : AbstractPortalEntity(
        world, plane, portalBlocks,
        localDimension, localPosition, localRotation,
        null, BlockPos.ORIGIN, Rotation.NONE
) {
    @Suppress("unused")
    constructor(world: World) : this(world, Direction.Type.VERTICAL, emptySet(), 0, BlockPos.ORIGIN, Rotation.NONE)
}