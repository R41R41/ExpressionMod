package com.expression;

import com.expression.network.NetworkHandler;
import com.expression.network.ServerNetworkHandler;
import com.expression.network.FullStateSyncPayload;
import com.expression.network.DialogueSyncPayload;
import com.expression.network.BridgeReadyPayload;
import com.expression.state.ExpressionStateManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.S2CPlayChannelEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExpressionMod implements ModInitializer {
    public static final String MOD_ID = "expressionmod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Expression Mod initializing...");

        // ネットワークパケット登録
        NetworkHandler.registerPackets();

        // サーバー側ハンドラー登録
        ServerNetworkHandler.registerServerReceivers();

        // サーバーtickイベント
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            ExpressionStateManager.server().tickAll();
        });

        // プレイヤー参加時に全状態を送信
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            server.execute(() -> {
                ServerNetworkHandler.sendAllStatesToPlayer(handler.getPlayer());
                ServerNetworkHandler.sendBridgeReady(handler.getPlayer());
            });
        });

        // A vanilla-compatible bot/client may advertise its channels only after JOIN.
        S2CPlayChannelEvents.REGISTER.register((handler, sender, server, channels) -> {
            if (channels.contains(FullStateSyncPayload.ID.id()))
                server.execute(() -> ServerNetworkHandler.sendAllStatesToPlayer(handler.getPlayer()));
            if (channels.contains(DialogueSyncPayload.ID.id()))
                server.execute(() -> ServerNetworkHandler.sendDialogueStatesToPlayer(handler.getPlayer()));
            if (channels.contains(BridgeReadyPayload.ID.id()))
                server.execute(() -> ServerNetworkHandler.sendBridgeReady(handler.getPlayer()));
        });

        // プレイヤー退出時に状態をクリア
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ExpressionStateManager.server().remove(handler.getPlayer().getUuid());
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> ExpressionStateManager.server().clear());

        LOGGER.info("Expression Mod initialized!");
    }
}
