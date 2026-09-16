package com.rivalzin.bettersearch.forge;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.client.SearchTreeWrapper;
import com.rivalzin.bettersearch.client.CreativeSearch;
import com.rivalzin.bettersearch.client.RecipeBookSearch;
import com.rivalzin.bettersearch.client.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.client.util.ISearchTree;
import net.minecraft.client.util.SearchTree;

import net.minecraft.client.util.SearchTreeManager;
import net.minecraft.client.gui.recipebook.RecipeList;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class SearchHook {
    private final boolean hasJei = Loader.isModLoaded("jei");
    private Method installJei;
    private long jeiRetryAt;
    private Method refreshCreative;
    private Field creativeSearchField;
    private Field creativeScrollField;
    private int appliedGeneration;
    private int appliedConfig = -1;
    private long creativeRetryAt;

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (Minecraft.getMinecraft().player != null) {
            CreativeSearch.warmUp();
        }

        SearchTreeManager manager = Minecraft.getMinecraft().getSearchTreeManager();
        if (manager == null) {
            return;
        }
        ISearchTree<ItemStack> items = manager.get(SearchTreeManager.ITEMS);
        if (!(items instanceof SearchTreeWrapper) && items instanceof SearchTree) {
            manager.register(SearchTreeManager.ITEMS,
                    new SearchTreeWrapper<>((SearchTree<ItemStack>) items, CreativeSearch::search));
            BetterSearch.LOGGER.info("[{}] creative search hooked (wrapped tree, no mixin)",
                    BetterSearch.MOD_NAME);
        }
        ISearchTree<RecipeList> recipes = manager.get(SearchTreeManager.RECIPES);
        if (!(recipes instanceof SearchTreeWrapper) && recipes instanceof SearchTree) {
            manager.register(SearchTreeManager.RECIPES,
                    new SearchTreeWrapper<>((SearchTree<RecipeList>) recipes, RecipeBookSearch::search));
            BetterSearch.LOGGER.info("[{}] recipe book search hooked (same wrapped tree)",
                    BetterSearch.MOD_NAME);
        }
        refreshOpenCreativeSearch();
        installJeiHook();
    }

    private void refreshOpenCreativeSearch() {
        int generation = CreativeSearch.generation();
        int config = ModConfig.stamp();
        if (generation == appliedGeneration && config == appliedConfig) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (!(minecraft.currentScreen instanceof GuiContainerCreative)) {
            appliedGeneration = generation;
            appliedConfig = config;
            return;
        }
        GuiContainerCreative screen = (GuiContainerCreative) minecraft.currentScreen;
        if (screen.getSelectedTabIndex() != CreativeTabs.SEARCH.getIndex()) {
            appliedGeneration = generation;
            appliedConfig = config;
            return;
        }
        if (System.nanoTime() < creativeRetryAt) {
            return;
        }
        try {
            if (refreshCreative == null) {
                refreshCreative = ReflectionHelper.findMethod(GuiContainerCreative.class,
                        "updateCreativeSearch", "func_147053_i");
            }
            if (creativeSearchField == null) {
                creativeSearchField = ReflectionHelper.findField(GuiContainerCreative.class,
                        "searchField", "field_147062_A");
            }
            if (creativeScrollField == null) {
                creativeScrollField = ReflectionHelper.findField(GuiContainerCreative.class,
                        "currentScroll", "field_147067_x");
            }
            GuiTextField search = (GuiTextField) creativeSearchField.get(screen);
            if (search != null && search.getVisible() && !search.getText().isEmpty()) {
                float scroll = creativeScrollField.getFloat(screen);
                refreshCreative.invoke(screen);
                GuiContainerCreative.ContainerCreative container =
                        (GuiContainerCreative.ContainerCreative) screen.inventorySlots;
                scroll = container.canScroll() ? scroll : 0.0F;
                creativeScrollField.setFloat(screen, scroll);
                container.scrollTo(scroll);
            }
            appliedGeneration = generation;
            appliedConfig = config;
        } catch (Exception | LinkageError error) {
            com.rivalzin.bettersearch.FailurePolicy.rethrowFatal(error);
            creativeRetryAt = System.nanoTime() + 5_000_000_000L;
            BetterSearch.LOGGER.debug("[{}] creative search refresh deferred: {}",
                    BetterSearch.MOD_NAME, error.toString());
        }
    }

    private void installJeiHook() {
        if (!hasJei || System.nanoTime() < jeiRetryAt) {
            return;
        }
        try {
            if (installJei == null) {
                installJei = Class.forName("com.rivalzin.bettersearch.forge.jei.JeiIntegration")
                        .getMethod("install");
            }
            installJei.invoke(null);
        } catch (Exception | LinkageError t) {
            com.rivalzin.bettersearch.FailurePolicy.rethrowFatal(t);
            jeiRetryAt = System.nanoTime() + 5_000_000_000L;
            BetterSearch.LOGGER.warn("[{}] could not hook JEI: {}",
                    BetterSearch.MOD_NAME, t.toString());
        }
    }
}
