package com.github.mkram17.bazaarutils.events;

import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfoStub;

/**
 * Sealed hierarchy of chat-derived bazaar events.
 *
 * <p>Every event carries an {@link OrderInfoStub} DTO built at parse time.
 * For events where price is not available from chat (OrderFilled, SellOrderCancelled),
 * {@code order.pricePerUnit()} is {@code 0.0} — callers must not use it.
 *
 * <p>{@link BuyOrderCancelled} carries no item info (chat only gives coin refund).
 */
public sealed interface BazaarChatEventStub {
    /**
     * A buy order or sell offer was placed.
     */
    record OrderCreated(OrderInfoStub order) implements BazaarChatEventStub {}

    /**
     * An order was fully filled.
     * {@code order.pricePerUnit()} is {@code 0.0} — price is not in the fill message.
     */
    record OrderFilled(OrderInfoStub order) implements BazaarChatEventStub {}

    /**
     * Filled items or coins were claimed from an order.
     */
    record OrderClaimed(OrderInfoStub order) implements BazaarChatEventStub {}

    /**
     * A sell offer was cancelled and items refunded.
     * {@code order.pricePerUnit()} is {@code 0.0} — price is not in the cancel message.
     */
    record SellOrderCancelled(OrderInfoStub order) implements BazaarChatEventStub {}

    /**
     * A buy order was cancelled and coins refunded.
     * No item info available from chat — matched in the registry by coin amount.
     */
    record BuyOrderCancelled(double coinsRefunded) implements BazaarChatEventStub {}

    /**
     * Player instant-sold items into the buy book.
     * {@code order.side()} is {@link com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType.Side#BUY}
     * — the consumed side.
     */
    record InstantSell(OrderInfoStub order) implements BazaarChatEventStub {}

    /**
     * Player instant-bought items from the sell book.
     * {@code order.side()} is {@link com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType.Side#SELL}
     * — the consumed side.
     */
    record InstantBuy(OrderInfoStub order) implements BazaarChatEventStub {}

    /**
     * {@code order.pricePerUnit()} is profit-per-unit (profit / volume),
     * NOT the sell price. Sell price = buyOrder.price() + pricePerUnit,
     * resolved in the event handler after buy order lookup.
     */
    record OrderFlipped(OrderInfoStub order) implements BazaarChatEventStub {}
}