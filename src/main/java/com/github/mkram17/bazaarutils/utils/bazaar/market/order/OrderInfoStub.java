package com.github.mkram17.bazaarutils.utils.bazaar.market.order;

import com.github.mkram17.bazaarutils.utils.bazaar.data.BazaarProductRegistry;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PriceInfoStub;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PricingPosition;
import lombok.Getter;

import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Parsed item DTO — short-lived.
 *
 * <p>Extends {@link PriceInfoStub} so all live price queries are available
 * without repeating the productId at every call site — {@link #productId()}
 * is resolved once from the conversions cache and threaded through automatically.
 */
public class OrderInfoStub extends PriceInfoStub {
    @Getter
    private final String name;

    @Getter
    private final int volume;

    /** Resolved lazily on construction; null if name is unknown. */
    private final String productId;

    public OrderInfoStub(String name, TransactionType.Side side, int volume, double pricePerUnit) {
        super(pricePerUnit, side);
        this.name = name;
        this.volume = volume;
        this.productId = BazaarProductRegistry.findProductIdOptional(name).orElse(null);
    }

    /** Empty if name could not be resolved to a Hypixel product ID. */
    public Optional<String> productId() {
        return Optional.ofNullable(productId);
    }

    public static boolean isValidName(String name) {
        return name != null && BazaarProductRegistry.findProductIdOptional(name).isPresent();
    }

    /**
     * @see PriceInfoStub#marketPrice(String)
     * */
    public OptionalDouble marketPrice() {
        return productId != null ? marketPrice(productId) : OptionalDouble.empty();
    }

    /**
     *  @see PriceInfoStub#priceForPosition(String, PricingPosition)
     * */
    public OptionalDouble priceForPosition(PricingPosition position) {
        return productId != null ? priceForPosition(productId, position) : OptionalDouble.empty();
    }

    /**
     *  @see PriceInfoStub#pricingPosition(String)
     * */
    public Optional<PricingPosition> pricingPosition() {
        return productId != null ? pricingPosition(productId) : Optional.empty();
    }

    /**
     *  @see PriceInfoStub#bookPosition(String)
     * */
    public int bookPosition() {
        return productId != null ? bookPosition(productId) : -1;
    }

    @Override
    public String toString() {
        return "(name=" + name + ", side=" + side + ", volume=" + volume + ", price=" + pricePerUnit + ")";
    }
}