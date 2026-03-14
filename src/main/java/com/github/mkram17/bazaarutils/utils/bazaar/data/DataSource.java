package com.github.mkram17.bazaarutils.utils.bazaar.data;

import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;

public sealed interface DataSource permits
        DataSource.ApiSnapshot,
        DataSource.ItemSummary,
        DataSource.ManageOrders,
        DataSource.OrdersScreen,
        DataSource.OrderPlaced,
        DataSource.OrderFilled,
        DataSource.OrderCancelled,
        DataSource.OrderFlipped,
        DataSource.InstantDeal {

    record ApiSnapshot(long snapshotTs) implements DataSource {}
    record ItemSummary(long observedAt) implements DataSource {}
    record ManageOrders(long observedAt) implements DataSource {}
    record OrdersScreen(long observedAt) implements DataSource {}
    record OrderPlaced(long placedAt) implements DataSource {}
    record OrderFilled(long confirmedAt) implements DataSource {}
    record OrderCancelled(long confirmedAt) implements DataSource {}
    record OrderFlipped(long confirmedAt) implements DataSource {}
    record InstantDeal(long confirmedAt, TransactionType.Side side) implements DataSource {}

    /**
     * Whether {@code this} source is superseded by an {@code incoming} source,
     * meaning the incoming source should be considered more authoritative.
     */
    default boolean isSupersededBy(DataSource incoming) {
        return switch (this) {
            case OrderPlaced ignored ->
                    incoming instanceof ApiSnapshot
                            || incoming instanceof ItemSummary
                            || incoming instanceof OrdersScreen
                            || incoming instanceof ManageOrders;

            case OrdersScreen current -> switch (incoming) {
                case OrdersScreen it -> it.observedAt() > current.observedAt();
                case ManageOrders it -> it.observedAt() > current.observedAt();
                default -> false;
            };

            case ManageOrders current -> switch (incoming) {
                case OrdersScreen it -> it.observedAt() > current.observedAt();
                case ManageOrders it -> it.observedAt() > current.observedAt();
                default -> false;
            };

            case ItemSummary current -> switch (incoming) {
                case ItemSummary it -> it.observedAt() > current.observedAt();
                case ApiSnapshot it -> it.snapshotTs() > current.observedAt();
                default -> false;
            };

            case ApiSnapshot current -> switch (incoming) {
                case ApiSnapshot it -> it.snapshotTs() > current.snapshotTs();
                case ItemSummary it -> it.observedAt() > current.snapshotTs();
                default -> false;
            };

            case OrderFilled current -> switch (incoming) {
                case OrdersScreen it -> it.observedAt() > current.confirmedAt();
                case ManageOrders it -> it.observedAt() > current.confirmedAt();
                default -> false;
            };

            case OrderCancelled current -> switch (incoming) {
                case OrdersScreen it -> it.observedAt() > current.confirmedAt();
                case ManageOrders it -> it.observedAt() > current.confirmedAt();
                default -> false;
            };

            case OrderFlipped current -> switch (incoming) {
                case OrdersScreen it -> it.observedAt() > current.confirmedAt();
                case ManageOrders it -> it.observedAt() > current.confirmedAt();
                default -> false;
            };

            case InstantDeal current -> switch (incoming) {
                case OrdersScreen it -> it.observedAt() > current.confirmedAt();
                case ManageOrders it -> it.observedAt() > current.confirmedAt();
                default -> false;
            };
        };
    }
}