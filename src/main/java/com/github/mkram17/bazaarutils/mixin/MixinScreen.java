package com.github.mkram17.bazaarutils.mixin;

import com.github.mkram17.bazaarutils.features.gui.overlays.ItemPriceSourcesOverlay;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Screen.class)
public class MixinScreen {

    @Inject(method = "keyPressed", at = @At("HEAD"))
    private void onKeyPressed(KeyInput input, CallbackInfoReturnable<Boolean> cir) {
        ItemPriceSourcesOverlay.onKeyPressed(input.key());
    }
}