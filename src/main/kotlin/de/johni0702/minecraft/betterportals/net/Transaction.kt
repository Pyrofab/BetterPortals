package de.johni0702.minecraft.betterportals.net

import com.raphydaphy.crochet.network.IPacket
import com.raphydaphy.crochet.network.MessageHandler
import de.johni0702.minecraft.betterportals.LOGGER
import de.johni0702.minecraft.betterportals.MOD_ID
import de.johni0702.minecraft.betterportals.client.view.ViewDemuxingTaskQueue
import de.johni0702.minecraft.betterportals.common.*
import de.johni0702.minecraft.betterportals.server.view.viewManager
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import net.fabricmc.fabric.api.network.PacketContext
import net.minecraft.client.Minecraft
import net.minecraft.entity.player.ServerPlayerEntity
import net.minecraft.network.PacketBuffer
import net.minecraft.network.play.server.SPacketCustomPayload
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import net.minecraft.util.PacketByteBuf
import net.minecraft.util.Util
import net.minecraftforge.fml.common.network.simpleimpl.IPacket
import net.minecraftforge.fml.common.network.simpleimpl.IPacketHandler
import net.minecraftforge.fml.common.network.simpleimpl.PacketContext
import java.util.concurrent.*

class Transaction(
        var phase: Phase = Phase.START
) : IPacket {
    override fun read(buf: PacketByteBuf) {
        with(buf) {
            phase = readEnum()
        }
    }

    override fun write(buf: PacketByteBuf) {
        with(buf) {
            writeEnum(phase)
        }
    }

    internal object Handler : MessageHandler<Transaction>() {
        override fun create() = Transaction()
        override fun handle(ctx: PacketContext, message: Transaction) {
            when(message.phase) {
                Transaction.Phase.START -> {
                    // We need to block the network thread until MC's queue has been replaced.
                    // Otherwise the network thread might be quick enough to synchronize on the original queue, deadlocking
                    val syncedSemaphore = Semaphore(0)
                    ctx.sync {
                        if (inTransaction++ == 0) {
                            val mc = Minecraft.getMinecraft()

                            // Replaces MC's queue with a new queue which we aren't currently synchronized on
                            val orgQueue = mc.scheduledTasks
                            val backingQueue = LinkedBlockingQueue<FutureTask<*>>()
                            val tmpQueue = ViewDemuxingTaskQueue(mc, backingQueue)
                            // Drain the original queue into our new one before replacing, to keep task order
                            tmpQueue.addAll(orgQueue)
                            mc.scheduledTasks = tmpQueue

                            // MC's queue has been replaced, the networking thread may continue
                            syncedSemaphore.release()

                            // Block main loop / run tasks while in transaction
                            while (inTransaction > 0) {
                                Util.runTask(backingQueue.poll(Long.MAX_VALUE, TimeUnit.NANOSECONDS), LOGGER)
                            }

                            // Restore original queue
                            synchronized(mc.scheduledTasks) {
                                orgQueue.addAll(mc.scheduledTasks)
                                mc.scheduledTasks = orgQueue
                            }
                        } else {
                            syncedSemaphore.release()
                        }
                    }
                    // Wait for main thread to be ready
                    syncedSemaphore.acquire()
                }
                Transaction.Phase.END -> {
                    ctx.sync {
                        if (inTransaction <= 0) throw IllegalStateException("Transaction end without start!")
                        inTransaction--
                    }
                }
            }
            return null
        }
    }

    enum class Phase {
        START, END
    }

    companion object {
        val ID = Identifier(MOD_ID, "transaction")
        private var inTransaction = 0

        fun start(player: ServerPlayerEntity) {
            with(player.viewManager.player) {
                connection.sendPacket(SPacketCustomPayload("$MOD_ID|TS", PacketBuffer(Unpooled.EMPTY_BUFFER)))
                Transaction(Phase.START).sendTo(this)
            }
        }

        fun end(player: ServerPlayerEntity) {
            with(player.viewManager.player) {
                Transaction(Phase.END).sendTo(this)
                connection.sendPacket(SPacketCustomPayload("$MOD_ID|TE", PacketBuffer(Unpooled.EMPTY_BUFFER)))
            }
        }
    }
}