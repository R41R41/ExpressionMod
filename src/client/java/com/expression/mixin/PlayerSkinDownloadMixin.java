package com.expression.mixin;

import com.expression.skin.FaceSkinData;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.PlayerSkinTextureDownloader;
import net.minecraft.util.AssetInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

@Mixin(PlayerSkinTextureDownloader.class)
public class PlayerSkinDownloadMixin {
    @Inject(method = "registerTexture", at = @At("HEAD"))
    private void expressionmod$capture(AssetInfo.TextureAsset asset, NativeImage image,
            CallbackInfoReturnable<CompletableFuture<AssetInfo.TextureAsset>> ci) {
        FaceSkinData.capture(asset.texturePath(), image);
        com.expression.skin.LegacySkinTextures.capture(asset.texturePath(), image);
    }
}
