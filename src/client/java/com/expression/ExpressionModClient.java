package com.expression;

import com.expression.config.ModConfig;
import com.expression.network.ClientNetworkHandler;
import com.expression.skin.FaceRuntime;
import com.expression.skin.FaceSkinData;
import com.expression.state.EmoteType;
import com.expression.state.ExpressionStateManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.UUID;

public class ExpressionModClient implements ClientModInitializer {
    private static final EmoteType[] EMOTES = { EmoteType.SMILE, EmoteType.ANGRY, EmoteType.SAD,
        EmoteType.SURPRISED, EmoteType.THINK, EmoteType.SHY, EmoteType.NEUTRAL };
    private final KeyBinding[] keys = new KeyBinding[EMOTES.length];
    private boolean menuChord;
    @Override public void onInitializeClient() {
        ClientNetworkHandler.registerClientReceivers();
        var category = KeyBinding.Category.create(Identifier.of("expressionmod", "emotes"));
        for (int i = 0; i < keys.length; i++)
            keys[i] = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.expressionmod." + EMOTES[i].getId(),
                    InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_KP_1 + i, category));
        var reload = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.expressionmod.reload_cache",
                InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_KP_0, category));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;
            long window=client.getWindow().getHandle();
            boolean chord=(GLFW.glfwGetKey(window,GLFW.GLFW_KEY_LEFT_CONTROL)==GLFW.GLFW_PRESS
                    || GLFW.glfwGetKey(window,GLFW.GLFW_KEY_RIGHT_CONTROL)==GLFW.GLFW_PRESS)
                    && GLFW.glfwGetMouseButton(window,GLFW.GLFW_MOUSE_BUTTON_RIGHT)==GLFW.GLFW_PRESS;
            if(chord && !menuChord && client.currentScreen==null) {
                var texture=client.player.getSkin().body().texturePath();
                for(int slot=0;slot<5;slot++) if(com.expression.skin.LegacySkinTextures.hasEmote(texture,slot)) {
                    client.setScreen(new com.expression.render.LegacyEmoteScreen()); break;
                }
            }
            menuChord=chord;
            for (int i = 0; i < keys.length; i++) while (keys[i].wasPressed()) {
                var emote = EMOTES[i];
                long duration = ModConfig.getInstance().defaultEmoteDurationMs;
                ExpressionStateManager.getInstance().getOrCreate(client.player.getUuid(), client.player.getName().getString())
                        .setEmote(emote, duration);
                ClientNetworkHandler.sendEmoteChange(emote, duration);
                client.player.sendMessage(Text.literal("[ExpressionMod] " + emote.getDisplayName()), true);
            }
            while (reload.wasPressed()) {
                FaceRuntime.clear();
                com.expression.skin.LegacySkinTextures.clearFrames();
                FaceSkinData.invalidateResources();
                ExpressionStateManager.getInstance().clear();
                client.player.sendMessage(Text.literal("[ExpressionMod] アニメーションをリセットしました"), true);
            }
            // Tick remote expression leases too; discard gaze caches for players outside the loaded world.
            var players = new HashSet<UUID>();
            for (var player : client.world.getPlayers()) players.add(player.getUuid());
            ExpressionStateManager.getInstance().tickAll();
            FaceRuntime.retain(players);
            com.expression.skin.LegacySkinTextures.retain(players);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
            ExpressionStateManager.getInstance().clear();
            FaceRuntime.clear();
            com.expression.skin.LegacySkinTextures.clearFrames();
        }));
        ExpressionMod.LOGGER.info("Continuous face renderer ready (Minecraft 1.21.11). Numpad 1-6: emotes, 7: neutral, 0: reset.");
    }
}
