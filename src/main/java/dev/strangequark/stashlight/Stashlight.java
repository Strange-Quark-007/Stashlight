package dev.strangequark.stashlight;

import com.mojang.blaze3d.platform.InputConstants;
import dev.strangequark.stashlight.render.HighlightRenderer;
import dev.strangequark.stashlight.repository.ContainerRepository;
import dev.strangequark.stashlight.screen.SearchScreen;
import dev.strangequark.stashlight.serializer.Serializer;
import dev.strangequark.stashlight.util.Util;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.client.player.ClientPlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

public class Stashlight implements ClientModInitializer {
    public static final String MOD_ID = "stashlight";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private Serializer serializer;
    private ContainerRepository repository;
    private static KeyMapping searchKey;
    public static final KeyMapping.Category STASHLIGHT = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "stashlight"));

    private int tickCounter = 0;

    @Nullable
    private BlockPos lastOpened;

    @Override
    public void onInitializeClient() {
        Init.init();
        UseBlockCallback.EVENT.register(this::onBlockUsed);
        ClientPlayerBlockBreakEvents.AFTER.register(this::onBlockBreak);
        ScreenEvents.AFTER_INIT.register(this::onScreenInit);
        WorldRenderEvents.AFTER_ENTITIES.register(HighlightRenderer::render);

        searchKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.stashlight.search_menu",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_KP_5,
                STASHLIGHT
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (searchKey.isDown()) {
                client.setScreen(new SearchScreen(repository));
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level == null || repository == null) return;

            tickCounter++;

            if (tickCounter % 100 == 0) {
                repository.runCleanup(client.level);
            }

            if (tickCounter % 3000 == 0) {
                repository.saveIfDirty();
                tickCounter = 0;
            }
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            serializer = new Serializer(Init.getFileName(), handler.registryAccess());
            repository = new ContainerRepository(serializer);
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            if (repository != null) {
                repository.shutdown();

            }
            serializer = null;
            repository = null;
        });
    }

    private InteractionResult onBlockUsed(Player player, Level level, InteractionHand interactionHand, BlockHitResult blockHitResult) {
        BlockPos pos = blockHitResult.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (Util.isValidSearchableContainer(state)) {
            lastOpened = pos;
        }
        return InteractionResult.PASS;
    }

    private void onBlockBreak(ClientLevel clientLevel, LocalPlayer localPlayer, BlockPos blockPos, BlockState blockState) {
        if (repository == null || !(blockState.getBlock() instanceof EntityBlock)) {
            return;
        }

        // Use the canonical resolution to find the correct database key to delete.
        BlockPos canonicalPos = Util.getCanonicalPos(clientLevel, blockPos);
        String dimension = Util.getDimensionName(clientLevel);
        repository.remove(dimension, canonicalPos);
    }


    private void onScreenInit(Minecraft client, Screen screen, int w, int h) {
        if (client.level == null || screen instanceof CreativeModeInventoryScreen) {
            return;
        }

        if (screen instanceof AbstractContainerScreen<?> handled) {
            var handler = handled.getMenu();
            // Serialize on close to ensure the database reflects the final state of the inventory.
            ScreenEvents.remove(screen).register(closedScreen -> serializeContainer(client, handler));
        }
    }

    private void serializeContainer(Minecraft client, AbstractContainerMenu handler) {
        if (client.level == null || repository == null || lastOpened == null) {
            return;
        }

        String dimension = Util.getDimensionName(client.level);
        Set<BlockPos> pair = Util.resolveContainerPositions(client.level, lastOpened);
        BlockPos canonicalPos = Util.getCanonicalPos(client.level, pair.iterator().next());
        BlockState blockstate = client.level.getBlockState(canonicalPos);


        if (!Util.isValidSearchableContainer(blockstate)) {
            lastOpened = null;
            return;
        }

        var stacks = handler.getItems();
        int containerSize = stacks.size() - 36;
        if (containerSize <= 0) {
            lastOpened = null;
            return;
        }

        // HARD INVALIDATION — nuke positions data
        repository.remove(dimension, canonicalPos);
        for (BlockPos p : pair) {
            repository.remove(dimension, p);
        }

        // Single authoritative write
        repository.update(
                dimension,
                canonicalPos,
                blockstate.getBlock().getName().getString(),
                containerSize,
                stacks.subList(0, containerSize)
        );

        lastOpened = null;
    }
}