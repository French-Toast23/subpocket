/*
 * Copyright (c) 2017-2020 Anton Bulakh <self@necauqua.dev>
 * Licensed under MIT, see the LICENSE file for details.
 */

package dev.necauqua.mods.subpocket;

import dev.necauqua.mods.subpocket.api.ISubpocket;
import dev.necauqua.mods.subpocket.impl.SubpocketImpl;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

import static dev.necauqua.mods.subpocket.Subpocket.MODID;

@EventBusSubscriber(modid = MODID)
public final class SubpocketCapability {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
        DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, MODID);

    public static final Supplier<AttachmentType<SubpocketImpl>> TYPE =
        ATTACHMENT_TYPES.register("storage", () -> AttachmentType.serializable(SubpocketImpl::new)
            .copyHandler((attachment, holder, provider) -> {
                var copy = new SubpocketImpl();
                copy.cloneFrom(attachment);
                return copy;
            })
            .copyOnDeath()
            .build());

    public static ISubpocket get(Player player) {
        return player.getData(TYPE);
    }

    @SubscribeEvent
    public static void on(PlayerLoggedInEvent e) {
        Network.syncToClient(e.getEntity());
    }

    @SubscribeEvent
    public static void on(PlayerChangedDimensionEvent e) {
        Network.syncToClient(e.getEntity());
    }

    @SubscribeEvent
    public static void on(PlayerRespawnEvent e) {
        Network.syncToClient(e.getEntity());
    }
}
