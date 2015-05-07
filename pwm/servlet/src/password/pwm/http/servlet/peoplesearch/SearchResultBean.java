package password.pwm.http.servlet.peoplesearch;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

class SearchResultBean implements Serializable {
    private List searchResults = new ArrayList<>();
    private boolean sizeExceeded;

    public List getSearchResults() {
        return searchResults;
    }

    public void setSearchResults(List searchResults) {
        this.searchResults = searchResults;
    }

    public boolean isSizeExceeded() {
        return sizeExceeded;
    }

    public void setSizeExceeded(boolean sizeExceeded) {
        this.sizeExceeded = sizeExceeded;
    }
}
