package com.ametra;

import org.lwjgl.glfw.GLFW;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.util.ActionResult;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.entity.EntityType;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

public class AmetraClient implements ClientModInitializer {
    /** Tecla del menú (Shift derecho por defecto; se cambia en Controles). */
    public static KeyBinding MENU_KEY;

    @Override
    public void onInitializeClient() {
        AmetraConfig.load();
        com.ametra.net.Probe.init();

        MENU_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.ametra.menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, "category.ametra"));

        Features.ZOOM_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.ametra.zoom", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_C, "category.ametra"));

        Features.FREELOOK_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.ametra.freelook", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, "category.ametra"));

        for (int i = 0; i < Features.TEXT_KEYS.length; i++) {
            Features.TEXT_KEYS[i] = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.ametra.autotext" + (i + 1), InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "category.ametra"));
        }
        // Auto GG: solo mensajes del sistema (el final de partida), no el chat de otros jugadores
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) Features.onGameMessage(message.getString());
        });

        // Cosméticos del launcher (capa, alas, sombrero, gafas, mochila) sobre el jugador local.
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, registrationHelper, context) -> {
            if (entityType == EntityType.PLAYER && entityRenderer instanceof PlayerEntityRenderer pr) {
                registrationHelper.register(new CosmeticsFeature(pr));
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            Custom.init(client);
            Features.tick(client);
            com.ametra.net.Probe.tick(client);
            if (client.player != null && client.player.age % 40 == 0) AmetraConfig.pollCosmetics();
            while (MENU_KEY.wasPressed()) {
                if (client.currentScreen == null && client.player != null) {
                    client.setScreen(new AmetraScreen(null));
                }
            }
        });

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient && entity.isAttackable()) Features.onAttack(entity);
            return ActionResult.PASS;
        });

        Hitboxes.register();
        Compat.registerHud();
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> Welcome.start());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> com.ametra.net.Probe.reset());
        Fx.register();
    }
}
