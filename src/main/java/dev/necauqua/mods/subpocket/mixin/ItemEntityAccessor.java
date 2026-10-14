package dev.necauqua.mods.subpocket.mixin;

import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemEntity.class)
public interface ItemEntityAccessor {

    @Accessor("age")
    int subpocket$getAge();

    @Accessor("age")
    void subpocket$setAge(int age);

    @Accessor("pickupDelay")
    int subpocket$getPickupDelay();

    @Accessor("pickupDelay")
    void subpocket$setPickupDelay(int pickupDelay);
}
