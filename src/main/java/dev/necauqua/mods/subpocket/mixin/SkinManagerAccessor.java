package dev.necauqua.mods.subpocket.mixin;

import com.mojang.authlib.minecraft.MinecraftSessionService;
import net.minecraft.client.resources.SkinManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SkinManager.class)
public interface SkinManagerAccessor {

    @Accessor("sessionService")
    MinecraftSessionService subpocket$getSessionService();

    @Accessor("sessionService")
    @Mutable
    void subpocket$setSessionService(MinecraftSessionService sessionService);
}
