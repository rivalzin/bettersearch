package codechicken.nei;

import codechicken.nei.api.ItemFilter;

// compile stub: only the shape the hook needs, checked against the real jar by verify.sh
public class SearchField {
    public interface ISearchProvider {
        boolean isPrimary();

        ItemFilter getFilter(String searchText);
    }
}
