package de.johni0702.minecraft.betterportals.common.entity

import com.raphydaphy.crochet.network.PacketHandler
import de.johni0702.minecraft.betterportals.BPConfig
import de.johni0702.minecraft.betterportals.LOGGER
import de.johni0702.minecraft.betterportals.client.UtilsClient
import de.johni0702.minecraft.betterportals.client.view.ClientView
import de.johni0702.minecraft.betterportals.common.*
import de.johni0702.minecraft.betterportals.net.*
import de.johni0702.minecraft.betterportals.server.view.ServerView
import de.johni0702.minecraft.betterportals.server.view.viewManager
import io.netty.buffer.ByteBuf
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.block.material.Material
import net.minecraft.client.Minecraft
import net.minecraft.client.entity.AbstractClientPlayer
import net.minecraft.client.network.OtherClientPlayerEntity
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.client.renderer.culling.ICamera
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityList
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.ServerPlayerEntity
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.PacketBuffer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.math.Direction
import net.minecraft.util.Rotation
import net.minecraft.util.math.BoundingBox
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World
import net.minecraft.server.world.ServerWorld
import net.minecraftforge.common.ForgeHooks
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.entity.living.LivingFallEvent
import net.minecraftforge.event.world.GetCollisionBoxesEvent
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.Environment

