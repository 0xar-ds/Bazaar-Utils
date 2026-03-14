package com.github.mkram17.bazaarutils.utils.bazaar.market;

import com.github.mkram17.bazaarutils.utils.bazaar.data.BazaarDataManager;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@EqualsAndHashCode
public class TransactionType {
    public enum Side {
        BUY,
        SELL;

        public Side opposite() {
            return this == BUY ? SELL : BUY;
        }

        public BazaarDataManager.PriceType asPriceType() {
            return this == BUY ? BazaarDataManager.PriceType.INSTABUY : BazaarDataManager.PriceType.INSTASELL;
        }

        public String getString() {
            return this == BUY ? "Buy" : "Sell";
        }
    }

    public enum Method {
        INSTANT {
            @Override
            public BazaarDataManager.PriceType resolveBookSide(Side side) {
                return side.asPriceType();
            }
        },

        ORDER {
            @Override
            public BazaarDataManager.PriceType resolveBookSide(Side side) {
                return side.asPriceType().opposite();
            }
        };

        public abstract BazaarDataManager.PriceType resolveBookSide(Side side);

        public String getString() {
            return this == INSTANT ? "Instant" : "Order";
        }
    }

    @Getter
    private BazaarDataManager.PriceType priceType;

    @Getter
    private Side side;

    @Getter
    private Method method;

    public TransactionType(Side side, Method method) {
        this.side = side; this.method = method;
        this.priceType = method.resolveBookSide(side);
    }

    public void edit(Side side, Method method) {
        this.side = side; this.method = method;
        this.priceType = method.resolveBookSide(side);
    }

    public TransactionType opposite() {
        return new TransactionType(side.opposite(), method);
    }

    public String getString() {
        return method.getString() + " " + side.getString();
    }

    public boolean isInstant() {
        return method == Method.INSTANT;
    }

    public boolean isOrder() {
        return method == Method.ORDER;
    }

    public boolean isBuy() {
        return side == Side.BUY;
    }

    public boolean isSell() {
        return side == Side.SELL;
    }
}