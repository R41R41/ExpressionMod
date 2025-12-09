package com.expression.state;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 全プレイヤーの表情状態を管理するシングルトン
 */
public class ExpressionStateManager {
    private static final ExpressionStateManager INSTANCE = new ExpressionStateManager();

    // プレイヤーUUID -> ExpressionState
    private final Map<UUID, ExpressionState> states = new ConcurrentHashMap<>();

    // プレイヤー名 -> UUID（名前でも検索できるように）
    private final Map<String, UUID> nameToUuid = new ConcurrentHashMap<>();

    private ExpressionStateManager() {
    }

    public static ExpressionStateManager getInstance() {
        return INSTANCE;
    }

    /**
     * プレイヤーの状態を取得（なければ作成）
     */
    public ExpressionState getOrCreate(UUID uuid, String playerName) {
        return states.computeIfAbsent(uuid, id -> {
            nameToUuid.put(playerName.toLowerCase(), id);
            return new ExpressionState(id, playerName);
        });
    }

    /**
     * プレイヤー名から状態を取得
     */
    public ExpressionState getByName(String playerName) {
        UUID uuid = nameToUuid.get(playerName.toLowerCase());
        if (uuid != null) {
            return states.get(uuid);
        }
        return null;
    }

    /**
     * UUIDから状態を取得
     */
    public ExpressionState get(UUID uuid) {
        return states.get(uuid);
    }

    /**
     * プレイヤーの状態を削除
     */
    public void remove(UUID uuid) {
        ExpressionState state = states.remove(uuid);
        if (state != null) {
            nameToUuid.remove(state.getPlayerName().toLowerCase());
        }
    }

    /**
     * 全プレイヤーの状態をtick
     */
    public void tickAll() {
        states.values().forEach(ExpressionState::tick);
    }

    /**
     * 全状態をクリア
     */
    public void clear() {
        states.clear();
        nameToUuid.clear();
    }

    /**
     * 登録されている全状態を取得
     */
    public Map<UUID, ExpressionState> getAllStates() {
        return states;
    }
}
