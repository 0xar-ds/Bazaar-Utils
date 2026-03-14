package com.github.mkram17.bazaarutils.utils.bazaar.market.price;

import com.github.mkram17.bazaarutils.utils.bazaar.data.BazaarProductRegistry;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import lombok.Getter;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Holds a parsed price + side and exposes stateless live market queries
 * against {@link BazaarProductRegistry}.
 */
public class PriceInfoStub {
    @Getter
    protected final TransactionType.Side side;

    @Getter
    protected final double pricePerUnit;

    public PriceInfoStub(double pricePerUnit, TransactionType.Side side) {
        this.pricePerUnit = pricePerUnit;
        this.side = side;
    }

    /**
     * Current best market price for this side (ORDER method, so book is flipped
     * per the TransactionType routing rules).
     */
    public OptionalDouble marketPrice(String productId) {
        return BazaarProductRegistry.findItemPriceOptional(productId, new TransactionType(side, TransactionType.Method.ORDER));
    }

    /**
     * What price would put this order at {@code position} relative to the current book?
     *
     * <ul>
     *   <li>BUY  COMPETITIVE → market + 0.1 (outbid everyone)</li>
     *   <li>BUY  MATCHED     → market</li>
     *   <li>BUY  OUTBID      → market − 0.1</li>
     *   <li>SELL COMPETITIVE → market − 0.1 (undercut everyone)</li>
     *   <li>SELL MATCHED     → market</li>
     *   <li>SELL OUTBID      → market + 0.1</li>
     * </ul>
     */
    public OptionalDouble priceForPosition(String productId, PricingPosition position) {
        OptionalDouble marketOpt = marketPrice(productId);

        if (marketOpt.isEmpty()) return OptionalDouble.empty();

        double market = marketOpt.getAsDouble();

        double result = switch (side) {
            case BUY  -> switch (position) {
                case COMPETITIVE -> market + 0.1;
                case MATCHED -> market;
                case OUTBID -> market - 0.1;
            };
            case SELL -> switch (position) {
                case COMPETITIVE -> market - 0.1;
                case MATCHED -> market;
                case OUTBID -> market + 0.1;
            };
        };

        return OptionalDouble.of(result);
    }

    /**
     * Where does {@link #pricePerUnit} sit relative to the current book?
     * Empty if the market price cannot be determined.
     */
    public Optional<PricingPosition> pricingPosition(String productId) {
        int position = bookPosition(productId);

        if (position < 0) return Optional.empty();

        if (position > 0) return Optional.of(PricingPosition.OUTBID);

        // position == 0 — at the best price level, but are we alone?
        var tx = new TransactionType(side, TransactionType.Method.ORDER);

        OptionalInt count = BazaarProductRegistry.getOrderCountOptional(productId, tx, pricePerUnit);

        return Optional.of(count.isPresent() && count.getAsInt() > 1
                ? PricingPosition.MATCHED
                : PricingPosition.COMPETITIVE);
    }

    /**
     * How many price levels are ahead of this order in the book — 0 = competitive.
     * Returns -1 if the product is not in the registry.
     */
    public int bookPosition(String productId) {
        return BazaarProductRegistry.bookPositionOf(productId, side, pricePerUnit);
    }
}