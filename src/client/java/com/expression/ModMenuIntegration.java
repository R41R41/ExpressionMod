package com.expression;

import com.expression.config.ModConfig;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * ModMenu統合 - 設定画面を提供
 */
public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return this::createConfigScreen;
    }

    private Screen createConfigScreen(Screen parent) {
        ModConfig config = ModConfig.getInstance();

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Text.translatable("config.expressionmod.title"))
                .setSavingRunnable(config::save);

        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        // 一般設定カテゴリ
        ConfigCategory general = builder.getOrCreateCategory(
                Text.translatable("config.expressionmod.category.general"));

        general.addEntry(entryBuilder
                .startBooleanToggle(
                        Text.translatable("config.expressionmod.enable_expressions"),
                        config.enableExpressions)
                .setDefaultValue(true)
                .setTooltip(Text.translatable("config.expressionmod.enable_expressions.tooltip"))
                .setSaveConsumer(value -> config.enableExpressions = value)
                .build());

        general.addEntry(entryBuilder
                .startBooleanToggle(
                        Text.translatable("config.expressionmod.enable_eye_movement"),
                        config.enableEyeMovement)
                .setDefaultValue(true)
                .setTooltip(Text.translatable("config.expressionmod.enable_eye_movement.tooltip"))
                .setSaveConsumer(value -> config.enableEyeMovement = value)
                .build());

        general.addEntry(entryBuilder
                .startBooleanToggle(
                        Text.translatable("config.expressionmod.enable_head_movement"),
                        config.enableHeadMovement)
                .setDefaultValue(true)
                .setTooltip(Text.translatable("config.expressionmod.enable_head_movement.tooltip"))
                .setSaveConsumer(value -> config.enableHeadMovement = value)
                .build());

        general.addEntry(entryBuilder
                .startBooleanToggle(
                        Text.translatable("config.expressionmod.enable_blinking"),
                        config.enableBlinking)
                .setDefaultValue(true)
                .setTooltip(Text.translatable("config.expressionmod.enable_blinking.tooltip"))
                .setSaveConsumer(value -> config.enableBlinking = value)
                .build());

        // 詳細設定カテゴリ
        ConfigCategory advanced = builder.getOrCreateCategory(
                Text.translatable("config.expressionmod.category.advanced"));

        advanced.addEntry(entryBuilder
                .startBooleanToggle(
                        Text.translatable("config.expressionmod.enable_micro_saccades"),
                        config.enableMicroSaccades)
                .setDefaultValue(true)
                .setTooltip(Text.translatable("config.expressionmod.enable_micro_saccades.tooltip"))
                .setSaveConsumer(value -> config.enableMicroSaccades = value)
                .build());

        advanced.addEntry(entryBuilder
                .startBooleanToggle(
                        Text.translatable("config.expressionmod.enable_gaze_aversion"),
                        config.enableGazeAversion)
                .setDefaultValue(true)
                .setTooltip(Text.translatable("config.expressionmod.enable_gaze_aversion.tooltip"))
                .setSaveConsumer(value -> config.enableGazeAversion = value)
                .build());

        advanced.addEntry(entryBuilder
                .startLongField(
                        Text.translatable("config.expressionmod.default_emote_duration"),
                        config.defaultEmoteDurationMs)
                .setDefaultValue(3000L)
                .setMin(500L)
                .setMax(30000L)
                .setTooltip(Text.translatable("config.expressionmod.default_emote_duration.tooltip"))
                .setSaveConsumer(value -> config.defaultEmoteDurationMs = value)
                .build());

        advanced.addEntry(entryBuilder
                .startFloatField(
                        Text.translatable("config.expressionmod.eye_movement_speed"),
                        config.eyeMovementSpeed)
                .setDefaultValue(0.3f)
                .setMin(0.1f)
                .setMax(1.0f)
                .setTooltip(Text.translatable("config.expressionmod.eye_movement_speed.tooltip"))
                .setSaveConsumer(value -> config.eyeMovementSpeed = value)
                .build());

        advanced.addEntry(entryBuilder
                .startFloatField(
                        Text.translatable("config.expressionmod.head_movement_speed"),
                        config.headMovementSpeed)
                .setDefaultValue(0.08f)
                .setMin(0.01f)
                .setMax(0.2f)
                .setTooltip(Text.translatable("config.expressionmod.head_movement_speed.tooltip"))
                .setSaveConsumer(value -> config.headMovementSpeed = value)
                .build());

        // デバッグカテゴリ
        ConfigCategory debug = builder.getOrCreateCategory(
                Text.translatable("config.expressionmod.category.debug"));

        debug.addEntry(entryBuilder
                .startBooleanToggle(
                        Text.translatable("config.expressionmod.debug_mode"),
                        config.debugMode)
                .setDefaultValue(false)
                .setTooltip(Text.translatable("config.expressionmod.debug_mode.tooltip"))
                .setSaveConsumer(value -> config.debugMode = value)
                .build());

        return builder.build();
    }
}
