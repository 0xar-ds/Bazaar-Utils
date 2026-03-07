package com.github.mkram17.bazaarutils.misc;

import com.github.mkram17.bazaarutils.events.ChestLoadedEvent;
import com.github.mkram17.bazaarutils.events.listener.BUListener;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreens;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenManager;
import com.github.mkram17.bazaarutils.features.gui.inventory.InstantSellHighlight;
import com.github.mkram17.bazaarutils.features.gui.inventory.OrderStatusHighlight;
import meteordevelopment.orbit.EventHandler;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Module
public class SlotHighlightCache extends BUListener {

    // key: slotIndex, value: highlightColor
    public static final Map<Integer, Integer> orderStatusHighlightCache = new ConcurrentHashMap<>();
    public static final Map<Integer, Integer> instaSellHighlightCache = new ConcurrentHashMap<>();

    public SlotHighlightCache() {
        super();
    }

    public void registerFabricEvents(){
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            orderStatusHighlightCache.clear();
            instaSellHighlightCache.clear();
        });
    }

    @EventHandler
    public static void updateCaches(ChestLoadedEvent event) {
        if (!ScreenManager.getInstance().isCurrent(BazaarScreens.ORDERS_PAGE, BazaarScreens.MAIN_PAGE)) {
            return;
        }

        OrderStatusHighlight.updateHighlightCache(event.getItemStacks());
        InstantSellHighlight.updateHighlightCache();
    }
}
