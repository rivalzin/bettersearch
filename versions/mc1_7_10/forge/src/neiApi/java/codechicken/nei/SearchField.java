package codechicken.nei;

import codechicken.nei.api.ItemFilter;

public class SearchField {
    public interface ISearchProvider {
        boolean isPrimary();

        ItemFilter getFilter(String searchText);
    }
}
