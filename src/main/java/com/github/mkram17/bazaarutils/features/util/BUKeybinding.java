package com.github.mkram17.bazaarutils.features.util;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;

public abstract class BUKeybinding {
    public final KeyBinding keyBinding;

    public BUKeybinding(KeyBinding keyBinding) {
        this.keyBinding = keyBinding;
        registerKeybinding(keyBinding);
        registerOnPressed();
    }

    private static void registerKeybinding(KeyBinding keyBinding){
        KeyBindingHelper.registerKeyBinding(keyBinding);
    }

    protected void registerOnPressed(){}

    public String getUsage(){
        return keyBinding.getBoundKeyTranslationKey();
    }
}
