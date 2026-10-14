/*
 * Copyright (c) 2017-2020 Anton Bulakh <self@necauqua.dev>
 * Licensed under MIT, see the LICENSE file for details.
 */

package dev.necauqua.mods.subpocket;

import dev.necauqua.mods.subpocket.api.SubpocketAPI;
import dev.necauqua.mods.subpocket.config.Config;
import dev.necauqua.mods.subpocket.impl.SubpocketAPIImpl;
import dev.necauqua.mods.subpocket.command.BigIntArgumentType;
import dev.necauqua.mods.subpocket.command.RelativeFloatArgumentType;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

import static dev.necauqua.mods.subpocket.Subpocket.MODID;

@Mod(MODID)
public final class Subpocket {

    public static final String MODID = "subpocket";

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);

    public static final DeferredRegister<ArgumentTypeInfo<?, ?>> COMMAND_ARGUMENT_TYPES =
        DeferredRegister.create(Registries.COMMAND_ARGUMENT_TYPE, MODID);

    public static final Supplier<ArgumentTypeInfo<BigIntArgumentType, BigIntArgumentType>> BIGINT_ARGUMENT_TYPE =
        COMMAND_ARGUMENT_TYPES.register("bigint", () -> ArgumentTypeInfos.registerByClass(BigIntArgumentType.class, BigIntArgumentType.BigIntArgumentTypeInfo.INSTANCE));

    public static final Supplier<ArgumentTypeInfo<RelativeFloatArgumentType, RelativeFloatArgumentType>> RELATIVE_FLOAT_ARGUMENT_TYPE =
        COMMAND_ARGUMENT_TYPES.register("rel_float", () -> ArgumentTypeInfos.registerByClass(RelativeFloatArgumentType.class, RelativeFloatArgumentType.RelativeFloatArgumentTypeInfo.INSTANCE));

    public Subpocket(IEventBus bus, ModContainer modContainer) {
        SubpocketAPI.instance = SubpocketAPIImpl.INSTANCE;
        Config.init(modContainer);

        ITEMS.register(bus);
        COMMAND_ARGUMENT_TYPES.register(bus);
        SubpocketCapability.ATTACHMENT_TYPES.register(bus);
        SubpocketContainer.CONTAINERS.register(bus);
        LootModifiers.LOOT_MODIFIERS.register(bus);
    }

    public static ResourceLocation ns(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
