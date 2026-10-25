package com.expression.test.mixin;

import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.PlayerSkinTextureDownloader;
import net.minecraft.util.AssetInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import java.util.concurrent.CompletableFuture;

@Mixin(PlayerSkinTextureDownloader.class)
public interface SkinDownloadInvoker {
    @Invoker("registerTexture")
    CompletableFuture<AssetInfo.TextureAsset> expressionmod$register(AssetInfo.TextureAsset asset, NativeImage image);
}
