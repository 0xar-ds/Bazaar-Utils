package com.github.mkram17.bazaarutils.features.gui.buttons;

import com.github.mkram17.bazaarutils.config.features.gui.ButtonsConfig;
import com.github.mkram17.bazaarutils.events.ChestLoadedEvent;
import com.github.mkram17.bazaarutils.events.ReplaceItemEvent;
import com.github.mkram17.bazaarutils.events.SlotClickEvent;
import com.github.mkram17.bazaarutils.events.listener.BUListener;
import com.github.mkram17.bazaarutils.utils.SoundUtil;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.github.mkram17.bazaarutils.utils.bazaar.InputHelper;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreens;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarSlots;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderType;
import com.github.mkram17.bazaarutils.utils.config.BUToggleableFeature;
import com.github.mkram17.bazaarutils.utils.minecraft.ItemButton;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenManager;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.container.ContainerManager;
import com.github.mkram17.bazaarutils.utils.minecraft.item.ItemRef;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigObject;
import lombok.Getter;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Optional;

@Module
public class ConfirmBuyOrder extends BUListener implements ItemButton, BUToggleableFeature {
    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public int getSlotIndex() {
        return 17;
    }

    @Override
    public ItemRef getItemRef() {
        return ItemRef.of(Items.BLUE_TERRACOTTA);
    }

    public ConfirmBuyOrder() {
        super();
    }

    private boolean inCorrectScreen() {
        return ScreenManager.getInstance().isCurrent(BazaarScreens.BUY_ORDER_CONFIRMATION);
    }

    @EventHandler
    private void onReplaceItemEvent(ReplaceItemEvent event) {
        if (!isEnabled() || !shouldReplaceItem(event) || !inCorrectScreen()) {
            return;
        }

        event.setReplacement(getReplacementItem());
    }

    @EventHandler
    private void onClick(SlotClickEvent event) {
        if (!isEnabled() || !wasButtonClicked(event) || !inCorrectScreen()) {
            return;
        }

        SoundUtil.playSound(BUTTON_SOUND, BUTTON_VOLUME);

        confirmOrderPlacement();
    }

    private void confirmOrderPlacement() {
        ContainerManager.clickSlot(13, 0);
    }
}
