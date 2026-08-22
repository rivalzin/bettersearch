package com.rivalzin.bettersearch.forge;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.client.SearchTreeWrapper;
import com.rivalzin.bettersearch.client.CreativeSearch;
import com.rivalzin.bettersearch.client.RecipeBookSearch;
import net.minecraft.client.Minecraft;
import net.minecraft.client.util.ISearchTree;
import net.minecraft.client.util.SearchTree;
// no mixin needed here: the manager hands out the tree object
import net.minecraft.client.util.SearchTreeManager;
import net.minecraft.client.gui.recipebook.RecipeList;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.lang.reflect.Method;

public final class SearchHook {
    private final boolean hasJei = Loader.isModLoaded("jei");
    private Method installJei;
    private boolean jeiFailed;

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
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
        installJeiHook();
    }

    private void installJeiHook() {
        if (!hasJei || jeiFailed) {
            return;
        }
        try {
            if (installJei == null) {
                installJei = Class.forName("com.rivalzin.bettersearch.forge.jei.JeiIntegration")
                        .getMethod("install");
            }
            installJei.invoke(null);
        } catch (Throwable t) {
            jeiFailed = true;
            BetterSearch.LOGGER.warn("[{}] could not hook JEI: {}",
                    BetterSearch.MOD_NAME, t.toString());
        }
    }
}
