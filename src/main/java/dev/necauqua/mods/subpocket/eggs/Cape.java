package dev.necauqua.mods.subpocket.eggs;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.exceptions.AuthenticationUnavailableException;
import com.mojang.authlib.minecraft.InsecurePublicKeyException;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.minecraft.MinecraftProfileTextures;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.yggdrasil.ProfileResult;
import dev.necauqua.mods.subpocket.mixin.SkinManagerAccessor;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.net.InetAddress;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static dev.necauqua.mods.subpocket.Subpocket.MODID;
import static dev.necauqua.mods.subpocket.eggs.Name.NECAUQUA;
import static java.util.Collections.emptyMap;

@EventBusSubscriber(value = Dist.CLIENT, modid = MODID, bus = Bus.MOD)
@OnlyIn(Dist.CLIENT)
public final class Cape {

    private static final List<UUID> CAPED = Collections.singletonList(NECAUQUA);

    @SubscribeEvent
    public static void on(FMLClientSetupEvent e) {
        NeoForge.EVENT_BUS.register(Name.class);

        var skinManager = Minecraft.getInstance().getSkinManager();
        var accessor = (SkinManagerAccessor) skinManager;
        var original = accessor.subpocket$getSessionService();
        accessor.subpocket$setSessionService(new MinecraftSessionService() {

            @Override
            public MinecraftProfileTextures getTextures(GameProfile profile) {
                var textures = original.getTextures(profile);
                if (CAPED.contains(profile.getId())) {
                    var capeTexture = new MinecraftProfileTexture("https://necauqua.dev/images/cape.png", emptyMap());
                    return new MinecraftProfileTextures(textures.skin(), capeTexture, textures.elytra(), textures.signatureState());
                }
                return textures;
            }

            @Override
            public MinecraftProfileTextures unpackTextures(Property property) {
                return original.unpackTextures(property);
            }

            @Override
            public Property getPackedTextures(GameProfile profile) {
                return original.getPackedTextures(profile);
            }

            @Override
            public void joinServer(UUID profileId, String authenticationToken, String serverId) throws AuthenticationException {
                original.joinServer(profileId, authenticationToken, serverId);
            }

            @Override
            public ProfileResult hasJoinedServer(String user, String serverId, InetAddress address) throws AuthenticationUnavailableException {
                return original.hasJoinedServer(user, serverId, address);
            }

            @Override
            public ProfileResult fetchProfile(UUID profileId, boolean requireSecure) {
                return original.fetchProfile(profileId, requireSecure);
            }

            @Override
            public String getSecurePropertyValue(Property property) throws InsecurePublicKeyException {
                return original.getSecurePropertyValue(property);
            }
        });
    }
}
