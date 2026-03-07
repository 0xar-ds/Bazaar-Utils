package com.github.mkram17.bazaarutils.features.gui.inventory;

import com.github.mkram17.bazaarutils.data.LastVisitedPagesStorage;
import com.github.mkram17.bazaarutils.events.ChestLoadedEvent;
import com.github.mkram17.bazaarutils.events.listener.BUListener;
import com.github.mkram17.bazaarutils.features.gui.buttons.Bookmarks;
import com.github.mkram17.bazaarutils.features.util.BUKeybinding;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.github.mkram17.bazaarutils.utils.bazaar.data.BazaarDataManager;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreens;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfo;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenManager;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenType;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.container.ContainerManager;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import meteordevelopment.orbit.EventHandler;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.*;

@Slf4j
@Module
public class LastVisitedPages extends BUListener {
    public record PageKeybind(KeyBinding keyBind, OrderInfo pageInfo) {}

    private static final int MAX_PAGES = 10;
    private static final KeyBinding.Category CATEGORY = KeyBinding.Category.create(Identifier.of("bazaarutils", "lastvisited"));

    private static final List<PageKeybind> PAGE_KEYBINDS = new ArrayList<>();

    private static final int[] GLFW_KEYS = {
            GLFW.GLFW_KEY_1, GLFW.GLFW_KEY_2, GLFW.GLFW_KEY_3,
            GLFW.GLFW_KEY_4, GLFW.GLFW_KEY_5, GLFW.GLFW_KEY_6,
            GLFW.GLFW_KEY_7, GLFW.GLFW_KEY_8, GLFW.GLFW_KEY_9,
            GLFW.GLFW_KEY_0   // index 9 → slot 10
    };

    private static class PageSlotKeybinding extends BUKeybinding {
        public PageSlotKeybinding(int glfwKey, String label) {
            super(new KeyBinding(
                    "key.bazaarutils.page." + label,
                    InputUtil.Type.KEYSYM,
                    glfwKey,
                    CATEGORY
            ));
        }
    }

    static {
        for (int i = 0; i < MAX_PAGES; i++) {
            String keyName = (i == 9) ? "0" : String.valueOf(i + 1);
            KeyBinding kb = new PageSlotKeybinding(GLFW_KEYS[i], keyName).keyBinding;
            // OrderInfo starts null — filled in as pages are visited
            PAGE_KEYBINDS.add(new PageKeybind(kb, null));
        }
    }

    @Getter
    private int ticksBetweenPresses;

    public static void savePages() {
        LastVisitedPagesStorage.INSTANCE.save();
    }

    public static List<OrderInfo> pages() {
        return LastVisitedPagesStorage.INSTANCE.get();
    }

    public boolean isSubmapActive() {
        return ScreenManager.getInstance().isCurrent(BazaarScreens.ALL.toArray(ScreenType[]::new));
    }

    public LastVisitedPages() {
        super();
    }

    @EventHandler
    private void onChestLoaded(ChestLoadedEvent event) {
        if (!ScreenManager.getInstance().isCurrent(BazaarScreens.ITEM_PAGE)) {
            return;
        }

        Bookmarks.resolveOrderInfoFromScreen().ifPresent(orderInfo -> {
            shiftPageIntoHistory(orderInfo);
            savePages();
        });
    }

    private void shiftPageIntoHistory(OrderInfo newPage) {
        List<OrderInfo> pageList = pages();

        pageList.removeIf(page -> page.getName().equalsIgnoreCase(newPage.getName()));
        pageList.addFirst(newPage);

        while (pageList.size() > MAX_PAGES) {
            pageList.removeLast();
        }
    }

    private static void navigateToPage(OrderInfo pageInfo) {
        Optional<Integer> screenSlot = findItemScreenSlot(pageInfo.getProductID());
        screenSlot.ifPresent(slot -> ContainerManager.clickSlot(slot, 0));
        screenSlot.ifPresentOrElse((ignored) -> Util.logMessage("was present"), () -> Util.logMessage("was not present"));
    }

    private static Optional<Integer> findItemScreenSlot(String productId) {
        return ScreenManager.getInstance()
                .current()
                .flatMap(context -> context.as(GenericContainerScreen.class))
                .flatMap(screen -> {
                    int containerSize = screen.getScreenHandler().getInventory().size();
                    List<ItemStack> mainStacks = Objects.requireNonNull(MinecraftClient.getInstance().player)
                            .getInventory().getMainStacks();

                    for (int i = 0; i < mainStacks.size(); i++) {
                        ItemStack stack = mainStacks.get(i);

                        boolean matches = !stack.isEmpty()
                                && BazaarDataManager.findProductIdOptional(stack.getName().getString())
                                .map(id -> id.equals(productId))
                                .orElse(false);

                        if (!matches) continue;

                        int screenSlot = (i < 9)
                                ? containerSize + 27 + i
                                : containerSize + (i - 9);

                        return Optional.of(screenSlot);
                    }

                    return Optional.empty();
                });
    }

    @Override
    protected void registerFabricEvents() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            ScreenKeyboardEvents.allowKeyPress(screen).register((s, key) -> {
                if (!isSubmapActive()) return true;

                List<OrderInfo> pageList = pages();

                for (int i = 0; i < GLFW_KEYS.length; i++) {
                    if (key.key() != GLFW_KEYS[i]) continue;
                    if (i >= pageList.size()) return true;

                    OrderInfo pageInfo = pageList.get(i);

                    log.debug("[LastVisitedPages] Submap key '{}' → navigating to '{}'",
                            (i == 9) ? "0" : String.valueOf(i + 1),
                            pageInfo.getName());

                    navigateToPage(pageInfo);
                    return false;
                }

                return true;
            });
        });
    }
}