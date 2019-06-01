package de.johni0702.minecraft.betterportals.common

import io.netty.util.ReferenceCounted
import net.minecraft.block.BlockState
import net.minecraft.entity.Entity
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.BoundingBox
import org.joml.Matrix4d
import org.joml.Vector3d
import org.joml.Vector4d

object Utils {
    val EMPTY_AABB = BoundingBox(0.0, 0.0, 0.0, 0.0, 0.0, 0.0)

    fun swapPosRot(e1: PlayerEntity, e2: PlayerEntity) {
        e1.x = e2.x.also { e2.x = e1.x }
        e1.y = e2.y.also { e2.y = e1.y }
        e1.z = e2.z.also { e2.z = e1.z }
        e1.prevX = e2.prevX.also { e2.prevX = e1.prevX }
        e1.prevY = e2.prevY.also { e2.prevY = e1.prevY }
        e1.prevZ = e2.prevZ.also { e2.prevZ = e1.prevZ }
        e1.prevRenderX = e2.prevRenderX.also { e2.prevRenderX = e1.prevRenderX }
        e1.prevRenderY = e2.prevRenderY.also { e2.prevRenderY = e1.prevRenderY }
        e1.prevRenderZ = e2.prevRenderZ.also { e2.prevRenderZ = e1.prevRenderZ }

        e1.yaw = e2.yaw.also { e2.yaw = e1.yaw }
        e1.pitch = e2.pitch.also { e2.pitch = e1.pitch }
        // field_7483 == cameraYaw
        e1.field_7483 = e2.field_7483.also { e2.field_7483 = e1.field_7483 }
//        e1.cameraPitch = e2.cameraPitch.also { e2.cameraPitch = e1.cameraPitch }

        e1.prevYaw = e2.prevYaw.also { e2.prevYaw = e1.prevYaw }
        e1.prevPitch = e2.prevPitch.also { e2.prevPitch = e1.prevPitch }
        // field 7505 == prevCameraYaw
        e1.field_7505 = e2.field_7505.also { e2.field_7505 = e1.field_7505 }
//        e1.prevCameraPitch = e2.prevCameraPitch.also { e2.prevCameraPitch = e1.prevCameraPitch }

        e1.headYaw = e2.headYaw.also { e2.headYaw = e1.headYaw }
        e1.prevHeadYaw = e2.prevHeadYaw.also { e2.prevHeadYaw = e1.prevHeadYaw }
        // renderYawOffset == field_6283
        e1.field_6283 = e2.field_6283.also { e2.field_6283 = e1.field_6283 }
        // prevRenderYawOffset == field_6220
        e1.field_6220 = e2.field_6220.also { e2.field_6220 = e1.field_6220 }

        e1.setPosition(e1.x, e1.y, e1.z)
        e2.setPosition(e2.x, e2.y, e2.z)

        e1.velocity = e2.velocity.also { e2.velocity = e1.velocity }
    }

    fun transformPosition(from: Entity, to: Entity, portal: Portal) {
        val rotation = portal.remoteRotation - portal.localRotation
        transformPosition(from, to, portal.localToRemoteMatrix, rotation.degrees.toFloat())
    }

    fun transformPosition(from: Entity, to: Entity, matrix: Matrix4d, yawOffset: Float) {
        with(from) { matrix * Point3d(x, y, z) }.let { pos ->
            to.setPosition(pos.x, pos.y, pos.z)
        }
        with(from) { matrix * Point3d(prevX, prevY, prevZ) }.let { pos ->
            to.prevX = pos.x
            to.prevY = pos.y
            to.prevZ = pos.z
        }
        with(from) { matrix * Point3d(prevRenderX, prevRenderY, prevRenderZ) }.let { pos ->
            to.prevRenderX = pos.x
            to.prevRenderY = pos.y
            to.prevRenderZ = pos.z
        }
        with(from) { matrix * Vector3d(velocity.x, velocity.y, velocity.z) }.let { pos ->
            to.velocity = pos.toMC()
        }

        to.yaw = from.yaw + yawOffset
        to.prevYaw = from.prevYaw + yawOffset
        to.pitch = from.pitch
        to.prevPitch = from.prevPitch

        if (to is PlayerEntity && from is PlayerEntity) {
            to.field_7483 = from.field_7483
            to.field_7505 = from.field_7505
//            to.cameraPitch = from.cameraPitch
//            to.prevCameraPitch = from.prevCameraPitch

            // Sneaking
            // to.height = from.height
            to.isSneaking = from.isSneaking
        }

        if (to is LivingEntity && from is LivingEntity) {
            to.limbAngle = from.limbAngle
            to.limbDistance = from.limbDistance
            to.lastLimbDistance = from.lastLimbDistance

            to.headYaw = from.headYaw + yawOffset
            to.prevHeadYaw = from.prevHeadYaw + yawOffset
            to.field_6283 = from.field_6283 + yawOffset
            to.field_6220 = from.field_6220 + yawOffset
        }

        // field_5973 == distanceWalkedModified
        to.field_5973 = from.field_5973
        //field_6039 == prevDistanceWalkedModified
        to.field_6039 = from.field_6039
        to.isSneaking = from.isSneaking
        to.isSprinting = from.isSprinting
    }
}

interface AReferenceCounted : ReferenceCounted {
    override fun touch(): ReferenceCounted = this
    override fun touch(hint: Any?): ReferenceCounted = this

    var refCnt: Int
    override fun refCnt(): Int = refCnt
    fun doRelease()

    override fun release(): Boolean = release(1)
    override fun release(decrement: Int): Boolean {
        if (decrement <= 0) return false
        if (refCnt <= 0) throw IllegalStateException("refCnt is already at 0")
        refCnt -= decrement
        if (refCnt <= 0) {
            doRelease()
            return true
        }
        return false
    }

    override fun retain(): ReferenceCounted = retain(1)
    override fun retain(increment: Int): ReferenceCounted {
        refCnt += increment
        return this
    }
}

class Gettable<in K, out V>(
        private val getter: (K) -> V
) {
    operator fun get(key: K): V = getter(key)
    operator fun invoke(key: K) = getter(key)
}
typealias BlockCache = Gettable<BlockPos, BlockState>

data class Point3d(val x: Double, val y: Double, val z: Double) {
    constructor(): this(0.0, 0.0, 0.0)

    fun toVector4d() = Vector4d(x, y, z, 1.0)
}

object Mat4d {
    fun id() = Matrix4d().apply { identity() }
    fun add(dx: Double, dy: Double, dz: Double) = add(Vector3d(dx, dy, dz))
    fun add(vec: Vector3d) = id().apply { setTranslation(vec) }
    fun sub(dx: Double, dy: Double, dz: Double) = sub(Vector3d(dx, dy, dz))
    fun sub(vec: Vector3d) = id().apply { setTranslation(Vector3d().also { it.negate(vec) }) }
    fun rotYaw(angle: Number) = id().apply { setRotationXYZ(0.0, -1.0 * Math.toRadians(angle.toDouble()), 0.0) }
    fun inverse(of: Matrix4d) = id().apply { invert(of) }
}
