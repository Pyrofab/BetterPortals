package de.johni0702.minecraft.betterportals.net

import com.raphydaphy.crochet.network.IPacket
import com.raphydaphy.crochet.network.MessageHandler
import de.johni0702.minecraft.betterportals.BetterPortalsMod
import de.johni0702.minecraft.betterportals.MOD_ID
import de.johni0702.minecraft.betterportals.common.sync
import net.fabricmc.fabric.api.network.PacketContext
import net.minecraft.client.MinecraftClient
import net.minecraft.client.world.ClientWorld
import net.minecraft.util.Identifier
import net.minecraft.util.PacketByteBuf
import net.minecraft.util.registry.Registry
import net.minecraft.world.Difficulty
import net.minecraft.world.GameMode
import net.minecraft.world.dimension.DimensionType
import net.minecraft.world.level.LevelGeneratorType
import net.minecraft.world.level.LevelInfo

class CreateView(
    var viewId: Int = 0,
    var dimension: DimensionType = DimensionType.OVERWORLD,
    var difficulty: Difficulty? = null,
    var viewDistance: Int = 3,
    var gameType: GameMode? = null,
    var worldType: LevelGeneratorType? = null
) : IPacket {

    override fun getID() = ID

    override fun read(buf: PacketByteBuf) {
        viewId = buf.readInt()
        dimension = Registry.DIMENSION.get(buf.readIdentifier()) ?: DimensionType.OVERWORLD
        difficulty = Difficulty.getDifficulty(buf.readUnsignedByte().toInt())
        gameType = GameMode.byId(buf.readUnsignedByte().toInt())
        worldType = LevelGeneratorType.getTypeFromName(buf.readString(16))
        if (worldType == null) {
            worldType = LevelGeneratorType.DEFAULT
        }
    }

    override fun write(buf: PacketByteBuf) {
        buf.writeInt(viewId)
        buf.writeIdentifier(Registry.DIMENSION.getId(dimension))
        buf.writeByte(difficulty!!.id)
        buf.writeByte(gameType!!.id)
        buf.writeString(worldType!!.name)
    }

    internal companion object Handler : MessageHandler<CreateView>() {
        val ID = Identifier(MOD_ID, "create_view")

        override fun handle(ctx: PacketContext, message: CreateView) {
            ctx.sync {
                val mc = MinecraftClient.getInstance()
                val world = ClientWorld(mc.networkHandler!!,
                    LevelInfo(0L,
                        message.gameType!!,
                        false,
                        mc.world.levelProperties.isHardcore,
                        message.worldType!!),
                    message.dimension,
                    message.viewDistance,
                    mc.profiler,
                    mc.worldRenderer)
                world.levelProperties.difficulty = message.difficulty
                BetterPortalsMod.viewManagerImpl.createView(message.viewId, world)
            }
        }

        override fun create() = CreateView()
    }
}
