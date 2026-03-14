package com.github.mkram17.bazaarutils.utils.bazaar.data;

import com.github.mkram17.bazaarutils.mixin.AccessorSkyBlockBazaarReply;
import lombok.Getter;
import net.hypixel.api.reply.AbstractReply;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply;

import java.util.Map;

/**
 * Domain wrapper around a raw {@link SkyBlockBazaarReply}.
 */
public final class CustomBazaarReply extends AbstractReply {
    @Getter
    private final long lastUpdated;

    @Getter
    private final Map<String, SkyBlockBazaarReply.Product> products;

    private CustomBazaarReply(long lastUpdated, Map<String, SkyBlockBazaarReply.Product> products) {
        this.lastUpdated = lastUpdated;
        this.products = products;
    }

    public static CustomBazaarReply fromSkyBlockReply(SkyBlockBazaarReply reply) {
        long lastUpdated = ((AccessorSkyBlockBazaarReply) reply).getLastUpdated();

        return new CustomBazaarReply(lastUpdated, reply.getProducts());
    }

    /** @return the product for {@code productId}, or {@code null} if absent. */
    public SkyBlockBazaarReply.Product getProduct(String productId) {
        var products = this.products;

        return products != null ? products.get(productId) : null;
    }
}