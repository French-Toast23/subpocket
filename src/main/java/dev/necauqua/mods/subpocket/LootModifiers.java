/*
 * Copyright (c) 2017-2020 Anton Bulakh <self@necauqua.dev>
 * Licensed under MIT, see the LICENSE file for details.
 */

package dev.necauqua.mods.subpocket;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.necauqua.mods.subpocket.mixin.LootTableAccessor;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;
import net.neoforged.neoforge.event.LootTableLoadEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Set;

import static dev.necauqua.mods.subpocket.Subpocket.MODID;
import static dev.necauqua.mods.subpocket.Subpocket.ns;
import static net.minecraft.world.level.storage.loot.LootPool.lootPool;
import static net.minecraft.world.level.storage.loot.entries.NestedLootTable.lootTableReference;

@EventBusSubscriber(modid = MODID)
public final class LootModifiers {

    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> LOOT_MODIFIERS =
        DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, MODID);

    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<ClearDropsModifier>> CLEAR_DROPS =
        LOOT_MODIFIERS.register("clear_drops", () -> ClearDropsModifier.CODEC);

    private static final Set<String> injectedPools = Set.of(
        "entities/enderman",
        "chests/stronghold_corridor",
        "chests/stronghold_crossing",
        "chests/simple_dungeon"
    );

    @SubscribeEvent
    public static void on(LootTableLoadEvent e) {
        var name = e.getName();
        var path = name.getPath();
        if (!"minecraft".equals(name.getNamespace()) || !injectedPools.contains(path)) {
            return;
        }
        var accessor = (LootTableAccessor) e.getTable();
        var newPools = new ArrayList<>(accessor.subpocket$getPools());
        newPools.add(lootPool()
            .name("subpocket_injected_pool")
            .add(lootTableReference(ResourceKey.create(Registries.LOOT_TABLE, ns(path.substring(path.indexOf('/') + 1)))))
            .setBonusRolls(UniformGenerator.between(0.0F, 1.0F))
            .build());
        accessor.subpocket$setPools(newPools);
    }

    public static final class ClearDropsModifier extends LootModifier {

        public static final MapCodec<ClearDropsModifier> CODEC = RecordCodecBuilder.mapCodec(inst -> codecStart(inst).apply(inst, ClearDropsModifier::new));

        private ClearDropsModifier(LootItemCondition[] conditions) {
            super(conditions);
        }

        @Nonnull
        @Override
        protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
            return new ObjectArrayList<>();
        }

        @Override
        public MapCodec<? extends IGlobalLootModifier> codec() {
            return CODEC;
        }
    }
}
