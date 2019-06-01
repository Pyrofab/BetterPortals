package de.johni0702.minecraft.betterportals.common

import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import de.johni0702.minecraft.betterportals.BetterPortalsMod
import de.johni0702.minecraft.betterportals.LOGGER
import net.fabricmc.fabric.api.network.PacketContext
import net.minecraft.block.BlockState
import net.minecraft.block.state.BlockState
import net.minecraft.client.network.OtherClientPlayerEntity
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityTracker
import net.minecraft.entity.player.ServerPlayerEntity
import net.minecraft.block.Blocks
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.NetHandlerPlayServer
import net.minecraft.network.PacketBuffer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.math.Direction
import net.minecraft.util.math.*
import net.minecraft.world.World
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.*
import net.minecraft.world.chunk.*
import net.minecraftforge.fml.common.eventhandler.EventBus
import net.minecraftforge.fml.common.network.simpleimpl.PacketContext
import org.joml.*
import java.lang.Math
import kotlin.math.max
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

// Generic
fun <T> MutableList<T>.removeAtOrNull(index: Int) = if (isEmpty()) null else removeAt(index)
fun <T> MutableList<T>.popOrNull() = removeAtOrNull(0)
fun <T> MutableList<T>.takeLast() = removeAt(lastIndex)
fun Vector3d.to4d() = Vector4d(x, y, z, 0.0)
operator fun Matrix4d.times(other: Matrix4d): Matrix4d = Mat4d.id().also { it.mul(this, other) }
operator fun Matrix4d.times(other: Vector3d): Vector3d = Vector4d().also { transform(other.to4d(), it) }.run { Vector3d(x, y, z) }
operator fun Matrix4d.times(other: Point3d): Point3d = Vector4d().also { transform(other.toVector4d(), it) }.run { Point3d(x, y, z) }
//inline operator fun <reified T : Tuple4d> Matrix4d.times(other: T) = (other.clone() as T).also { transform(other, it) }

// MC
val Direction.Type.axes get() = Direction.Axis.values().filter { it.type == this }
val Direction.Type.perpendicularAxes get() = opposite.axes
val Direction.Type.opposite get() = when(this) {
    Direction.Type.HORIZONTAL -> Direction.Type.VERTICAL
    Direction.Type.VERTICAL -> Direction.Type.HORIZONTAL
}
fun BlockRotation.axis(plane: Direction.Type): Direction.Axis = when(plane) {
    Direction.Type.HORIZONTAL -> this.facing.axis
    Direction.Type.VERTICAL -> Direction.Axis.Y
}
val Direction.Axis.perpendicularPlane get() = type.opposite
fun Direction.Axis.toFacing(direction: Direction.AxisDirection): Direction = Direction.get(direction, this)
fun Direction.Axis.toFacing(direction: Double)
        = toFacing(if (direction > 0) Direction.AxisDirection.POSITIVE else Direction.AxisDirection.NEGATIVE)
