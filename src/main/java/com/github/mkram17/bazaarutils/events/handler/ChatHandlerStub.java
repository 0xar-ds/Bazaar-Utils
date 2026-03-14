package com.github.mkram17.bazaarutils.events.handler;

import com.github.mkram17.bazaarutils.config.BUConfig;
import com.github.mkram17.bazaarutils.events.BazaarChatEventStub;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.annotations.autoregistration.RunOnInit;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfoStub;
import com.github.mkram17.bazaarutils.utils.regex.RegexSwitch;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.github.mkram17.bazaarutils.BazaarUtils.EVENT_BUS;

public class ChatHandlerStub {
    private static final Pattern BUY_ORDER_CREATED = Pattern.compile("Buy Order Setup! (?<amount>[\\d,]+)x (?<item>.+?) for (?<price>[\\d,.]+) coins");
    private static final Pattern SELL_ORDER_CREATED = Pattern.compile("Sell Offer Setup! (?<amount>[\\d,]+)x (?<item>.+?) for (?<price>[\\d,.]+) coins");
    private static final Pattern ORDER_FLIPPED = Pattern.compile("Order Flipped! (?<amount>[\\d,]+)x (?<item>.+?) for (?<profit>-?[\\d,.]+) coins of total expected profit");    private static final Pattern BUY_CANCELLED = Pattern.compile("Cancelled! Refunded (?<coins>[\\d,.]+) coins from cancelling Buy Order");
    private static final Pattern SELL_CANCELLED = Pattern.compile("Cancelled! Refunded (?<amount>[\\d,]+)x (?<item>.+?) from cancelling Sell Offer");
    private static final Pattern ORDER_FILLED_BUY = Pattern.compile("Your Buy Order for (?<amount>[\\d,]+)x (?<item>.+?) was filled");
    private static final Pattern ORDER_FILLED_SELL = Pattern.compile("Your Sell Offer for (?<amount>[\\d,]+)x (?<item>.+?) was filled");
    private static final Pattern CLAIMED_BUY = Pattern.compile("Claimed (?<amount>[\\d,]+)x (?<item>.+?) worth (?<coins>[\\d,.]+) coins bought for (?<price>[\\d,.]+) each");
    private static final Pattern CLAIMED_SELL = Pattern.compile("Claimed (?<coins>[\\d,.]+) coins from selling (?<amount>[\\d,]+)x (?<item>.+?) at (?<price>[\\d,.]+) each");
    private static final Pattern INSTANT_BUY = Pattern.compile("Bought (?<amount>[\\d,]+)x (?<item>.+?) for (?<price>[\\d,.]+) coins");
    private static final Pattern INSTANT_SELL = Pattern.compile("Sold (?<amount>[\\d,]+)x (?<item>.+?) for (?<coins>[\\d,.]+) coins");

    @RunOnInit
    public static void registerBazaarChat() {
        ClientReceiveMessageEvents.GAME.register((text, overlay) -> {
            String message = Util.stripFormatCodes(text.getString());

            if (message.contains("Error")) return;

            RegexSwitch.when()
                    .on(ORDER_FLIPPED, ChatHandlerStub::postFlipped)
                    .on(BUY_ORDER_CREATED, ChatHandlerStub::postBuyOrderCreated)
                    .on(SELL_ORDER_CREATED, ChatHandlerStub::postSellOrderCreated)
                    .on(BUY_CANCELLED, ChatHandlerStub::postBuyCancelled)
                    .on(SELL_CANCELLED, ChatHandlerStub::postSellCancelled)
                    .on(ORDER_FILLED_BUY, ChatHandlerStub::postFilledBuy)
                    .on(ORDER_FILLED_SELL, ChatHandlerStub::postFilledSell)
                    .on(CLAIMED_BUY, ChatHandlerStub::postClaimedBuy)
                    .on(CLAIMED_SELL, ChatHandlerStub::postClaimedSell)
                    .on(INSTANT_BUY, ChatHandlerStub::postInstantBuy)
                    .on(INSTANT_SELL, ChatHandlerStub::postInstantSell)
                    .against(message);
        });
    }