abstract class AbstractPortalEntity(
        world: World,
        override var plane: Direction.Type,
        override var relativeBlocks: Set<BlockPos>,
        override var localDimension: Int,
        localPosition: BlockPos,
        localRotation: Rotation,
        override var remoteDimension: Int?,
        override var remotePosition: BlockPos,
        override var remoteRotation: Rotation
) : Entity(world), Portal.Mutable, IEntityAdditionalSpawnData {

    override fun getRenderBoundingBox(): BoundingBox = localBoundingBox
    override var localPosition = localPosition
        set(value) {
            field = value
            with(value.to3d()) { setPosition(x + 0.5, y + 0.5, z + 0.5) }
        }
    override var localRotation = localRotation
        set(value) {
            field = value
            setRotation(value.degrees.toFloat(), 0f)
        }

    init {
        // MC checks whether entities are completely inside the view frustum which is completely useless/broken for
        // large entities.
        // The sane solution would have been to check if they are partially inside it.
        // If required for performance, that check may be implemented in Render#shouldRender but I'm not doing it now.
        // I blame MC
        ignoreFrustumCheck = true

        width = 0f
        height = 0f

        with(localPosition.to3d()) { setPosition(x + 0.5, y + 0.5, z + 0.5) }
        this.setRotation(localRotation.degrees.toFloat(), 0f)
    }


    override fun entityInit() {
    }

    override fun onUpdate() {
        if (!EventHandler.registered) {
            EventHandler.registered = true
        }

        if (world.isRemote) {
            onClientUpdate()
        }
    }

    private var lastTickPos = mutableMapOf<Entity, Vec3d>()
    private var thisTickPos = mutableMapOf<Entity, Vec3d>()
    protected open fun checkTeleportees() {
        val facingVec = localFacing.directionVec.to3d().abs() * 2
        val largerBB = localBoundingBox.grow(facingVec)
        val finerBBs = localBlocks.map { BoundingBox(it).grow(facingVec) }
        world.getEntitiesWithinAABBExcludingEntity(this, largerBB).forEach {
            val entityBB = it.entityBoundingBox
            if (finerBBs.any { entityBB.intersects(it) }) {
                checkTeleportee(it)
            }
        }
        lastTickPos = thisTickPos.also {
            thisTickPos = lastTickPos
            thisTickPos.clear()
        }
    }

    protected open fun checkTeleportee(entity: Entity) {
        val portalPos = pos
        val entityPos = entity.pos + entity.eyeOffset
        thisTickPos[entity] = entityPos
        val entityPrevPos = lastTickPos[entity] ?: return
        val relPos = entityPos - portalPos
        val prevRelPos = entityPrevPos - portalPos
        val from = localAxis.toFacing(relPos)
        val prevFrom = localAxis.toFacing(prevRelPos)

        if (from != prevFrom) {
            teleportEntity(entity, prevFrom)
        }
    }

    protected open fun teleportEntity(entity: Entity, from: Direction) {
        thisTickPos.remove(entity)

        if (entity.isRiding || entity.isBeingRidden) {
            return // just do nothing for now, not even dismounting works as one would hope
        }

        if (entity is PlayerEntity) {
            if (world.isRemote) teleportPlayer(entity, from)
            return
        }

        if (!world.isRemote) {
            val remotePortal = getRemotePortal()!!
            val localWorld = world as ServerWorld
            val remoteWorld = remotePortal.world as ServerWorld

            if (!ForgeHooks.onTravelToDimension(entity, remotePortal.dimension)) return

            val newEntity = EntityList.newEntity(entity.javaClass, remoteWorld) ?: return

            // Inform other clients that the entity is going to be teleported
            val trackingPlayers = localWorld.entityTracker.getTracking(entity).intersect(views.keys)
            trackingPlayers.forEach {
                Transaction.start(it)
                it.viewManager.flushPackets()
            }
            EntityUsePortal(EntityUsePortal.Phase.BEFORE, entity.entityId, this.entityId).sendTo(trackingPlayers)

            localWorld.removeEntityDangerously(entity)
            localWorld.resetUpdateEntityTick()

            entity.dimension = remotePortal.dimension
            entity.isDead = false
            newEntity.readFromNBT(entity.writeToNBT(CompoundTag()))
            entity.isDead = true

            Utils.transformPosition(entity, newEntity, this)

            remoteWorld.forceSpawnEntity(newEntity)
            // TODO Vanilla does an update here, not sure if that's necessary?
            //remoteWorld.updateEntityWithOptionalForce(newEntity, false)
            remoteWorld.resetUpdateEntityTick()

            // Inform other clients that the teleportation has happened
            trackingPlayers.forEach { it.viewManager.flushPackets() }
            EntityUsePortal(EntityUsePortal.Phase.AFTER, newEntity.entityId, this.entityId).sendTo(trackingPlayers)
            trackingPlayers.forEach { Transaction.end(it) }
        }
    }

    override fun canBeAttackedWithItem(): Boolean = true
    override fun canBeCollidedWith(): Boolean = true
    override fun canBePushed(): Boolean = false
    override fun hitByEntity(entityIn: Entity?): Boolean = true

    override fun shouldSetPosAfterLoading(): Boolean = false
    override fun readEntityFromNBT(compound: CompoundTag) {
        readPortalFromNBT(compound.getTag("BetterPortal"))
    }

    override fun writeEntityToNBT(compound: CompoundTag) {
        compound.setTag("BetterPortal", writePortalToNBT())
    }

    override fun readSpawnData(additionalData: ByteBuf) {
        readPortalFromNBT(PacketBuffer(additionalData).readCompoundTag())
    }

    override fun writeSpawnData(buffer: ByteBuf) {
        PacketBuffer(buffer).writeCompoundTag(writePortalToNBT())
    }

    internal object EventHandler {
        var registered by MinecraftForge.EVENT_BUS
        var collisionBoxesEntity: Entity? = null

        @SubscribeEvent
        fun onWorldTick(event: TickEvent.WorldTickEvent) {
            if (event.phase != TickEvent.Phase.END) return
            if (event.side != Side.SERVER) return
            tickWorld(event.world)
        }

        @SubscribeEvent
        fun onClientTick(event: TickEvent.ClientTickEvent) {
            if (event.phase != TickEvent.Phase.END) return
            val world = Minecraft.getMinecraft().world
            if (world != null) {
                tickWorld(world)
            }
        }

        private fun tickWorld(world: World) {
            world.getEntities(AbstractPortalEntity::class.java, { it?.isDead == false }).forEach {
                it.checkTeleportees()
            }
        }

        @SubscribeEvent(priority = EventPriority.LOW)
        fun onGetCollisionBoxes(event: GetCollisionBoxesEvent) {
            val entity = event.entity ?: collisionBoxesEntity ?: return
            modifyAABBs(entity, event.aabb, event.aabb, event.collisionBoxesList) { world, aabb ->
                world.getCollisionBoxes(null, aabb)
            }
        }

        fun onIsOpenBlockSpace(entity: Entity, pos: BlockPos): Boolean {
            val query = { world: World, aabb: BoundingBox ->
                val blockPos = aabb.min.toBlockPos()
                val blockState = world.getBlockState(blockPos)
                if (blockState.block.isNormalCube(blockState, world, blockPos)) {
                    mutableListOf(BoundingBox(blockPos))
                } else {
                    mutableListOf()
                }
            }
            val aabbList = query(entity.world, BoundingBox(pos))
            modifyAABBs(entity, entity.entityBoundingBox, BoundingBox(pos), aabbList, query)
            return aabbList.isEmpty()
        }

        private fun modifyAABBs(
                entity: Entity,
                entityAABB: BoundingBox,
                queryAABB: BoundingBox,
                aabbList: MutableList<BoundingBox>,
                queryRemote: (World, BoundingBox) -> List<BoundingBox>
        ) {
            val world = entity.world
            world.getEntities(AbstractPortalEntity::class.java) { it?.isDead == false }.forEach { portal ->
                if (!portal.localBoundingBox.intersects(entityAABB)) return@forEach // not even close

                val remotePortal = portal.getRemotePortal()
                if (remotePortal == null) {
                    // Remote portal hasn't yet been loaded, treat all portal blocks as solid to prevent passing
                    portal.localBlocks.forEach {
                        val blockAABB = BoundingBox(it)
                        if (blockAABB.intersects(entityAABB)) {
                            aabbList.add(blockAABB)
                        }
                    }
                    return@forEach
                }

                // If this is a non-rectangular portal and the entity isn't inside it, we don't care
                if (portal.localBlocks.none { BoundingBox(it).intersects(entityAABB) }) return@forEach

                // otherwise, we need to remove all collision boxes on the other, local side of the portal
                // to prevent the entity from colliding with them
                val portalPos = portal.localPosition.to3dMid()
                val entityPos = portal.lastTickPos[entity] ?: (entity.pos + entity.eyeOffset)
                val entitySide = portal.localFacing.axis.toFacing(entityPos - portalPos)
                val hiddenSide = entitySide.opposite
                val hiddenAABB = portal.localBoundingBox
                        .offset(hiddenSide.directionVec.to3d())
                        .expand(hiddenSide.directionVec.to3d() * Double.POSITIVE_INFINITY)
                aabbList.removeIf { it.intersects(hiddenAABB) }

                // and instead add collision boxes from the remote world
                if (!hiddenAABB.intersects(queryAABB)) return@forEach // unless we're not even interested in those
                val remoteAABB = with(portal) {
                    // Reduce the AABB which we're looking for in the first place to the hidden section
                    val aabb = hiddenAABB.intersect(queryAABB)
                    // and transform it to remote space in order to lookup collision boxes over there
                    aabb.min.fromLocal().toRemote().toBoundingBox(aabb.max.fromLocal().toRemote())
                }
                // Unset the entity while calling into the remote world since it's not valid over there
                collisionBoxesEntity = collisionBoxesEntity.also {
                    collisionBoxesEntity = null
                    val remoteCollisions = queryRemote(remotePortal.world, remoteAABB)

                    // finally transform any collision boxes back to local space and add them to the result
                    remoteCollisions.mapTo(aabbList) { aabb ->
                        with(portal) { aabb.min.fromRemote().toLocal().toBoundingBox(aabb.max.fromRemote().toLocal()) }
                    }
                }
            }
        }

        fun isInMaterial(entity: Entity, material: Material): Boolean? {
            val entityAABB = entity.entityBoundingBox
            val queryAABB = entityAABB.grow(-0.1, -0.4, -0.1)

            val world = entity.world
            world.getEntities(AbstractPortalEntity::class.java) { it?.isDead == false }.forEach { portal ->
                if (!portal.localBoundingBox.intersects(entityAABB)) return@forEach // not even close

                val remotePortal = portal.getRemotePortal() ?: return@forEach

                // If this is a non-rectangular portal and the entity isn't inside it, we don't care
                if (portal.localBlocks.none { BoundingBox(it).intersects(entityAABB) }) return@forEach

                val portalPos = portal.localPosition.to3dMid()
                val entityPos = portal.lastTickPos[entity] ?: (entity.pos + entity.eyeOffset)
                val entitySide = portal.localFacing.axis.toFacing(entityPos - portalPos)
                val hiddenSide = entitySide.opposite
                val entityHalf = BoundingBox_INFINITE.with(entitySide.opposite, portalPos[entitySide.axis])
                val hiddenHalf = BoundingBox_INFINITE.with(hiddenSide.opposite, portalPos[hiddenSide.axis])

                // For sanity, pretend there are no recursive portals

                // Split BB into local and remote side
                if (queryAABB.intersects(entityHalf)) {
                    val localAABB = queryAABB.intersect(entityHalf)
                    if (world.isMaterialInBB(localAABB, material)) {
                        return true
                    }
                }
                if (queryAABB.intersects(hiddenHalf)) {
                    val aabb = queryAABB.intersect(hiddenHalf)
                    val remoteAABB = with(portal) {
                        aabb.min.fromLocal().toRemote().toBoundingBox(aabb.max.fromLocal().toRemote())
                    }
                    if (remotePortal.world.isMaterialInBB(remoteAABB, material)) {
                        return true
                    }
                }
                return false
            }

            // Entity not in any portal, fallback to default implementation
            return null
        }
    }

    protected fun getRemotePortal(): AbstractPortalEntity? {
        val remoteWorld = if (world.isRemote) {
            (view ?: return null).camera.world
        } else {
            world.minecraftServer!!.getWorld(remoteDimension ?: return null)
        }
        return remoteWorld.getEntitiesWithinAABB(javaClass, BoundingBox(remotePosition)).firstOrNull()
    }

    //
    //  Server-side
    //

    private val views = mutableMapOf<ServerPlayerEntity, ServerView?>()

    internal open fun usePortal(player: ServerPlayerEntity): Boolean {
        val view = views[player]
        if (view == null) {
            LOGGER.warn("Received use portal request from $player which has no view for portal $this")
            return false
        }

        // Update view position
        Utils.transformPosition(player, view.camera, this)

        // Inform other clients that the entity is going to be teleported
        val trackingPlayers = player.serverWorld.entityTracker.getTracking(player).intersect(views.keys)
        trackingPlayers.forEach {
            Transaction.start(it)
            it.viewManager.flushPackets()
        }
        EntityUsePortal(EntityUsePortal.Phase.BEFORE, player.entityId, this.entityId).sendTo(trackingPlayers)

        // Swap views
        view.makeMainView()

        // Inform other clients that the teleportation has happened
        trackingPlayers.forEach { it.viewManager.flushPackets() }
        EntityUsePortal(EntityUsePortal.Phase.AFTER, player.entityId, this.entityId).sendTo(trackingPlayers)
        trackingPlayers.forEach { it.viewManager.flushPackets() }
        trackingPlayers.forEach { Transaction.end(it) }

        // In case of horizontal portals, be nice and protect the player from fall damage for the next 10 seconds
        if (plane == Direction.Type.HORIZONTAL && BPConfig.preventFallDamage) {
            PreventNextFallDamage(player)
        }

        return true
    }

    private val trackingPlayers = mutableListOf<ServerPlayerEntity>()

    override fun addTrackingPlayer(player: ServerPlayerEntity) {
        super.addTrackingPlayer(player)

        trackingPlayers.add(player)

        val viewManager = player.viewManager
        val viewId = if (viewManager.player != player) {
            val remotePortal = getRemotePortal()
            (views[player] ?: if (remotePortal != null && remotePortal.trackingPlayers.contains(viewManager.player)) {
                // The player's main view is right on the other side of this portal (in fact that's why it's loaded)
                viewManager.mainView.also { it.retain() }.also { views[player] = it }
            } else {
                // Main view could be far away from the remote portal or not even in the right dimension
                // TODO transitive portals
                return
            }).id
        } else {
            val remoteWorld = player.mcServer.getWorld(remoteDimension ?: return)
            // Choose already existing view
            val view = views[player] ?: (
                    // Or existing view close by (64 blocks, ignoring y axis)
                    viewManager.views
                            .filter { it.camera.world == remoteWorld }
                            .map { it to it.camera.pos.withoutY().distanceTo(remotePosition.to3d().withoutY()) }
                            .filter { it.second < 64 } // Arbitrarily chosen limit for max distance between cam and portal
                            .sortedBy { it.second }
                            .firstOrNull()
                            ?.first
                            ?.also { it.retain() }
                            // Or create a new one
                            ?: viewManager.createView(remoteWorld, remotePosition.to3d())
                    ).also { views[player] = it }
            view.id
        }

        LinkPortal(
                entityId,
                writePortalToNBT(),
                viewId
        ).sendTo(player)
    }

    override fun removeTrackingPlayer(player: ServerPlayerEntity) {
        super.removeTrackingPlayer(player)

        trackingPlayers.remove(player)

        views.remove(player)?.let {
            LinkPortal(
                    entityId,
                    writePortalToNBT(),
                    null
            ).sendTo(player)

            it.release()
        }
    }

    override fun link(remoteDimension: Int, remotePosition: BlockPos, remoteRotation: Rotation) {
        if (this.remoteDimension != null) {
            // Unlink all tracking players
            trackingPlayers.toList().forEach {
                removeTrackingPlayer(it)
            }
        }

        super.link(remoteDimension, remotePosition, remoteRotation)

        // Update tracking players
        trackingPlayers.toList().forEach {
            addTrackingPlayer(it)
        }
    }

    override fun setDead() {
        if (isDead) return
        super.setDead()
        if (world is ServerWorld) {
            getRemotePortal()?.setDead()
            removePortal()
        }
    }

    protected open fun removePortal() {
        localBlocks.forEach { world.setBlockToAir(it) }
    }

    //
    // Client-side
    //

    @Environment(Side.CLIENT)
    var view: ClientView? = null

    @Environment(Side.CLIENT)
    override fun isInRangeToRenderDist(distance: Double): Boolean = true // MC makes this depend on entityBoundingBox

    @Environment(Side.CLIENT)
    protected open fun onClientUpdate() {
        val player = world.getPlayers(ClientPlayerEntity::class.java) { true }[0]
        view?.let { view ->
            if (!view.isMainView) {
                UtilsClient.transformPosition(player, view.camera, this)
            }
        }
    }

    @Environment(Side.CLIENT)
    private var portalUser: Entity? = null

    @Environment(Side.CLIENT)
    fun beforeUsePortal(entity: Entity) {
        portalUser = entity
    }

    @Environment(Side.CLIENT)
    fun afterUsePortal(entityId: Int) {
        val entity = portalUser
        portalUser = null
        if (entity == null) {
            LOGGER.warn("Got unexpected post portal usage message for $this by entity with new id $entityId")
            return
        }
        if (!entity.isDead) {
            LOGGER.warn("Entity $entity is still alive post portal usage!")
        }

        val view = view
        if (view == null) {
            LOGGER.warn("Failed syncing of $entity after usage of portal $this because view has not been set")
            return
        }

        val newEntity = view.camera.world.getEntityByID(entityId)
        if (newEntity == null) {
            LOGGER.warn("Oh no! The entity $entity with new id $entityId did not reappear at the other side of $this!")
            return
        }

        val pos = newEntity.pos
        val yaw = newEntity.rotationYaw
        val pitch = newEntity.rotationPitch
        Utils.transformPosition(entity, newEntity, this)
        if (newEntity is OtherClientPlayerEntity) {
            newEntity.otherPlayerMPPos = pos // preserve otherPlayerMP pos to prevent desync
            newEntity.otherPlayerMPYaw = yaw.toDouble()
            newEntity.otherPlayerMPPitch = pitch.toDouble()
            newEntity.otherPlayerMPPosRotationIncrements = 3 // and sudden jumps
        }
        if (newEntity is AbstractClientPlayer && entity is AbstractClientPlayer) {
            newEntity.ticksElytraFlying = entity.ticksElytraFlying
            newEntity.rotateElytraX = entity.rotateElytraX
            newEntity.rotateElytraY = entity.rotateElytraY
            newEntity.rotateElytraZ = entity.rotateElytraZ
        }
    }

    @Environment(Side.CLIENT)
    protected open fun teleportPlayer(player: PlayerEntity, from: Direction): Boolean {
        if (player !is ClientPlayerEntity || player.entityId < 0) return false

        val view = view
        if (view == null) {
            LOGGER.warn("Failed to use portal $this because view has not been set")
            return false
        }
        UtilsClient.transformPosition(player, view.camera, this)

        val remotePortal = getRemotePortal()
        if (remotePortal == null) {
            LOGGER.warn("Failed to use portal $this because remote portal in $view couldn't be found")
            return false
        }

        view.makeMainView()
        PacketHandler.sendToServer(UsePortal(entityId))

        remotePortal.onClientUpdate()
        return true
    }

    @Environment(EnvType.CLIENT)
    open fun canBeSeen(camera: ICamera): Boolean =
            camera.isBoundingBoxInFrustum(renderBoundingBox)
                    && localBlocks.any { camera.isBoundingBoxInFrustum(BoundingBox(it)) }
}

/**
 * Suppresses the next fall damage a player will take (within 10 seconds).
 */
class PreventNextFallDamage(
        private val player: ServerPlayerEntity
) {
    private var registered by MinecraftForge.EVENT_BUS
    /**
     * After this timeout reaches zero, we stop listening and assume the player somehow managed to not take fall damage.
     */
    private var timeoutTicks = 10 * 20 // 10 seconds

    init {
        registered = true
    }

    @SubscribeEvent
    fun onLivingFall(event: LivingFallEvent) {
        if (event.entity !== player) return // Note: cannot use != because Entity overwrites .equals
        event.isCanceled = true
        registered = false
    }

    @SubscribeEvent
    fun onTick(event: TickEvent.ServerTickEvent) {
        if (event.phase != TickEvent.Phase.START) return

        timeoutTicks--
        if (timeoutTicks <= 0) {
            registered = false
        }
    }
}

