package de.johni0702.minecraft.betterportals.common

import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import de.johni0702.minecraft.betterportals.BetterPortalsMod
import de.johni0702.minecraft.betterportals.LOGGER
import net.minecraft.block.state.IBlockState
import net.minecraft.client.entity.EntityOtherPlayerMP
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityTracker
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.init.Blocks
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.network.NetHandlerPlayServer
import net.minecraft.network.PacketBuffer
import net.minecraft.util.BitArray
import net.minecraft.util.EnumFacing
import net.minecraft.util.LazyLoadBase
import net.minecraft.util.Rotation
import net.minecraft.util.math.*
import net.minecraft.world.World
import net.minecraft.world.WorldServer
import net.minecraft.world.chunk.BlockStateContainer
import net.minecraft.world.chunk.BlockStatePaletteHashMap
import net.minecraft.world.chunk.BlockStatePaletteLinear
import net.minecraftforge.fml.common.eventhandler.EventBus
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext
import javax.vecmath.*
import kotlin.math.max
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

// Generic
fun <T> MutableList<T>.removeAtOrNull(index: Int) = if (isEmpty()) null else removeAt(index)
fun <T> MutableList<T>.popOrNull() = removeAtOrNull(0)
fun <T> MutableList<T>.takeLast() = removeAt(lastIndex)
operator fun Matrix4d.times(other: Matrix4d) = Mat4d.id().also { it.mul(this, other) }
operator fun Matrix4d.times(other: Vector3d) = Vector3d().also { transform(other, it) }
operator fun Matrix4d.times(other: Point3d) = Point3d().also { transform(other, it) }
inline operator fun <reified T : Tuple4d> Matrix4d.times(other: T) = (other.clone() as T).also { transform(other, it) }

// MC
val EnumFacing.Plane.axes get() = EnumFacing.Axis.values().filter { it.plane == this }
val EnumFacing.Plane.perpendicularAxes get() = opposite.axes
val EnumFacing.Plane.opposite get() = when(this) {
    EnumFacing.Plane.HORIZONTAL -> EnumFacing.Plane.VERTICAL
    EnumFacing.Plane.VERTICAL -> EnumFacing.Plane.HORIZONTAL
}
fun Rotation.axis(plane: EnumFacing.Plane): EnumFacing.Axis = when(plane) {
    EnumFacing.Plane.HORIZONTAL -> this.facing.axis
    EnumFacing.Plane.VERTICAL -> EnumFacing.Axis.Y
}
val EnumFacing.Axis.perpendicularPlane get() = plane.opposite
fun EnumFacing.Axis.toFacing(direction: EnumFacing.AxisDirection): EnumFacing = EnumFacing.getFacingFromAxis(direction, this)
fun EnumFacing.Axis.toFacing(direction: Double)
        = toFacing(if (direction > 0) EnumFacing.AxisDirection.POSITIVE else EnumFacing.AxisDirection.NEGATIVE)
