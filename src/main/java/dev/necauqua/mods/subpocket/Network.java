/*
 * Copyright (c) 2017-2020 Anton Bulakh <self@necauqua.dev>
 * Licensed under MIT, see the LICENSE file for details.
 */

package dev.necauqua.mods.subpocket;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.apache.logging.log4j.LogManager;

import javax.annotation.Nullable;

import static dev.necauqua.mods.subpocket.Subpocket.MODID;

@EventBusSubscriber(modid = MODID, bus = Bus.MOD)
public final class Network {

    public record SyncStoragePayload(CompoundTag nbt) implements CustomPacketPayload {
        public static final Type<SyncStoragePayload> TYPE = new Type<>(Subpocket.ns("sync_storage"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SyncStoragePayload> STREAM_CODEC =
            StreamCodec.composite(
                ByteBufCodecs.COMPOUND_TAG,
                SyncStoragePayload::nbt,
                SyncStoragePayload::new
            );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(SyncStoragePayload payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                var player = context.player();
                if (player != null) {
                    SubpocketCapability.get(player).deserializeNBT(player.registryAccess(), payload.nbt());
                } else {
                    LogManager.getLogger(Subpocket.class).warn("Clientside player was null on sync!");
                }
            });
        }
    }

    public record ClickActionPayload(float x, float y, int index, byte clickState) implements CustomPacketPayload {
        public static final Type<ClickActionPayload> TYPE = new Type<>(Subpocket.ns("click_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ClickActionPayload> STREAM_CODEC =
            StreamCodec.composite(
                ByteBufCodecs.FLOAT, ClickActionPayload::x,
                ByteBufCodecs.FLOAT, ClickActionPayload::y,
                ByteBufCodecs.VAR_INT, ClickActionPayload::index,
                ByteBufCodecs.BYTE, ClickActionPayload::clickState,
                ClickActionPayload::new
            );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(ClickActionPayload payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                var player = context.player();
                if (player != null && player.containerMenu instanceof SubpocketContainer container) {
                    if (payload.clickState() == 0) {
                        container.stackMoved(payload.x(), payload.y(), payload.index());
                    } else {
                        container.processClick(payload.x(), payload.y(), new ClickState(payload.clickState()), payload.index());
                    }
                }
            });
        }
    }

    @SubscribeEvent
    public static void on(RegisterPayloadHandlersEvent e) {
        var registrar = e.registrar("1.0.0");
        registrar.playToClient(SyncStoragePayload.TYPE, SyncStoragePayload.STREAM_CODEC, SyncStoragePayload::handle);
        registrar.playToServer(ClickActionPayload.TYPE, ClickActionPayload.STREAM_CODEC, ClickActionPayload::handle);
    }

    public static void syncToClient(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            syncToClient(serverPlayer);
        }
    }

    public static void syncToClient(ServerPlayer player) {
        var nbt = SubpocketCapability.get(player).serializeNBT(player.registryAccess());
        PacketDistributor.sendToPlayer(player, new SyncStoragePayload(nbt));
    }

    @OnlyIn(Dist.CLIENT)
    public static void sendClickToServer(float x, float y, int index, @Nullable ClickState state) {
        PacketDistributor.sendToServer(new ClickActionPayload(x, y, index, state != null ? state.toByte() : 0));
    }
}
