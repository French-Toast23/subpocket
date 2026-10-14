/*
 * Copyright (c) 2017-2020 Anton Bulakh <self@necauqua.dev>
 * Licensed under MIT, see the LICENSE file for details.
 */

package dev.necauqua.mods.subpocket;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import com.mojang.blaze3d.vertex.VertexSorting;
import dev.necauqua.mods.subpocket.api.ISubpocket;
import dev.necauqua.mods.subpocket.api.ISubpocketStack;
import dev.necauqua.mods.subpocket.config.Config;
import dev.necauqua.mods.subpocket.config.PickingMode;
import dev.necauqua.mods.subpocket.config.StackCountCondition;
import dev.necauqua.mods.subpocket.config.StackCountSize;
import dev.necauqua.mods.subpocket.mixin.CompositeRenderTypeAccessor;
import dev.necauqua.mods.subpocket.mixin.CompositeStateAccessor;
import dev.necauqua.mods.subpocket.mixin.FontAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions.FontContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.*;

import static com.mojang.blaze3d.platform.GlStateManager.LogicOp.XOR;
import static com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS;
import static dev.necauqua.mods.subpocket.Subpocket.MODID;
import static dev.necauqua.mods.subpocket.Subpocket.ns;
import static dev.necauqua.mods.subpocket.SubpocketContainer.HEIGHT;
import static dev.necauqua.mods.subpocket.SubpocketContainer.WIDTH;
import static java.lang.String.format;
import static net.minecraft.ChatFormatting.BOLD;
import static net.minecraft.ChatFormatting.DARK_PURPLE;
import static net.minecraft.client.renderer.LightTexture.FULL_BRIGHT;
import static net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_GRAVE_ACCENT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_TAB;
import static org.lwjgl.opengl.GL11.*;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class SubpocketScreen extends AbstractContainerScreen<SubpocketContainer> {

    private static final Logger LOGGER = LogManager.getLogger();

    public static final ResourceLocation TEXTURE = ns("textures/gui/subpocket.png");

    // white color that you get when nothing was picked
    private static final int NONE = 0xFFFFFF;
    private static final int X_OFF = 35, Y_OFF = 8;
    private static final int ARMOR_OFFSET = 14;

    private static final TextureTarget framebuffer = new TextureTarget(WIDTH, HEIGHT, false, Minecraft.ON_OSX);

    private static final ByteBuffer outputColor = BufferUtils.createByteBuffer(4);
    private static final byte[] underMouseColor = new byte[3];

    private static final Map<String, Throwable> pickingErrors = new LinkedHashMap<>();

    private final MnemonicButton<StackCountCondition> stackCountCondition;
    private final MnemonicButton<StackCountSize> stackCountSize;
    private final MnemonicButton<PickingMode> pickingMode;
    private final ErrorPopup errorPopup;

    private PickingMode effectivePickingMode; // for holding alt

    private boolean isTabDown = false;
    private boolean debug = false;

    private final SubpocketContainer container;
    private final ISubpocket storage;

    private float localX = 0, localY = 0;
    private float scale = 1;
    private float guiFitScale = 1.0F;
    private boolean mouseInside = false;

    private int underMouseIndex = NONE;
    private int draggingIndex = NONE;
    private float draggingOffX = 0, draggingOffY = 0;
    private boolean didDrag = false;

    private Minecraft mc;
    private Font smolFont;

    @SubscribeEvent
    public static void on(RegisterMenuScreensEvent e) {
        e.register(SubpocketContainer.TYPE.get(), SubpocketScreen::new);
    }

    public SubpocketScreen(SubpocketContainer container, Inventory playerInv, Component title) {
        super(container, playerInv, title);
        this.container = container;
        storage = container.getStorage();

        imageWidth = 203;
        imageHeight = 236;
        stackCountCondition = new MnemonicButton<>(() -> isTabDown, Config.stackCountCondition);
        stackCountSize = new MnemonicButton<>(() -> isTabDown, Config.stackCountSize);
        pickingMode = new MnemonicButton<>(() -> isTabDown, Config.pickingMode);
        errorPopup = new ErrorPopup(pickingErrors, this);
    }

    @Override
    public void init() {
        mc = Objects.requireNonNull(super.minecraft);

        smolFont = new Font(((FontAccessor) mc.font).subpocket$getFonts(), false) {
            @Override
            public int drawInBatch(String text, float x, float y, int color, boolean dropShadow, Matrix4f transform, MultiBufferSource bufferSource, DisplayMode displayMode, int effectColor, int packedLightCoords) {
                var adjusted = transform.scale(0.5F, 0.5F, 1, new Matrix4f());
                return super.drawInBatch(text,
                    x * 2 + font.width(text),
                    y * 2 + font.lineHeight / 2.0F,
                    color, dropShadow, adjusted, bufferSource, displayMode, effectColor, packedLightCoords);
            }
        };

        super.init();

        guiFitScale = Math.min(1.0F, Math.min((float) this.height / (float) imageHeight, (float) this.width / (float) (imageWidth + ARMOR_OFFSET)));
        if (guiFitScale < 1.0F) {
            topPos = Math.max(0, (int) ((this.height / guiFitScale - imageHeight) / 2));
            leftPos = Math.max(0, (int) ((this.width / guiFitScale - imageWidth) / 2) - ARMOR_OFFSET);
        } else {
            leftPos -= ARMOR_OFFSET;
            if (leftPos < 0) {
                leftPos = 0;
            }
            if (topPos < 0) {
                topPos = 0;
            }
        }

        var effectiveScale = (float) mc.getWindow().getGuiScale() * guiFitScale;
        if (effectiveScale != scale) {
            scale = effectiveScale;
            framebuffer.resize((int) (WIDTH * scale), (int) (HEIGHT * scale), Minecraft.ON_OSX);
        }

        addRenderableWidget(stackCountCondition.withPos(leftPos + 9, topPos + 129));
        addRenderableWidget(stackCountSize.withPos(leftPos + 9, topPos + 139));
        addRenderableWidget(pickingMode.withPos(leftPos + 9, topPos + 149));
        addRenderableWidget(errorPopup.withPos(leftPos + 195, topPos + 3));
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTicks, int x, int y) {}

    // prevent it from drawing the titles
    @Override
    protected void renderLabels(GuiGraphics graphics, int x, int y) {}

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (guiFitScale < 1.0F) {
            graphics.pose().pushPose();
            graphics.pose().scale(guiFitScale, guiFitScale, 1.0F);
            mouseX = (int) (mouseX / guiFitScale);
            mouseY = (int) (mouseY / guiFitScale);
        }

        try {
            renderBackground(graphics, mouseX, mouseY, partialTicks);

            // get global (not downscaled) mouse pos and 'downscale' it to floats, keeping precision
            localX = (float) (mc.mouseHandler.xpos() / scale - leftPos - X_OFF);
            localY = (float) (mc.mouseHandler.ypos() / scale - topPos - Y_OFF);

            // exclude edges here because they behave weirdly sometimes
            mouseInside = localX > 0 && localX < WIDTH && localY > 0 && localY < HEIGHT;
            effectivePickingMode = hasAltDown() ? PickingMode.ALTERNATIVE : pickingMode.get();

            graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
            if (isTabDown) {
                graphics.blit(TEXTURE, leftPos, topPos + 126, 215, 30, 27, 40);
            }

            mc.getProfiler().popPush("subpocket");

            underMouseIndex = mouseInside ?
                draggingIndex == NONE ?
                    effectivePickingMode.isAlt() ?
                        altPick(storage.getStacksView(), localX, localY) :
                        pixelPick() :
                    draggingIndex :
                NONE;

            graphics.enableScissor(
                leftPos + X_OFF,
                topPos + Y_OFF,
                leftPos + X_OFF + WIDTH,
                topPos + Y_OFF + HEIGHT
            );

            if (debug) {
                if (underMouseIndex != NONE && underMouseIndex != draggingIndex) {
                    drawStack(graphics, storage.get(underMouseIndex), true, true);
                }
            } else {
                var stacksView = storage.getStacksView();
                for (var i = 0; i < stacksView.size(); ++i) {
                    if (i != draggingIndex) {
                        drawStack(graphics, stacksView.get(i), i == underMouseIndex, true);
                    }
                }
            }

            mc.getProfiler().popPush("gameRenderer");

            if (draggingIndex != NONE) {
                var pose = graphics.pose();
                pose.pushPose();
                pose.translate(
                    Mth.clamp(localX + draggingOffX, draggingOffX, WIDTH + draggingOffX),
                    Mth.clamp(localY + draggingOffY, draggingOffY, HEIGHT + draggingOffY),
                    0
                );
                drawStack(graphics, storage.get(draggingIndex), true, false);
                pose.popPose();
            }

            graphics.disableScissor();

            super.render(graphics, mouseX, mouseY, partialTicks);

            var underMouse = storage.get(underMouseIndex);

            if (debug && mouseInside) {
                List<Component> lines = new ArrayList<>();

                lines.add(Component.literal("debug:").withStyle(DARK_PURPLE, BOLD));
                lines.add(Component.literal(format("scale factor: %.2f", scale)));
                lines.add(Component.literal(format("local mouse coords: [%.2f, %.2f]", localX, localY)));

                if (effectivePickingMode == PickingMode.PIXEL) {
                    lines.add(Component.literal(format("color under mouse: [%d, %d, %d]",
                        underMouseColor[0] & 0xff,
                        underMouseColor[1] & 0xff,
                        underMouseColor[2] & 0xff)));
                } else {
                    lines.add(Component.literal(format("expected color: [%d, %d, %d]",
                        underMouseIndex >> 16 & 0xff,
                        underMouseIndex >> 8 & 0xff,
                        underMouseIndex & 0xff)));
                }

                if (underMouseIndex != NONE) {
                    lines.add(Component.literal(format("computed index: %d", underMouseIndex)));
                    lines.add(Component.literal(format("hovered stack pos: %.2f, %.2f", underMouse.getX(), underMouse.getY())));
                }

                graphics.renderComponentTooltip(font, lines, leftPos + 25, topPos + 168);
            }

            renderTooltip(graphics, mouseX, mouseY);

            if (underMouseIndex == NONE || !container.getCarried().isEmpty()) {
                return;
            }

            var ref = underMouse.getRef();
            var tooltip = getTooltipFromItem(this.minecraft, ref);

            if (underMouse.getCount().compareTo(BigInteger.ONE) > 0) {
                tooltip.add(1, Component.translatable("gui.subpocket:it.quantity",
                    hasShiftDown() ? underMouse.getCount().toString() :
                        underMouse.getShortNumberString(Config.overflowType.get())));
            }
            var customFont = IClientItemExtensions.of(ref).getFont(ref, FontContext.TOOLTIP);
            graphics.renderTooltip(customFont != null ? customFont : this.font, tooltip, ref.getTooltipImage(), mouseX + 12, mouseY);
        } finally {
            if (guiFitScale < 1.0F) {
                graphics.pose().popPose();
            }
        }
    }

    private void drawStack(GuiGraphics graphics, ISubpocketStack stack, boolean hovered, boolean translate) {
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(
            leftPos + X_OFF + (translate ? stack.getX() : 0),
            topPos + Y_OFF + (translate ? stack.getY() : 0),
            0
        );

        var ref = stack.getRef();
        graphics.renderItem(mc.player, ref, 0, 0, 0);
        if (hovered && effectivePickingMode.isAlt()) {
            RenderSystem.enableColorLogicOp();
            RenderSystem.logicOp(XOR);
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            graphics.blit(TEXTURE, -1, -1, 203, 0, 18, 18);
            RenderSystem.disableColorLogicOp();
        }
        if (stackCountCondition.get().applies(hovered)) {
            var s = stack.getShortNumberString(Config.overflowType.get());
            var f = stackCountSize.get().applies(s.length() <= 2) ? font : smolFont;
            graphics.renderItemDecorations(f, ref, 0, 0, s);
        }

        pose.popPose();
        RenderSystem.clear(GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
        if (guiFitScale < 1.0F) {
            mouseX /= guiFitScale;
            mouseY /= guiFitScale;
        }
        if (!mouseInside) {
            super.mouseClicked(mouseX, mouseY, mouseButton);
            return false;
        }
        if (draggingIndex != NONE || underMouseIndex == NONE || !container.getCarried().isEmpty()) {
            return false;
        }
        var underMouse = storage.get(underMouseIndex);
        draggingIndex = underMouseIndex;
        draggingOffX = underMouse.getX() - localX;
        draggingOffY = underMouse.getY() - localY;
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int mouseButton, double dragX, double dragY) {
        if (guiFitScale < 1.0F) {
            mouseX /= guiFitScale;
            mouseY /= guiFitScale;
            dragX /= guiFitScale;
            dragY /= guiFitScale;
        }
        if (!mouseInside) {
            return super.mouseDragged(mouseX, mouseY, mouseButton, dragX, dragY);
        }
        if (draggingIndex == NONE) {
            return false;
        }
        didDrag = true;
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int mouseButton) {
        if (guiFitScale < 1.0F) {
            mouseX /= guiFitScale;
            mouseY /= guiFitScale;
        }
        if (didDrag) {
            var x = Mth.clamp(localX + draggingOffX, draggingOffX, WIDTH + draggingOffX);
            var y = Mth.clamp(localY + draggingOffY, draggingOffY, HEIGHT + draggingOffY);
            container.stackMoved(x, y, draggingIndex);
            Network.sendClickToServer(x, y, draggingIndex, null);
            didDrag = false;
        } else if (mouseInside) {
            var click = new ClickState(
                mouseButton == 0 ? 1 : mouseButton == 1 ? 3 : mouseButton,
                hasShiftDown(), hasControlDown(), hasAltDown()
            );
            var clickLocalX = (float) (mouseX - leftPos - X_OFF);
            var clickLocalY = (float) (mouseY - topPos - Y_OFF);
            container.processClick(clickLocalX, clickLocalY, click, underMouseIndex);
            Network.sendClickToServer(clickLocalX, clickLocalY, underMouseIndex, click);
        } else {
            return super.mouseReleased(mouseX, mouseY, mouseButton);
        }
        draggingIndex = NONE;
        underMouseIndex = NONE;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (guiFitScale < 1.0F) {
            mouseX /= guiFitScale;
            mouseY /= guiFitScale;
        }
        if (scrollY == 0.0 || !mouseInside) {
            return false;
        }
        var click = new ClickState(
            scrollY > 0.0 ? 4 : 5,
            hasShiftDown(), hasControlDown(), hasAltDown()
        );

        var underMouse = storage.get(underMouseIndex);

        container.processClick(localX, localY, click, underMouseIndex);
        Network.sendClickToServer(localX, localY, underMouseIndex, click);

        if (!underMouse.isEmpty()) {
            underMouseIndex = storage.getStacksView().indexOf(underMouse);
        }
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        switch (key) {
            case GLFW_KEY_TAB -> isTabDown = true;
            case GLFW_KEY_GRAVE_ACCENT -> debug = true;
            default -> {
                errorPopup.keyPressed(key, scanCode, modifiers);
                return super.keyPressed(key, scanCode, modifiers);
            }
        }
        return true;
    }

    @Override
    public boolean keyReleased(int key, int scanCode, int modifiers) {
        switch (key) {
            case GLFW_KEY_TAB -> isTabDown = false;
            case GLFW_KEY_GRAVE_ACCENT -> debug = false;
            default -> {
                errorPopup.keyReleased(key, scanCode, modifiers);
                return super.keyReleased(key, scanCode, modifiers);
            }
        }
        return true;
    }

    private static int altPick(List<ISubpocketStack> stacks, float mouseX, float mouseY) {
        for (var i = stacks.size() - 1; i >= 0; i--) {
            var stack = stacks.get(i);
            if (mouseX >= stack.getX() && mouseX <= stack.getX() + 16
                && mouseY >= stack.getY() && mouseY <= stack.getY() + 16) {
                return i;
            }
        }
        return NONE;
    }

    private int pixelPick() {
        framebuffer.bindWrite(true);

        RenderSystem.clearColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.clear(GL_COLOR_BUFFER_BIT, Minecraft.ON_OSX);

        var originalProjection = RenderSystem.getProjectionMatrix();
        var originalSorting = RenderSystem.getVertexSorting();
        RenderSystem.setProjectionMatrix(new Matrix4f().ortho(0.0F, framebuffer.width, framebuffer.height, 0.0F, 1000.0F, 3000.0F), VertexSorting.ORTHOGRAPHIC_Z);

        mc.getProfiler().popPush("subpocket.silhouettes");

        var stacksView = storage.getStacksView();
        for (var i = 0; i < stacksView.size(); ++i) {
            RenderSystem.setShaderColor((i >> 16 & 0xff) / 255.0F, (i >> 8 & 0xff) / 255.0F, (i & 0xff) / 255.0F, 1.0F);

            var stack = stacksView.get(i);

            var modelview = RenderSystem.getModelViewStack();
            modelview.pushMatrix();
            modelview.scale(scale, scale, 1.0F);
            modelview.translate(stack.getX() + 8.0F, stack.getY() + 8.0F, 0.0F);
            modelview.scale(16.0F, -16.0F, 16.0F);
            RenderSystem.applyModelViewMatrix();

            var ref = stack.getRef();
            var itemRenderer = mc.getItemRenderer();
            var model = itemRenderer.getModel(ref, null, mc.player, 0);
            var poseStack = new PoseStack();
            poseStack.pushPose();

            var bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
            var wrapper = (MultiBufferSource) renderType -> bufferSource.getBuffer(SilhouetteRenderType.get(renderType));

            try {
                itemRenderer.render(ref, ItemDisplayContext.GUI, false, poseStack, wrapper, FULL_BRIGHT, NO_OVERLAY, model);
            } catch (Throwable throwable) {
                var id = String.valueOf(BuiltInRegistries.ITEM.getKey(ref.getItem()));
                id += ref.getComponentsPatch().toString();
                if (pickingErrors.put(id, throwable) == null) {
                    LOGGER.error("Failed to render the silhouette of {}", id, throwable);
                }
            }

            bufferSource.endBatch();

            modelview.popMatrix();
            RenderSystem.applyModelViewMatrix();

            RenderSystem.clear(GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        }

        mc.getProfiler().popPush("subpocket");

        RenderSystem.readPixels(
            (int) (localX * scale), (int) ((HEIGHT - localY) * scale),
            1, 1, GL_RGBA, GL_UNSIGNED_BYTE,
            outputColor
        );

        mc.getMainRenderTarget().bindWrite(true);

        RenderSystem.setProjectionMatrix(originalProjection, originalSorting);

        outputColor.get(underMouseColor).rewind();
        return (underMouseColor[0] & 0xff) << 16
            | (underMouseColor[1] & 0xff) << 8
            | underMouseColor[2] & 0xff;
    }

    @SubscribeEvent
    public static void on(RegisterShadersEvent e) throws IOException {
        e.registerShader(
            new ShaderInstance(e.getResourceProvider(), Subpocket.ns("item_silhouette").toString(), DefaultVertexFormat.BLOCK),
            shader -> SilhouetteRenderType.silhouette = shader
        );
    }

    @SubscribeEvent
    public static void on(RegisterClientReloadListenersEvent e) {
        e.registerReloadListener((preparationBarrier, b, c, d, f, g) ->
            preparationBarrier.wait(true)
                .thenAccept(v -> {
                    LogManager.getLogger(Subpocket.class).info("Clearing cached silhouette render types");
                    SilhouetteRenderType.types.clear();
                }));
    }

    public static final class SilhouetteRenderType extends RenderType {

        private static final Map<RenderType, RenderType> types = new HashMap<>();

        private static ShaderInstance silhouette;

        private static final RenderStateShard.ShaderStateShard SILHOUETTE_SHADER = new RenderStateShard.ShaderStateShard(() -> silhouette);

        public static RenderType get(RenderType renderType) {
            return types.computeIfAbsent(renderType, $ -> create("subpocket_item_silhouette",
                DefaultVertexFormat.BLOCK,
                QUADS,
                SMALL_BUFFER_SIZE,
                true,
                false,
                CompositeState.builder()
                    .setShaderState(SILHOUETTE_SHADER)
                    .setTextureState(renderType instanceof CompositeRenderTypeAccessor composite ?
                        ((CompositeStateAccessor) (Object) composite.subpocket$getState()).subpocket$getTextureState() :
                        RenderType.NO_TEXTURE)
                    .createCompositeState(false)));
        }

        private SilhouetteRenderType(String name, VertexFormat format, Mode drawMode, int bufferSize, boolean useDelegate, boolean needsSorting, Runnable setupTask, Runnable clearTask) {
            super(name, format, drawMode, bufferSize, useDelegate, needsSorting, setupTask, clearTask);
        }
    }
}