fun EnumFacing.Axis.toFacing(direction: Vec3d) = toFacing(direction[this])
val EnumFacing.Axis.parallelFaces get() = EnumFacing.values().filter { it.axis != this }
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
operator fun Vec3d.get(axis: EnumFacing.Axis) = when(axis) {
    EnumFacing.Axis.X -> x
    EnumFacing.Axis.Y -> y
    EnumFacing.Axis.Z -> z
}
fun Vec3d.with(axis: EnumFacing.Axis, value: Double) = when(axis) {
    EnumFacing.Axis.X -> Vec3d(value, y, z)
    EnumFacing.Axis.Y -> Vec3d(x, value, z)
    EnumFacing.Axis.Z -> Vec3d(x, y, value)
}
fun Vec3d.rotate(rot: Rotation): Vec3d = when(rot) {
    Rotation.NONE -> this
    Rotation.CLOCKWISE_90 -> Vec3d(-z, y, x)
    Rotation.CLOCKWISE_180 -> Vec3d(-x, y, -z)
    Rotation.COUNTERCLOCKWISE_90 -> Vec3d(z, y, -x)
}
fun EnumFacing.toRotation(): Rotation = if (horizontalIndex == -1) Rotation.NONE else Rotation.values()[horizontalIndex]
val Rotation.facing: EnumFacing get() = EnumFacing.HORIZONTALS[ordinal]
val Rotation.degrees get() = ordinal * 90
val Rotation.reverse get() = when(this) {
    Rotation.CLOCKWISE_90 -> Rotation.COUNTERCLOCKWISE_90
    Rotation.COUNTERCLOCKWISE_90 -> Rotation.CLOCKWISE_90
    else -> this
}
operator fun Rotation.plus(other: Rotation): Rotation = add(other)
operator fun Rotation.minus(other: Rotation): Rotation = add(other.reverse)
val AxisAlignedBB_INFINITE = AxisAlignedBB(
        Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY,
        Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY
)
fun AxisAlignedBB.with(facing: EnumFacing, value: Double) = when(facing) {
    EnumFacing.DOWN -> AxisAlignedBB(minX, value, minZ, maxX, maxY, maxZ)
    EnumFacing.UP -> AxisAlignedBB(minX, minY, minZ, maxX, value, maxZ)
    EnumFacing.NORTH -> AxisAlignedBB(minX, minY, value, maxX, maxY, maxZ)
    EnumFacing.SOUTH -> AxisAlignedBB(minX, minY, minZ, maxX, maxY, value)
    EnumFacing.WEST -> AxisAlignedBB(value, minY, minZ, maxX, maxY, maxZ)
    EnumFacing.EAST -> AxisAlignedBB(minX, minY, minZ, value, maxY, maxZ)
}
fun AxisAlignedBB.grow(by: Vec3d): AxisAlignedBB = grow(by.x, by.y, by.z)
fun AxisAlignedBB.expand(by: Vec3d): AxisAlignedBB = expand(by.x, by.y, by.z)
fun AxisAlignedBB.contract(by: Vec3d): AxisAlignedBB = contract(by.x, by.y, by.z)
val AxisAlignedBB.sizeX get() = maxX - minX
val AxisAlignedBB.sizeY get() = maxY - minY
val AxisAlignedBB.sizeZ get() = maxZ - minZ
val AxisAlignedBB.maxSideLength get() = max(sizeX, max(sizeY, sizeZ))
val AxisAlignedBB.min get() = Vec3d(minX, minY, minZ)
val AxisAlignedBB.max get() = Vec3d(maxX, maxY, maxZ)
// Note: the obvious choice of constructor is @SideOnly(Client)
fun Vec3d.toAxisAlignedBB(other: Vec3d) = AxisAlignedBB(x, y, z, other.x, other.y, other.z)
fun Collection<BlockPos>.toAxisAlignedBB(): AxisAlignedBB =
        if (isEmpty()) AxisAlignedBB(BlockPos.ORIGIN, BlockPos.ORIGIN)
        else map(::AxisAlignedBB).reduce(AxisAlignedBB::union)
fun Collection<BlockPos>.minByAnyCoord() =
        minWith(Comparator.comparingInt<BlockPos> { it.x }.thenComparingInt { it.y }.thenComparingInt { it.z })

fun World?.sync(task: () -> Unit) = BetterPortalsMod.PROXY.sync(this, task)
fun MessageContext.sync(task: () -> Unit) = (this.netHandler as? NetHandlerPlayServer)?.player?.world.sync(task)

fun <L : ListenableFuture<T>, T> L.logFailure(): L {
    Futures.addCallback(this, object : FutureCallback<T> {
        override fun onSuccess(result: T?) = Unit
        override fun onFailure(t: Throwable) {
            LOGGER.error("Failed future:", t)
        }
    })
    return this
}

