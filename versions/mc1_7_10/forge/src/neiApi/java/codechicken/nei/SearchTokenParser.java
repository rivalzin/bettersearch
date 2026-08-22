package codechicken.nei;

import codechicken.nei.api.ItemFilter;
import net.minecraft.client.resources.Language;
import net.minecraft.util.EnumChatFormatting;

import java.util.List;

public class SearchTokenParser {
    public void addProvider(ISearchParserProvider provider) {
        throw new AssertionError("compile stub");
    }

    public enum SearchMode {
        ALWAYS,
        PREFIX,
        NEVER
    }

    public interface ISearchParserProvider {
        ItemFilter getFilter(String searchText);

        char getPrefix();

        EnumChatFormatting getHighlightedColor();

        SearchMode getSearchMode();

        default List<Language> getMatchingLanguages() {
            throw new AssertionError("compile stub");
        }
    }
}
