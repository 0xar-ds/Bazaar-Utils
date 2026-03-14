package com.github.mkram17.bazaarutils.events.handler;

import com.github.mkram17.bazaarutils.events.BazaarChatEventStub;
import com.github.mkram17.bazaarutils.features.gui.overlays.BazaarLimitsVisualizer;
import com.github.mkram17.bazaarutils.utils.annotations.autoregistration.RunOnInit;
import com.github.mkram17.bazaarutils.utils.bazaar.data.BazaarProductRegistry;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderState;
import com.github.mkram17.bazaarutils.utils.Util;
import meteordevelopment.orbit.EventHandler;

import java.util.Comparator;

import static com.github.mkram17.bazaarutils.BazaarUtils.EVENT_BUS;

public class BazaarChatEventHandlerStub {
    @EventHandler
    private static void onOrderCreated(BazaarChatEventStub.OrderCreated event) {
        var order = event.order();
        BazaarLimitsVisualizer.addOrderToLimit((double) order.getVolume() * order.getPricePerUnit());
        order.productId().ifPresent(id -> BazaarProductRegistry.notifyPlacement(id, order.getSide(), order.getPricePerUnit(), order.getVolume()));
    }

    @EventHandler
    private static void onOrderFilled(BazaarChatEventStub.OrderFilled event) {
        var order = event.order();
        order.productId().ifPresent(id -> BazaarProductRegistry.notifyOrderFilled(id, order.getSide(), order.getVolume()));
    }

    @EventHandler
    private static void onOrderClaimed(BazaarChatEventStub.OrderClaimed event) {
        var order = event.order();
        order.productId().ifPresent(id -> BazaarProductRegistry.notifyClaim(id, order.getSide(), order.getPricePerUnit(), order.getVolume()));
    }

    @EventHandler
    private static void onSellOrderCancelled(BazaarChatEventStub.SellOrderCancelled event) {
        event.order().productId().ifPresent(id -> BazaarProductRegistry.notifyCancelSellOffer(id, event.order().getVolume()));
    }

    @EventHandler
    private static void onBuyOrderCancelled(BazaarChatEventStub.BuyOrderCancelled event) {
        // No item name in chat — registry resolves the order by coin amount.
        BazaarProductRegistry.notifyCancelBuyOrder(event.coinsRefunded());
    }

    @EventHandler
    private static void onInstantSell(BazaarChatEventStub.InstantSell event) {
        var order = event.order();
        BazaarLimitsVisualizer.addOrderToLimit((double) order.getVolume() * order.getPricePerUnit());
        order.productId().ifPresent(id -> BazaarProductRegistry.notifyInstantDeal(id, order.getSide(), order.getPricePerUnit(), order.getVolume()));
    }

    @EventHandler
    private static void onInstantBuy(BazaarChatEventStub.InstantBuy event) {
        var order = event.order();
        BazaarLimitsVisualizer.addOrderToLimit((double) order.getVolume() * order.getPricePerUnit());
        order.productId().ifPresent(id -> BazaarProductRegistry.notifyInstantDeal(id, order.getSide(), order.getPricePerUnit(), order.getVolume()));
    }

    @EventHandler
    private static void onOrderFlipped(BazaarChatEventStub.OrderFlipped event) {
        var order = event.order(); // pricePerUnit = profitPerUnit (profit / volume)
        order.productId().ifPresent(id -> {
            // Chat carries volume + total profit but not the sell price directly.
            // Recover buy price from the matching filled BUY order in storage.
            // sellPrice = (totalProfit + buyPrice × originalAmount) / unclaimedVolume
            BazaarProductRegistry.getTrackedOrders(id).stream()
                    .filter(it -> it.side() == TransactionType.Side.BUY)
                    .filter(it -> it.state() instanceof OrderState.Filled)
                    .filter(it -> it.unclaimedFilled() >= order.getVolume())
                    .min(Comparator.comparingInt(it -> it.unclaimedFilled() - order.getVolume()))
                    .ifPresentOrElse(
                            buyOrder -> {
                                double totalProfit = order.getPricePerUnit() * order.getVolume();
                                double sellPrice = Util.truncateNum((totalProfit + buyOrder.price() * buyOrder.originalAmount()) / order.getVolume());
                                BazaarProductRegistry.notifyFlip(id, buyOrder.price(), order.getVolume(), sellPrice);
                            },
                            () -> Util.notifyError("Flip: no matching filled BUY order for " + id + " unclaimedVol=" + order.getVolume(), null));
        });
    }

    @RunOnInit
    public static void subscribe() {
        EVENT_BUS.subscribe(BazaarChatEventHandlerStub.class);
    }
}