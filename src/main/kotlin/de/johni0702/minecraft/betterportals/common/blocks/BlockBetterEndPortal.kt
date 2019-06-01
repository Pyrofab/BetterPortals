package de.johni0702.minecraft.betterportals.common.blocks

import de.johni0702.minecraft.betterportals.common.Utils.EMPTY_AABB
import de.johni0702.minecraft.betterportals.common.entity.EndEntryPortalEntity
import de.johni0702.minecraft.betterportals.common.entity.EndExitPortalEntity
import de.johni0702.minecraft.betterportals.common.entity.EndPortalEntity
import de.johni0702.minecraft.betterportals.common.server
import net.minecraft.block.*
import net.minecraft.block.material.Material
import net.minecraft.block.pattern.BlockPattern
import net.minecraft.block.state.BlockWorldState
import net.minecraft.block.state.BlockState
import net.minecraft.block.state.pattern.BlockMatcher
import net.minecraft.block.state.pattern.BlockPattern
import net.minecraft.block.state.pattern.FactoryBlockPattern
import net.minecraft.entity.Entity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.EnumBlockRenderType
import net.minecraft.util.Rotation
import net.minecraft.util.math.BoundingBox
import net.minecraft.util.math.BlockPos
import net.minecraft.world.IBlockAccess
import net.minecraft.world.World
import net.minecraft.server.world.ServerWorld

class BlockBetterEndPortal : EndPortalBlock(Material.PORTAL) {
    companion object {
        val BETTER_END_SPAWN = BlockPos(1, 0, 1)
        val exitPattern: BlockPattern = FactoryBlockPattern.start()
                .aisle("       ", "       ", "       ", "   #   ", "       ", "       ", "       ")
                .aisle("       ", "       ", "       ", "   #   ", "       ", "       ", "       ")
                .aisle("       ", "       ", "       ", "   #   ", "       ", "       ", "       ")
                .aisle("  ###  ", " #---# ", "#-----#", "#--#--#", "#-----#", " #---# ", "  ###  ")
                .aisle("       ", "  ###  ", " ##### ", " ##### ", " ##### ", "  ###  ", "       ")
                .where('#', BlockWorldState.hasState(BlockMatcher.forBlock(Blocks.BEDROCK)))
                .where('-', { true })
                .build()
    }

    init {
        unlocalizedName = "end_portal"
        setRegistryName("minecraft", "end_portal")
        setBlockUnbreakable()
        setLightLevel(1f)
        setResistance(6000000f)
    }

    override fun getRenderType(state: BlockState): EnumBlockRenderType = EnumBlockRenderType.INVISIBLE
    override fun getBoundingBox(state: BlockState, source: IBlockAccess, pos: BlockPos): BoundingBox = EMPTY_AABB
    override fun onEntityCollidedWithBlock(worldIn: World, pos: BlockPos, state: BlockState, entityIn: Entity) {
        if (entityIn is PlayerEntity) {
            onBlockAdded(worldIn, pos, state) // Convert vanilla portals upon touching
        }
    }

    override fun neighborChanged(state: BlockState, worldIn: World, pos: BlockPos, blockIn: Block, fromPos: BlockPos) {
        if (BlockEndPortalFrame.getOrCreatePortalShape().match(worldIn, pos) == null
                && exitPattern.match(worldIn, pos) == null) {
            worldIn.getEntitiesWithinAABB(EndPortalEntity::class.java, BoundingBox(pos)).forEach {
                it.setDead()
            }
            worldIn.setBlockToAir(pos)
        }
    }

    override fun onBlockAdded(localWorld: World, pos: BlockPos, state: BlockState) {
        if (localWorld !is ServerWorld) return
        val server = localWorld.server

        BlockEndPortalFrame.getOrCreatePortalShape().match(localWorld, pos)?.let { pattern ->
            val localDim = localWorld.provider.dimension
            val localPos = pattern.frontTopLeft.add(-1, 0, -1)
            val localRot = Rotation.NONE

            if (localWorld.getEntitiesWithinAABB(EndPortalEntity::class.java, BoundingBox(localPos)).isNotEmpty()) return

            val remoteDim = 1
            val remoteWorld = server.getWorld(remoteDim)
            val remotePos = remoteWorld.getTopSolidOrLiquidBlock(BETTER_END_SPAWN).add(0, 40, 0).let {
                if (it.y > remoteWorld.height) BlockPos(it.x, remoteWorld.height - 1, it.z) else it
            }
            val remoteRot = Rotation.NONE

            val localPortal = EndEntryPortalEntity(localWorld, localDim, localPos, localRot, remoteDim, remotePos, remoteRot)
            val remotePortal = EndEntryPortalEntity(remoteWorld, remoteDim, remotePos, remoteRot, localDim, localPos, localRot)

            localWorld.spawnEntity(localPortal)
            remoteWorld.spawnEntity(remotePortal)
            return
        }
        exitPattern.match(localWorld, pos)?.let { pattern ->
            val localDim = localWorld.provider.dimension
            val localPos = pattern.frontTopLeft.add(-1, -3, -1)
            val localRot = Rotation.NONE

            if (localWorld.getEntitiesWithinAABB(EndPortalEntity::class.java, BoundingBox(localPos)).isNotEmpty()) return

            val remoteDim = 0
            val remoteWorld = server.getWorld(remoteDim)
            val remotePos = remoteWorld.getTopSolidOrLiquidBlock(remoteWorld.spawnPoint).add(0, 40, 0).let {
                if (it.y > remoteWorld.height) BlockPos(it.x, remoteWorld.height - 1, it.z) else it
            }
            val remoteRot = Rotation.NONE

            val localPortal = EndExitPortalEntity(localWorld, localDim, localPos, localRot, remoteDim, remotePos, remoteRot)
            val remotePortal = EndExitPortalEntity(remoteWorld, remoteDim, remotePos, remoteRot, localDim, localPos, localRot)

            localWorld.spawnEntity(localPortal)
            remoteWorld.spawnEntity(remotePortal)
            return
        }
    }
}