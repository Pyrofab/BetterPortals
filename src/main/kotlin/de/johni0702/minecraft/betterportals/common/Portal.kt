package de.johni0702.minecraft.betterportals.common

import net.fabricmc.fabric.api.util.NbtType
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.util.BlockRotation
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.BoundingBox
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import org.joml.Matrix4d

interface Portal {
    val plane: Direction.Type
    val relativeBlocks: Set<BlockPos>
    val localDimension: Int
    val localPosition: BlockPos
    val localRotation: BlockRotation
    val remoteDimension: Int?
    val remotePosition: BlockPos
    val remoteRotation: BlockRotation

    val localFacing: Direction
        get() = when(plane) {
            Direction.Type.VERTICAL -> localRotation.facing
            Direction.Type.HORIZONTAL -> Direction.UP
        }
    val remoteFacing: Direction
        get() = when(plane) {
            Direction.Type.VERTICAL -> remoteRotation.facing
            Direction.Type.HORIZONTAL -> Direction.UP
        }

    val localAxis: Direction.Axis get() = localFacing.axis
    val remoteAxis: Direction.Axis get() = remoteFacing.axis

    fun BlockPos.toRemote(): BlockPos = rotate(remoteRotation).add(remotePosition)
    fun BlockPos.toLocal(): BlockPos = rotate(localRotation).add(localPosition)

    fun Vec3d.toSpace(pos: BlockPos, rotation: BlockRotation): Vec3d =
            subtract(0.5, 0.0, 0.5).rotate(rotation).add(0.5, 0.0, 0.5).add(pos.to3d())
    fun Vec3d.fromSpace(pos: BlockPos, rotation: BlockRotation): Vec3d =
            subtract(pos.to3d()).subtract(0.5, 0.0, 0.5).rotate(rotation.reverse).add(0.5, 0.0, 0.5)
    fun Vec3d.toRemote(): Vec3d = toSpace(remotePosition, remoteRotation)
    fun Vec3d.toLocal(): Vec3d = toSpace(localPosition, localRotation)
    fun Vec3d.fromRemote(): Vec3d = fromSpace(remotePosition, remoteRotation)
    fun Vec3d.fromLocal(): Vec3d = fromSpace(localPosition, localRotation)

    val localToRemoteMatrix: Matrix4d
        get() =
        Mat4d.add((remotePosition.to3d() + Vec3d(0.5, 0.0, 0.5)).toJavaX()) *
                Mat4d.rotYaw((remoteRotation - localRotation).degrees) *
                Mat4d.sub((localPosition.to3d() + Vec3d(0.5, 0.0, 0.5)).toJavaX())

    val localBlocks get() = relativeBlocks.map { it.toLocal() }.toSet()
    val remoteBlocks get() = relativeBlocks.map { it.toRemote() }.toSet()

    val localBoundingBox: BoundingBox get() = localBlocks.toBoundingBox()
    val remoteBoundingBox: BoundingBox get() = remoteBlocks.toBoundingBox()

    fun writePortalToNBT(): CompoundTag = CompoundTag().apply {
        putInt("Plane", plane.ordinal)
        put("Blocks", ListTag().apply {
            relativeBlocks.forEach { add(CompoundTag().setXYZ(it)) }
        })
        put("Local", CompoundTag().apply {
            setXYZ(localPosition)
            putInt("Rotation", localRotation.ordinal)
            putInt("Dim", localDimension)
        })
        val remoteDimension = this@Portal.remoteDimension
        if (remoteDimension != null) {
            put("Remote", CompoundTag().apply {
                setXYZ(remotePosition)
                putInt("Rotation", remoteRotation.ordinal)
                putInt("Dim", remoteDimension)
            })
        }
    }

    interface Linkable : Portal {
        fun link(remoteDimension: Int, remotePosition: BlockPos, remoteRotation: BlockRotation)
    }

    interface Mutable : Portal.Linkable {
        override var plane: Direction.Type
        override var relativeBlocks: Set<BlockPos>
        override var localDimension: Int
        override var localPosition: BlockPos
        override var localRotation: BlockRotation
        override var remoteDimension: Int?
        override var remotePosition: BlockPos
        override var remoteRotation: BlockRotation

        override fun link(remoteDimension: Int, remotePosition: BlockPos, remoteRotation: BlockRotation) {
            this.remoteDimension = remoteDimension
            this.remotePosition = remotePosition
            this.remoteRotation = remoteRotation
        }

        fun readPortalFromNBT(nbt: Tag?) {
            (nbt as? CompoundTag)?.apply {
                plane = Direction.Type.values()[getInt("Plane")]
                relativeBlocks = getList("Blocks", NbtType.COMPOUND).map {
                    (it as CompoundTag).getXYZ()
                }.toSet()
                getCompound("Local").apply {
                    localPosition = getXYZ()
                    localRotation = BlockRotation.values()[getInt("Rotation")]
                    localDimension = getInt("Dim")
                }
                if (containsKey("Remote")) {
                    getCompound("Remote").apply {
                        remotePosition = getXYZ()
                        remoteRotation = BlockRotation.values()[getInt("Rotation")]
                        remoteDimension = getInt("Dim")
                    }
                } else {
                    remotePosition = BlockPos.ORIGIN
                    remoteRotation = BlockRotation.NONE
                    remoteDimension = null
                }
            }
        }
    }
}