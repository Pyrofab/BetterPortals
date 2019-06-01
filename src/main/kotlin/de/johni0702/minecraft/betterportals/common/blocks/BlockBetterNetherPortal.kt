package de.johni0702.minecraft.betterportals.common.blocks

import de.johni0702.minecraft.betterportals.common.*
import de.johni0702.minecraft.betterportals.common.Utils.EMPTY_AABB
import de.johni0702.minecraft.betterportals.common.entity.NetherPortalEntity
import net.minecraft.block.Block
import net.minecraft.block.BlockPortal
import net.minecraft.block.SoundType
import net.minecraft.block.state.BlockState
import net.minecraft.entity.Entity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.block.Blocks
import net.minecraft.util.EnumBlockRenderType
import net.minecraft.util.math.Direction
import net.minecraft.util.Rotation
import net.minecraft.util.math.BoundingBox
import net.minecraft.util.math.BlockPos
import net.minecraft.world.IBlockAccess
import net.minecraft.world.World
import net.minecraft.server.world.ServerWorld

class BlockBetterNetherPortal : BlockPortal(), PortalBlock<NetherPortalEntity> {
    init {
        unlocalizedName = "portal"
        setRegistryName("minecraft", "portal")
        setBlockUnbreakable()
        setLightLevel(1f)
        soundType = SoundType.GLASS
    }

    override val portalBlock: Block get() = this
    override val frameBlock: Block get() = Blocks.OBSIDIAN
    override val frameStepsBlock: Block get() = Blocks.OBSIDIAN
    override val maxPortalSize: Int = 100
    override val entityType: Class<NetherPortalEntity> = NetherPortalEntity::class.java

    override fun createPortalEntity(localEnd: Boolean, world: World, plane: Direction.Type, portalBlocks: Set<BlockPos>,
                                    localDim: Int, localPos: BlockPos, localRot: Rotation): NetherPortalEntity =
            NetherPortalEntity(world, plane, portalBlocks, localDim, localPos, localRot)

    override fun getRemoteWorldFor(localWorld: ServerWorld, pos: BlockPos): ServerWorld? {
        val server = localWorld.server
        if (!server.allowNether) return null
        val remoteDim = if (localWorld.provider.dimensionType.id == -1) 0 else -1
        return server.getWorld(remoteDim)
    }

    override fun getRenderType(state: BlockState): EnumBlockRenderType = EnumBlockRenderType.INVISIBLE
    override fun getBoundingBox(state: BlockState, source: IBlockAccess, pos: BlockPos): BoundingBox = EMPTY_AABB
    override fun onEntityCollidedWithBlock(worldIn: World, pos: BlockPos, state: BlockState, entityIn: Entity) {
        if (entityIn is PlayerEntity) {
            validatePortalOrDestroy(worldIn, pos) // Convert vanilla portals upon touching
        }
    }

    override fun neighborChanged(state: BlockState, worldIn: World, pos: BlockPos, blockIn: Block, fromPos: BlockPos) {
        validatePortalOrDestroy(worldIn, pos)
    }

    override fun trySpawnPortal(localWorld: World, pos: BlockPos): Boolean = tryToLinkPortals(localWorld, pos)
}