package com.github.mkram17.bazaarutils.mixin;

import com.github.mkram17.bazaarutils.features.gui.overlays.ItemPriceSourcesOverlay;
import net.minecraft.client.Keyboard;
import net.minecraft.client.input.CharInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public class MixinKeyboard {

    @Inject(method = "onChar", at = @At("HEAD"))
    private void onChar(long window, CharInput input, CallbackInfo ci) {
        char[] chars = Character.toChars(input.codepoint());
        if (chars.length == 1) {
            ItemPriceSourcesOverlay.onCharTyped(chars[0]);
        }
    }
}