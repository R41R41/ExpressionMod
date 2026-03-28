package com.expression;

import com.expression.network.NetworkHandler;
import com.expression.network.ServerNetworkHandler;
import com.expression.state.ExpressionStateManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
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
            ExpressionStateManager.getInstance().tickAll();
        });

        // プレイヤー参加時に全状態を送信
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            server.execute(() -> {
                ServerNetworkHandler.sendAllStatesToPlayer(handler.getPlayer());
            });
        });

        // プレイヤー退出時に状態をクリア
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ExpressionStateManager.getInstance().remove(handler.getPlayer().getUUID());
        });

        LOGGER.info("Expression Mod initialized!");
    }
}