fun Direction.Axis.toFacing(direction: Vec3d) = toFacing(direction[this])
val Direction.Axis.parallelFaces get() = Direction.values().filter { it.axis != this }
fun Vec3i.to3d(): Vec3d = Vec3d(this.x.toDouble(), this.y.toDouble(), this.z.toDouble())
fun Vec3i.to3dMid(): Vec3d = this.to3d() + Vec3d(0.5, 0.5, 0.5)
operator fun Vec3i.plus(other: Vec3i): Vec3i = Vec3i(x + other.x, y + other.y, z + other.z)
operator fun Vec3i.times(n: Int): Vec3i = Vec3i(x * n, y * n, z * n)
operator fun Vec3d.plus(other: Vec3d): Vec3d = add(other)
operator fun Vec3d.minus(other: Vec3d): Vec3d = subtract(other)
operator fun Vec3d.times(n: Int): Vec3d = Vec3d(x * n, y * n, z * n)
operator fun Vec3d.times(d: Double): Vec3d = Vec3d(x * d, y * d, z * d)
fun Vec3d.withoutY(): Vec3d = Vec3d(x, 0.0, y)
fun Vec3d.abs(): Vec3d = Vec3d(Math.abs(x), Math.abs(y), Math.abs(z))
fun Vec3d.toJavaX() = Vector3d(x, y, z)
fun Vec3d.toJavaXPos() = Vector4d(x, y, z, 1.0)
fun Vec3d.toJavaXVec() = Vector4d(x, y, z, 0.0)
fun Vec3d.toPoint() = Point3d(x, y, z)
fun Vec3d.toBlockPos() = BlockPos(this)
fun Vector3d.toMC() = Vec3d(x, y, z)
fun Vector4d.toMC() = Vec3d(x, y, z)
fun Point3d.toMC() = Vec3d(x, y, z)
operator fun Vec3d.get(axis: Direction.Axis) = when(axis) {
    Direction.Axis.X -> x
    Direction.Axis.Y -> y
    Direction.Axis.Z -> z
}
fun Vec3d.with(axis: Direction.Axis, value: Double) = when(axis) {
    Direction.Axis.X -> Vec3d(value, y, z)
    Direction.Axis.Y -> Vec3d(x, value, z)
    Direction.Axis.Z -> Vec3d(x, y, value)
}
fun Vec3d.rotate(rot: BlockRotation): Vec3d = when(rot) {
    BlockRotation.NONE -> this
    BlockRotation.CLOCKWISE_90 -> Vec3d(-z, y, x)
    BlockRotation.CLOCKWISE_180 -> Vec3d(-x, y, -z)
    BlockRotation.COUNTERCLOCKWISE_90 -> Vec3d(z, y, -x)
}
fun Direction.toRotation(): BlockRotation = if (horizontal == -1) BlockRotation.NONE else BlockRotation.values()[horizontal]
val BlockRotation.facing: Direction get() = Direction.fromHorizontal(ordinal)
val BlockRotation.degrees get() = ordinal * 90
val BlockRotation.reverse get() = when(this) {
    BlockRotation.CLOCKWISE_90 -> BlockRotation.COUNTERCLOCKWISE_90
    BlockRotation.COUNTERCLOCKWISE_90 -> BlockRotation.CLOCKWISE_90
    else -> this
}
operator fun BlockRotation.plus(other: BlockRotation): BlockRotation = rotate(other)
operator fun BlockRotation.minus(other: BlockRotation): BlockRotation = rotate(other.reverse)
val BoundingBox_INFINITE = BoundingBox(
        Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY,
        Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY
)
fun BoundingBox.with(facing: Direction, value: Double) = when(facing) {
    Direction.DOWN -> BoundingBox(minX, value, minZ, maxX, maxY, maxZ)
    Direction.UP -> BoundingBox(minX, minY, minZ, maxX, value, maxZ)
    Direction.NORTH -> BoundingBox(minX, minY, value, maxX, maxY, maxZ)
    Direction.SOUTH -> BoundingBox(minX, minY, minZ, maxX, maxY, value)
    Direction.WEST -> BoundingBox(value, minY, minZ, maxX, maxY, maxZ)
    Direction.EAST -> BoundingBox(minX, minY, minZ, value, maxY, maxZ)
}
//fun BoundingBox.grow(by: Vec3d): BoundingBox = grow(by.x, by.y, by.z)
//fun BoundingBox.expand(by: Vec3d): BoundingBox = expand(by.x, by.y, by.z)
//fun BoundingBox.contract(by: Vec3d): BoundingBox = contract(by.x, by.y, by.z)
val BoundingBox.sizeX get() = maxX - minX
val BoundingBox.sizeY get() = maxY - minY
val BoundingBox.sizeZ get() = maxZ - minZ
val BoundingBox.maxSideLength get() = max(sizeX, max(sizeY, sizeZ))
val BoundingBox.min get() = Vec3d(minX, minY, minZ)
val BoundingBox.max get() = Vec3d(maxX, maxY, maxZ)
fun Vec3d.toBoundingBox(other: Vec3d) = BoundingBox(this, other)
fun Collection<BlockPos>.toBoundingBox(): BoundingBox =
        if (isEmpty()) BoundingBox(BlockPos.ORIGIN, BlockPos.ORIGIN)
        else map(::BoundingBox).reduce(BoundingBox::union)
fun Collection<BlockPos>.minByAnyCoord() =
        minWith(Comparator.comparingInt<BlockPos> { it.x }.thenComparingInt { it.y }.thenComparingInt { it.z })

fun World?.sync(task: () -> Unit) = BetterPortalsMod.PROXY.sync(this, task)
fun PacketContext.sync(task: () -> Unit) = this.taskQueue.execute(task)

fun <L : ListenableFuture<T>, T> L.logFailure(): L {
    Futures.addCallback(this, object : FutureCallback<T> {
        override fun onSuccess(result: T?) = Unit
        override fun onFailure(t: Throwable) {
            LOGGER.error("Failed future:", t)
        }
    })
    return this
}

