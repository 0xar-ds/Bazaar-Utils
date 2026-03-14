package com.github.mkram17.bazaarutils.utils.bazaar.components;

import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PriceLevelPool;
import com.github.mkram17.bazaarutils.utils.minecraft.components.LoreParser;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Optional;

public final class SummaryScreenParser {
    public record SummaryResult(List<PriceLevelPool> buyLevels, List<PriceLevelPool> sellLevels) {}

    private SummaryScreenParser() {}

    public static SummaryResult parseItemPage(ItemStack buyOrderStack, ItemStack sellOfferStack) {
        return new SummaryResult(parsePriceLevels(buyOrderStack), parsePriceLevels(sellOfferStack));
    }

    public static List<PriceLevelPool> parsePriceLevels(ItemStack stack) {
        long now = System.currentTimeMillis();
        var lines = LoreParser.lines(stack);

        PlayerActionUtil.notifyAll("Parsing " + lines.size() + " lore lines from: " + stack.getName().getString(), NotificationType.BAZAARDATA);

        return lines.stream()
                .peek(line -> PlayerActionUtil.notifyAll(
                        "Line siblings=" + line.getSiblings().size()
                                + " | " + line.getSiblings().stream()
                                .map(Text::getString)
                                .collect(java.util.stream.Collectors.joining(", ")),
                        NotificationType.BAZAARDATA))
                .filter(line -> line.getSiblings().size() == 8)
                .map(line -> parsePriceLevel(line, now))
                .peek(pool -> {
                    if (pool.isEmpty()) PlayerActionUtil.notifyAll("parsePriceLevel returned empty on a size-8 line", NotificationType.BAZAARDATA);
                })
                .flatMap(Optional::stream)
                .toList();
    }

    private static Optional<PriceLevelPool> parsePriceLevel(Text line, long now) {
        var siblings = line.getSiblings();

        if (siblings.size() != 8) return Optional.empty();

        try {
            double price = Double.parseDouble(siblings.get(1).getString().replace(" coins ", "").replace(",", "").trim());
            int volume = Integer.parseInt(siblings.get(3).getString().replace(",", "").trim());
            int orders = Integer.parseInt(siblings.get(6).getString().trim());

            PlayerActionUtil.notifyAll("Parsed level: price=" + price + " vol=" + volume + " orders=" + orders, NotificationType.BAZAARDATA);

            return Optional.of(PriceLevelPool.fromItemSummary(price, volume, orders, now));
        } catch (Exception e) {
            PlayerActionUtil.notifyAll(
                    "Parse exception on siblings: "
                            + siblings.stream().map(Text::getString).collect(java.util.stream.Collectors.joining(", "))
                            + " | " + e.getMessage(),
                    NotificationType.BAZAARDATA);

            return Optional.empty();
        }
    }
}