    private static void postBuyOrderCreated(Matcher matcher) {
        String item = clean(matcher.group("item"));
        int volume = amount(matcher.group("amount"));
        double price = Util.truncateNum(coins(matcher.group("price")) / volume);

        EVENT_BUS.post(new BazaarChatEventStub.OrderCreated(new OrderInfoStub(item, TransactionType.Side.BUY, volume, price)));
    }

    private static void postSellOrderCreated(Matcher matcher) {
        String item = clean(matcher.group("item"));
        int volume = amount(matcher.group("amount"));
        double price = Util.truncateNum(coins(matcher.group("price")) / volume);
        double preTax = Util.truncateNum(price / ((100.0 - BUConfig.USER_BAZAAR_FLIPPER_ACCOUNT_UPGRADE.userBazaarTax) / 100.0));

        EVENT_BUS.post(new BazaarChatEventStub.OrderCreated(new OrderInfoStub(item, TransactionType.Side.SELL, volume, preTax)));
    }

    private static void postFilledBuy(Matcher matcher) {
        String item = clean(matcher.group("item"));
        int volume = amount(matcher.group("amount"));

        EVENT_BUS.post(new BazaarChatEventStub.OrderFilled(new OrderInfoStub(item, TransactionType.Side.BUY, volume, 0.0)));
    }

    private static void postFilledSell(Matcher matcher) {
        String item = clean(matcher.group("item"));
        int volume = amount(matcher.group("amount"));

        EVENT_BUS.post(new BazaarChatEventStub.OrderFilled(new OrderInfoStub(item, TransactionType.Side.SELL, volume, 0.0)));
    }

    private static void postClaimedBuy(Matcher matcher) {
        String item = clean(matcher.group("item"));
        int volume = amount(matcher.group("amount"));
        double price = Util.truncateNum(coins(matcher.group("price")));

        EVENT_BUS.post(new BazaarChatEventStub.OrderClaimed(new OrderInfoStub(item, TransactionType.Side.BUY, volume, price)));
    }

    private static void postClaimedSell(Matcher m) {
        String item = clean(m.group("item"));
        int volume = amount(m.group("amount"));
        double totalCoins = coins(m.group("coins"));

        EVENT_BUS.post(new BazaarChatEventStub.OrderClaimed(new OrderInfoStub(item, TransactionType.Side.SELL, volume, totalCoins)));
    }
    private static void postInstantBuy(Matcher matcher) {
        String item = clean(matcher.group("item"));
        int volume = amount(matcher.group("amount"));
        double price = Util.truncateNum(coins(matcher.group("price")) / volume);

        EVENT_BUS.post(new BazaarChatEventStub.InstantBuy(new OrderInfoStub(item, TransactionType.Side.SELL, volume, price)));
    }

    private static void postInstantSell(Matcher matcher) {
        String item = clean(matcher.group("item"));
        int volume = amount(matcher.group("amount"));
        double price = Util.truncateNum(coins(matcher.group("coins")) / volume);

        EVENT_BUS.post(new BazaarChatEventStub.InstantSell(new OrderInfoStub(item, TransactionType.Side.BUY, volume, price)));
    }

    private static void postBuyCancelled(Matcher matcher) {
        double coins = coins(matcher.group("coins"));

        EVENT_BUS.post(new BazaarChatEventStub.BuyOrderCancelled(coins));
    }

    private static void postSellCancelled(Matcher matcher) {
        String item = clean(matcher.group("item"));
        int volume = amount(matcher.group("amount"));

        EVENT_BUS.post(new BazaarChatEventStub.SellOrderCancelled(new OrderInfoStub(item, TransactionType.Side.SELL, volume, 0.0)));
    }

    private static void postFlipped(Matcher matcher) {
        String item = clean(matcher.group("item"));
        int volume = amount(matcher.group("amount"));
        double profitPerUnit = Util.truncateNum(coins(matcher.group("profit")) / volume);

        EVENT_BUS.post(new BazaarChatEventStub.OrderFlipped(new OrderInfoStub(item, TransactionType.Side.SELL, volume, profitPerUnit)));
    }

    private static String clean(String source) {
        return Util.removeFormatting(source.trim());
    }

    private static int amount(String source) {
        return Integer.parseInt(source.replace(",", "").trim());
    }

    private static double coins(String source) {
        return Double.parseDouble(source.replace(",", "").trim());
    }
}