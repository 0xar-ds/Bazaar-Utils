package com.github.mkram17.bazaarutils.data;

import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfo;
import com.github.mkram17.bazaarutils.utils.storage.DataStorage;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public final class LastVisitedPagesStorage {
    private static final Type TYPE = new TypeToken<List<OrderInfo>>(){}.getType();

    public static final DataStorage<List<OrderInfo>> INSTANCE = new DataStorage<>(ArrayList::new, "last_visited_pages", TYPE);

    private LastVisitedPagesStorage() { }
}