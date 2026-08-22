package mezz.jei.gui.ingredients;

import java.util.List;

public interface IIngredientListElement<V> {
    V getIngredient();

    String getDisplayName();

    String getResourceId();

    List<String> getTooltipStrings();
}
