package com.github.mkram17.bazaarutils.events.handler;

import com.github.mkram17.bazaarutils.events.ChestLoadedEvent;
import com.github.mkram17.bazaarutils.events.listener.BUListener;
import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.github.mkram17.bazaarutils.utils.bazaar.components.OrdersScreenParser;
import com.github.mkram17.bazaarutils.utils.bazaar.components.SummaryScreenParser;
import com.github.mkram17.bazaarutils.utils.bazaar.components.ManageOrdersParser;
import com.github.mkram17.bazaarutils.utils.bazaar.data.BazaarProductRegistry;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenHandler;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreens;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PriceLevelPool;
import com.github.mkram17.bazaarutils.utils.minecraft.ItemInfo;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenContext;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenManager;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.ItemStack;

import java.util.List;
import java.util.Optional;

@Module()
public class BazaarScreensHandler extends BUListener {

    public BazaarScreensHandler() {
        super();
    }

    @EventHandler
    private void onChestLoaded(ChestLoadedEvent event) {
        ScreenManager.getInstance().current().ifPresent(context -> {
            if (context.isAnyOf(BazaarScreens.ITEM_PAGE)) {
                handleItemPage(context);
            } else if (context.isAnyOf(BazaarScreens.BUY_ORDER_PRICE, BazaarScreens.SELL_ORDER_PRICE)) {
                handlePricePage(context);
            } else if (context.isAnyOf(BazaarScreens.ORDERS_PAGE)) {
                handleOrdersPage(context);
            } else if (context.isAnyOf(BazaarScreens.ITEMS_GROUP_PAGE)) {
                handleItemsGroupPage(context);
            }
        });
    }

    private static void handleItemPage(ScreenContext context) {
        Optional<String> productId = BazaarScreenHandler.getDisplayProductId(context);
        if (productId.isEmpty()) return;

        Optional<ItemStack> buyStack  = BazaarScreenHandler.getCreateBuyOrderItem(context).map(ItemInfo::itemStack);
        Optional<ItemStack> sellStack = BazaarScreenHandler.getCreateSellOfferItem(context).map(ItemInfo::itemStack);
        if (buyStack.isEmpty() || sellStack.isEmpty()) return;

        SummaryScreenParser.SummaryResult result = SummaryScreenParser.parseItemPage(buyStack.get(), sellStack.get());
        if (!result.buyLevels().isEmpty() || !result.sellLevels().isEmpty()) {
            BazaarProductRegistry.notifyBookScreen(productId.get(), result.sellLevels(), result.buyLevels());
        }

        BazaarScreenHandler.getManageOrdersItem(context).map(ItemInfo::itemStack).ifPresent(book -> {
            var parsed = ManageOrdersParser.parse(book, productId.get());
            BazaarProductRegistry.notifyManageOrdersItemPage(productId.get(), parsed.hints(), parsed.observedAt());
        });
    }

    private static void handleItemsGroupPage(ScreenContext context) {
        BazaarScreenHandler.getManageOrdersItem(context).map(ItemInfo::itemStack).ifPresent(book -> {
            var parsed = ManageOrdersParser.parse(book, null);

            if (!parsed.hints().isEmpty()) {
                BazaarProductRegistry.notifyManageOrdersGroupPage(parsed.hints(), parsed.observedAt());
            }
        });
    }

    private static void handlePricePage(ScreenContext context) {
        Optional<String> productId = ScreenManager.getInstance()
                .findBack(BazaarScreens.ITEM_PAGE)
                .flatMap(BazaarScreenHandler::getDisplayProductId);

        PlayerActionUtil.notifyAll("[BazaarScreensHandler] PRICE_PAGE productId=" + productId, NotificationType.BAZAARDATA);
        if (productId.isEmpty()) return;

        Optional<ItemStack> signStack = BazaarScreenHandler.getCustomPriceItem(context).map(ItemInfo::itemStack);
        PlayerActionUtil.notifyAll("[BazaarScreensHandler] PRICE_PAGE signStack=" + signStack.isPresent(), NotificationType.BAZAARDATA);
        if (signStack.isEmpty()) return;

        List<PriceLevelPool> levels = SummaryScreenParser.parsePriceLevels(signStack.get());
        PlayerActionUtil.notifyAll("[BazaarScreensHandler] PRICE_PAGE parsed levels=" + levels.size(), NotificationType.BAZAARDATA);
        if (levels.isEmpty()) return;

        boolean isBuy = context.isAnyOf(BazaarScreens.BUY_ORDER_PRICE);

        BazaarProductRegistry.notifyBookScreen(
                productId.get(),
                isBuy ? List.of() : levels,
                isBuy ? levels : List.of());
    }

    private static void handleOrdersPage(ScreenContext ctx) {
        if (!(ctx.screen() instanceof GenericContainerScreen containerScreen)) return;

        var parsed = OrdersScreenParser.parse(containerScreen);

        parsed.orders().forEach((productId, entries) ->
                PlayerActionUtil.notifyAll(
                        "[BazaarScreensHandler] ORDERS_PAGE: " + productId + " → " + entries.size() + " order(s)",
                        NotificationType.BAZAARDATA));

        BazaarProductRegistry.reconcileOrdersScreen(parsed.orders(), parsed.observedAt());
    }
}