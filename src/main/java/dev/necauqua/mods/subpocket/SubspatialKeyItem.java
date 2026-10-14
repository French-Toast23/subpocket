/*
 * Copyright (c) 2017-2020 Anton Bulakh <self@necauqua.dev>
 * Licensed under MIT, see the LICENSE file for details.
 */

package dev.necauqua.mods.subpocket;

import dev.necauqua.mods.subpocket.config.Config;
import dev.necauqua.mods.subpocket.mixin.ItemEntityAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock;
import net.neoforged.neoforge.registries.DeferredItem;

import javax.annotation.Nullable;
import java.util.List;

import static dev.necauqua.mods.subpocket.Subpocket.MODID;

@EventBusSubscriber(modid = MODID)
public final class SubspatialKeyItem extends Item implements MenuProvider {

    public static final DeferredItem<SubspatialKeyItem> INSTANCE = Subpocket.ITEMS.registerItem("key", SubspatialKeyItem::new, new Properties().stacksTo(1).rarity(Rarity.EPIC));

    public SubspatialKeyItem(Properties properties) {
        super(properties);
    }

    @EventBusSubscriber(modid = MODID, bus = Bus.MOD)
    private static final class CreativeTabHandler {

        @SubscribeEvent
        public static void on(BuildCreativeModeTabContentsEvent e) {
            if (e.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
                e.accept(INSTANCE);
            }
        }
    }

    @Override
    public String getDescriptionId() {
        return "item." + MODID + ":key";
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide || !SubpocketCapability.get(player).isUnlocked()) {
            return InteractionResultHolder.pass(player.getItemInHand(hand));
        }
        if (player instanceof ServerPlayer) { // idk may be some fake player or something
            player.openMenu(this);
            return InteractionResultHolder.success(player.getItemInHand(hand));
        }
        return InteractionResultHolder.fail(player.getItemInHand(hand));
    }

    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        entity.clearFire();
        if (!Config.subspatialKeyFrozen.get()) {
            return false;
        }
        var accessor = (ItemEntityAccessor) entity;
        if (accessor.subpocket$getAge() >= 5999 // remove the fake item from the 'makeFakeItem' method (e.g. from /give)
            || !Config.subspatialKeyNoDespawn.get() && accessor.subpocket$getAge() != -32768 && --entity.lifespan == 0) {
            // shortening the lifespan instead of growing the age here to keep the 'frozen' look ^
            entity.discard();
        }
        if (accessor.subpocket$getPickupDelay() > 0 && accessor.subpocket$getPickupDelay() != 32767) {
            accessor.subpocket$setPickupDelay(accessor.subpocket$getPickupDelay() - 1);
        }
        return true;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean isFoil(ItemStack stack) {
        var player = Minecraft.getInstance().player;
        return player != null && SubpocketCapability.get(player).isUnlocked();
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (!isFoil(stack)) {
            tooltip.add(Component.translatable("item." + MODID + ":key.desc"));
        }
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new SubpocketContainer(id, inventory);
    }

    @Override
    public Component getDisplayName() {
        return CommonComponents.EMPTY;
    }

    @SubscribeEvent
    public static void on(RightClickBlock e) {
        if (!Config.blockEnderChests.get() || e.getLevel().getBlockState(e.getPos()).getBlock() != Blocks.ENDER_CHEST) {
            return;
        }
        var player = e.getEntity();
        if (player.isCreative() || !SubpocketCapability.get(player).isUnlocked()) {
            return;
        }
        if (!player.level().isClientSide) {
            player.displayClientMessage(Component.translatable("popup." + MODID + ":blocked_ender_chest"), true);
        }
        e.setUseBlock(TriState.FALSE);
        e.setCanceled(true);
    }

    @SubscribeEvent
    public static void on(EntityJoinLevelEvent event) {
        var entity = event.getEntity();
        if (!(entity instanceof ItemEntity entityItem) || entityItem.getItem().getItem() != INSTANCE.get()) {
            return;
        }
        entity.setInvulnerable(true);
        if (Config.subspatialKeyNoDespawn.get()) {
            ((ItemEntityAccessor) entityItem).subpocket$setAge(-32768);
        }
    }

    @Override
    public void onCraftedBy(ItemStack stack, Level level, Player player) {
        if (!level.isClientSide) {
            var storage = SubpocketCapability.get(player);
            if (!storage.isUnlocked()) {
                storage.unlock();
                Network.syncToClient(player);
            }
        }
    }

    @SubscribeEvent
    public static void on(PlayerEvent.ItemCraftedEvent e) {
        if (e.getCrafting().getItem() == INSTANCE.get()) {
            var player = e.getEntity();
            if (!player.level().isClientSide) {
                var storage = SubpocketCapability.get(player);
                if (!storage.isUnlocked()) {
                    storage.unlock();
                    Network.syncToClient(player);
                }
            }
        }
    }

    @SubscribeEvent
    public static void on(PlayerInteractEvent.LeftClickBlock e) {
        if (e.getItemStack().getItem() == INSTANCE.get()) {
            e.setUseBlock(TriState.FALSE);
        }
    }

    public static boolean allowCreativeDestroy(Player player, Level level, BlockPos pos) {
        return player.getMainHandItem().getItem() != INSTANCE.get()
            || level.getBlockState(pos).getDestroySpeed(level, pos) < 0.0F && !Config.allowBreakingUnbreakable.get();
    }
}
