package codechicken.nei;

import codechicken.nei.api.ItemFilter;

// id, tooltip and the language fields each score separately
public class SearchField {
    public interface ISearchProvider {
        boolean isPrimary();

        ItemFilter getFilter(String searchText);
    }
}
