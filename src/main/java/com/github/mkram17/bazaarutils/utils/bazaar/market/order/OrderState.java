package com.github.mkram17.bazaarutils.utils.bazaar.market.order;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public sealed interface OrderState permits
        OrderState.Set,
        OrderState.Partial,
        OrderState.Filled,
        OrderState.Cancelled,
        OrderState.Claimed {
    record Set() implements OrderState {
        static final MapCodec<Set> CODEC = MapCodec.unit(new Set());
    }

    record Partial(int filledSoFar) implements OrderState {
        static final MapCodec<Partial> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.INT.fieldOf("filledSoFar").forGetter(Partial::filledSoFar)
        ).apply(instance, Partial::new));
    }

    record Filled(int totalFilled, long filledAt) implements OrderState {
        static final MapCodec<Filled> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.INT.fieldOf("totalFilled").forGetter(Filled::totalFilled),
                Codec.LONG.fieldOf("filledAt").forGetter(Filled::filledAt)
        ).apply(instance, Filled::new));
    }

    record Cancelled(long cancelledAt) implements OrderState {
        static final MapCodec<Cancelled> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.LONG.fieldOf("cancelledAt").forGetter(Cancelled::cancelledAt)
        ).apply(instance, Cancelled::new));
    }

    record Claimed(int claimedAmount, long claimedAt) implements OrderState {
        static final MapCodec<Claimed> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.INT.fieldOf("claimedAmount").forGetter(Claimed::claimedAmount),
                Codec.LONG.fieldOf("claimedAt").forGetter(Claimed::claimedAt)
        ).apply(instance, Claimed::new));
    }

    Codec<OrderState> CODEC = Codec.STRING.dispatch(
            "type",
            state -> switch (state) {
                case Set ignored -> "set";
                case Partial ignored -> "partial";
                case Filled ignored -> "filled";
                case Cancelled ignored -> "cancelled";
                case Claimed ignored -> "claimed";
            },
            type -> switch (type) {
                case "set" -> Set.CODEC;
                case "partial" -> Partial.CODEC;
                case "filled" -> Filled.CODEC;
                case "cancelled" -> Cancelled.CODEC;
                case "claimed" -> Claimed.CODEC;
                default -> throw new IllegalArgumentException("Unknown OrderState: " + type);
            }
    );
}