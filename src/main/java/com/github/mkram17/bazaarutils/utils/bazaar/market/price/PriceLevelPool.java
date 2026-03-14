package com.github.mkram17.bazaarutils.utils.bazaar.market.price;

import com.github.mkram17.bazaarutils.utils.bazaar.data.DataSource;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply;

public record PriceLevelPool(
        double price,
        long totalVolume,
        int aggregatedOrderCount,
        long timestamp,
        DataSource source
) {
    public PriceLevelPool withPlacementIncrement(int amount, long now) {
        return new PriceLevelPool(price, totalVolume + amount, aggregatedOrderCount + 1, now, new DataSource.OrderPlaced(now));
    }

    public PriceLevelPool withVolumeDecrement(long amount) {
        return new PriceLevelPool(price, Math.max(0, totalVolume - amount), aggregatedOrderCount, timestamp, source);
    }

    public static PriceLevelPool fromApiSummary(SkyBlockBazaarReply.Product.Summary summary, DataSource.ApiSnapshot source) {
        return new PriceLevelPool(summary.getPricePerUnit(), summary.getAmount(), (int) summary.getOrders(), source.snapshotTs(), source);
    }

    public static PriceLevelPool fromItemSummary(double price, int volume, int orders, long now) {
        return new PriceLevelPool(price, volume, orders, now, new DataSource.ItemSummary(now));
    }
}