fun NBTTagCompound.setXYZ(pos: BlockPos): NBTTagCompound {
    setInteger("x", pos.x)
    setInteger("y", pos.y)
    setInteger("z", pos.z)
    return this
}
fun NBTTagCompound.getXYZ(): BlockPos = BlockPos(getInteger("x"), getInteger("y"), getInteger("z"))

inline fun <reified E : Enum<E>> PacketBuffer.readEnum(): E = readEnumValue(E::class.java)
inline fun <reified E : Enum<E>> PacketBuffer.writeEnum(value: E): PacketBuffer = writeEnumValue(value)

@Suppress("UNCHECKED_CAST") // Why forge? why?
fun EntityTracker.getTracking(entity: Entity): Set<EntityPlayerMP> = getTrackingPlayers(entity) as Set<EntityPlayerMP>

val Entity.eyeOffset get() = Vec3d(0.0, eyeHeight.toDouble(), 0.0)
val Entity.syncPos get() = when {
    this is EntityOtherPlayerMP && otherPlayerMPPosRotationIncrements > 0 -> otherPlayerMPPos
    else -> pos
}
var Entity.pos
    get() = Vec3d(posX, posY, posZ)
    set(value) = with(value) { posX = x; posY = y; posZ = z }
var Entity.lastTickPos
    get() = Vec3d(lastTickPosX, lastTickPosY, lastTickPosZ)
    set(value) = with(value) { lastTickPosX = x; lastTickPosY = y; lastTickPosZ = z }
var Entity.prevPos
    get() = Vec3d(prevPosX, prevPosY, prevPosZ)
    set(value) = with(value) { prevPosX = x; prevPosY = y; prevPosZ = z }
var EntityOtherPlayerMP.otherPlayerMPPos
    get() = Vec3d(otherPlayerMPX, otherPlayerMPY, otherPlayerMPZ)
    set(value) = with(value) { otherPlayerMPX = x; otherPlayerMPY = y; otherPlayerMPZ = z }

fun ChunkPos.add(x: Int, z: Int) = ChunkPos(this.x + x, this.z + z)

val WorldServer.server get() = minecraftServer!!

fun World.forceSpawnEntity(entity: Entity) {
    val wasForceSpawn = entity.forceSpawn
    entity.forceSpawn = true
    spawnEntity(entity)
    entity.forceSpawn = wasForceSpawn
}

fun World.makeBlockCache(forceLoad: Boolean = true): BlockCache =
        HashMap<BlockPos, IBlockState>().let { cache ->
            Gettable { pos ->
                cache.getOrPut(pos) {
                    if (forceLoad || isBlockLoaded(pos)) getBlockState(pos) else Blocks.AIR.defaultState
                }
            }
        }

fun World.makeChunkwiseBlockCache(forceLoad: Boolean = true): BlockCache =
        HashMap<ChunkPos, List<BlockStateContainer?>>().let { cache ->
            Gettable { pos ->
                val chunkPos = ChunkPos(pos)
                val storageLists = cache.getOrPut(chunkPos) {
                    if (forceLoad || isChunkGeneratedAt(chunkPos.x, chunkPos.z)) {
                        getChunkFromChunkCoords(chunkPos.x, chunkPos.z).blockStorageArray.map { it?.data?.copy() }
                    } else {
                        emptyList()
                    }
                }
                storageLists.getOrNull(pos.y shr 4)?.get(pos.x and 15, pos.y and 15, pos.z and 15)
                        ?: Blocks.AIR.defaultState
            }
        }

fun BlockStateContainer.copy(): BlockStateContainer {
    val copy = BlockStateContainer()
    copy.bits = bits
    copy.palette = when {
        bits <= 4 -> BlockStatePaletteLinear(bits, copy).apply {
            val oldPalette = palette as BlockStatePaletteLinear
            System.arraycopy(oldPalette.states, 0, states, 0, states.size)
            arraySize = oldPalette.arraySize
        }
        bits <= 8 -> BlockStatePaletteHashMap(bits, copy).apply {
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
