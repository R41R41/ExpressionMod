package com.expression.config;

import com.expression.ExpressionMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * MOD設定を管理するクラス
 */
public class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("expressionmod.json");

    private static ModConfig instance;

    // 設定項目
    public boolean enableExpressions = true;
    public boolean enableEyeMovement = true;
    public boolean enableHeadMovement = true;
    public boolean enableBlinking = true;
    public boolean enableMicroSaccades = true;
    public boolean enableGazeAversion = true;

    // デフォルトのエモート持続時間（ミリ秒）
    public long defaultEmoteDurationMs = 3000;

    // 視線移動速度（0.1 - 1.0）
    public float eyeMovementSpeed = 0.3f;

    // 頭部移動速度（0.01 - 0.2）
    public float headMovementSpeed = 0.08f;

    // まばたき間隔（ミリ秒）
    public long blinkIntervalMin = 3000;
    public long blinkIntervalMax = 6000;

    // デバッグモード
    public boolean debugMode = false;

    private ModConfig() {
    }

    public static ModConfig getInstance() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    public static ModConfig load() {
        if (Files.exists(CONFIG_PATH)) {
            try {
                String json = Files.readString(CONFIG_PATH);
                ModConfig config = GSON.fromJson(json, ModConfig.class);
                if (config != null) {
                    ExpressionMod.LOGGER.info("Config loaded from " + CONFIG_PATH);
                    return config;
                }
            } catch (IOException e) {
                ExpressionMod.LOGGER.error("Failed to load config", e);
            }
        }

        // デフォルト設定を作成して保存
        ModConfig config = new ModConfig();
        config.save();
        return config;
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(this));
            ExpressionMod.LOGGER.info("Config saved to " + CONFIG_PATH);
        } catch (IOException e) {
            ExpressionMod.LOGGER.error("Failed to save config", e);
        }
    }

    /**
     * 設定をリロード
     */
    public static void reload() {
        instance = load();
    }
}
