package com.rivalzin.bettersearch.forge.nei;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.FailurePolicy;
import com.rivalzin.bettersearch.async.StorageSearchSession;
import com.rivalzin.bettersearch.client.ModConfig;
import com.rivalzin.bettersearch.core.SearchSettings;
import net.minecraft.item.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public final class NeiIntegration {
    private static Object provider;
    private static NeiSearchBridge bridge;
    private static Object updateFilter;
    private static Method restart;
    private static Field searchOwner;
    private static Field panelOwner;
    private static boolean ownershipChecked;
    private static int appliedStamp = -1;

    private NeiIntegration() {
    }

    public static void install() throws Exception {
        if (provider == null) {
            ClassLoader loader = NeiIntegration.class.getClassLoader();
            Class<?> providerType = Class.forName("codechicken.nei.widget.SearchField$ISearchProvider", false, loader);
            Class<?> filterType = providerType.getMethod("getFilter", String.class).getReturnType();
            filterType.getMethod("matches", ItemStack.class);
            Class<?> itemList = Class.forName("codechicken.nei.util.ItemList", false, loader);
            Field items = itemList.getField("items");
            updateFilter = itemList.getField("updateFilter").get(null);
            restart = updateFilter.getClass().getMethod("restart");
            restart.setAccessible(true);
            bridge = new NeiSearchBridge(items, NeiIntegration::refresh);
            Object created = Proxy.newProxyInstance(loader, new Class<?>[]{providerType}, (proxy, method, arguments) -> {
                if (method.getDeclaringClass() == Object.class) {
                    return objectMethod(proxy, method, arguments, "Better Search NEI provider");
                }
                if ("isPrimary".equals(method.getName())) {
                    return false;
                }
                if ("getFilter".equals(method.getName())) {
                    String query = (String) arguments[0];
                    SearchSettings settings = ModConfig.settings();
                    if (!settings.enabled || !settings.searchNei || !StorageSearchSession.isPlainTerm(query)) {
                        return null;
                    }
                    bridge.request(query);
                    InvocationHandler filter = (filterProxy, filterMethod, filterArguments) -> {
                        if (filterMethod.getDeclaringClass() == Object.class) {
                            return objectMethod(filterProxy, filterMethod, filterArguments, "Better Search NEI filter");
                        }
                        return bridge.matches((ItemStack) filterArguments[0], query);
                    };
                    return Proxy.newProxyInstance(loader, new Class<?>[]{filterType}, filter);
                }
                throw new UnsupportedOperationException(method.toString());
            });
            Class.forName("codechicken.nei.api.API", false, loader).getMethod("addSearchProvider", providerType)
                    .invoke(null, created);
            provider = created;
            BetterSearch.LOGGER.info("[{}] NEI search hooked as a secondary provider", BetterSearch.MOD_NAME);
        }
        bridge.tick();
        int stamp = ModConfig.stamp();
        if (stamp != appliedStamp) {
            appliedStamp = stamp;
            refresh();
        }
    }

    public static synchronized boolean usesJeiSearch() {
        if (!ownershipChecked) {
            ownershipChecked = true;
            try {
                Class<?> manager = Class.forName("codechicken.nei.jei.JEIIntegrationManager", false,
                        NeiIntegration.class.getClassLoader());
                searchOwner = manager.getField("searchBoxOwner");
                panelOwner = manager.getField("itemPanelOwner");
            } catch (ReflectiveOperationException | LinkageError error) {
                FailurePolicy.rethrowFatal(error);
                searchOwner = null;
                panelOwner = null;
            }
        }
        try {
            return searchOwner != null && panelOwner != null
                    && "NEI".equals(String.valueOf(searchOwner.get(null)))
                    && "JEI".equals(String.valueOf(panelOwner.get(null)));
        } catch (ReflectiveOperationException | LinkageError error) {
            FailurePolicy.rethrowFatal(error);
            return false;
        }
    }

    private static Object objectMethod(Object proxy, Method method, Object[] arguments, String label) {
        if ("equals".equals(method.getName())) {
            return proxy == arguments[0];
        }
        if ("hashCode".equals(method.getName())) {
            return System.identityHashCode(proxy);
        }
        return label;
    }

    private static void refresh() {
        try {
            restart.invoke(updateFilter);
        } catch (ReflectiveOperationException | LinkageError error) {
            FailurePolicy.rethrowFatal(error);
            BetterSearch.LOGGER.debug("[{}] NEI filter refresh deferred: {}", BetterSearch.MOD_NAME, error.toString());
        }
    }
}