fun CompoundTag.setXYZ(pos: BlockPos): CompoundTag {
    putInt("x", pos.x)
    putInt("y", pos.y)
    putInt("z", pos.z)
    return this
}
fun CompoundTag.getXYZ(): BlockPos = BlockPos(getInt("x"), getInt("y"), getInt("z"))

inline fun <reified E : Enum<E>> PacketByteBuf.readEnum(): E = readEnumConstant(E::class.java)
inline fun <reified E : Enum<E>> PacketByteBuf.writeEnum(value: E): PacketByteBuf = writeEnumConstant(value)

val Entity.eyeOffset get() = Vec3d(0.0, getEyeHeight(pose).toDouble(), 0.0)
val Entity.syncPos get() = when {
//    this is OtherClientPlayerEntity && otherPlayerMPPosRotationIncrements > 0 -> otherPlayerMPPos
    else -> pos
}
var Entity.pos
    get() = Vec3d(x, y, z)
    set(value) = with(value) { x = x; y = y; z = z }
var Entity.lastTickPos
    get() = Vec3d(prevRenderX, prevRenderY, prevRenderZ)
    set(value) = with(value) { prevRenderX = x; prevRenderY = y; prevRenderZ = z }
var Entity.prevPos
    get() = Vec3d(prevX, prevY, prevZ)
    set(value) = with(value) { prevX = x; prevY = y; prevZ = z }
var OtherClientPlayerEntity.otherPlayerMPPos
    get() = Vec3d(otherPlayerMPX, otherPlayerMPY, otherPlayerMPZ)
    set(value) = with(value) { otherPlayerMPX = x; otherPlayerMPY = y; otherPlayerMPZ = z }

fun ChunkPos.add(x: Int, z: Int) = ChunkPos(this.x + x, this.z + z)

//val ServerWorld.server get() = minecraftServer!!

fun World.forceSpawnEntity(entity: Entity) {
    val wasForceSpawn = entity.teleporting
    entity.teleporting = true
    spawnEntity(entity)
    entity.teleporting = wasForceSpawn
}

fun World.makeBlockCache(forceLoad: Boolean = true): BlockCache =
    Gettable { pos ->
        HashMap<BlockPos, BlockState>().getOrPut(pos) {
            if (forceLoad || isBlockLoaded(pos)) getBlockState(pos) else Blocks.AIR.defaultState
        }
    }

fun World.makeChunkwiseBlockCache(forceLoad: Boolean = true): BlockCache =
        HashMap<ChunkPos, List<PalettedContainer<BlockState>?>>().let { cache ->
            Gettable { pos ->
                val chunkPos = ChunkPos(pos)
                val storageLists = cache.getOrPut(chunkPos) {
                    getChunk(chunkPos.x, chunkPos.z, ChunkStatus.FULL, forceLoad)?.sectionArray?.map { it?.container?.copy() } ?: emptyList()
                }
                storageLists.getOrNull(pos.y shr 4)?.get(pos.x and 15, pos.y and 15, pos.z and 15)
                        ?: Blocks.AIR.defaultState
            }
        }

fun <T> PalettedContainer<T>.copy(): PalettedContainer<T> {
    val copy = PalettedContainer<T>(this.fallbac)
    copy.bits = bits
    copy.palette = when {
        bits <= 4 -> BlockStatePaletteLinear(bits, this).apply {
            System.arraycopy((palette as BlockStatePaletteLinear).states, 0, states, 0, states.size)
        }
        bits <= 8 -> BlockStatePaletteHashMap(bits, this).apply {
            val org = (palette as BlockStatePaletteHashMap).statePaletteMap
            org.forEach { statePaletteMap.put(it, org.getId(it)) }
        }
        else -> BlockStateContainer.REGISTRY_BASED_PALETTE
    }
    copy.storage = BitArray(bits, 4096)
    System.arraycopy(storage.backingLongArray, 0, copy.storage.backingLongArray, 0, storage.backingLongArray.size)
    return copy
}

val <T> LazyLoadBase<T>.maybeValue get() = if (isLoaded) value else null

operator fun <T> EventBus.provideDelegate(thisRef: T, prop: KProperty<*>): ReadWriteProperty<T, Boolean>
        = EventBusRegistration(this)

private class EventBusRegistration<in T>(
        val eventBus: EventBus
) : ReadWriteProperty<T, Boolean> {
    var registered = false

    override fun getValue(thisRef: T, property: KProperty<*>): Boolean = registered

    override fun setValue(thisRef: T, property: KProperty<*>, value: Boolean) {
        if (value) eventBus.register(thisRef)
        else eventBus.unregister(thisRef)
        this.registered = value
    }

}
