package codechicken.nei.api;

import net.minecraft.item.ItemStack;

public interface ItemFilter {
    boolean matches(ItemStack item);
}
