package password.pwm.http.servlet.helpdesk;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class HelpdeskSearchResultsBean implements Serializable {
    private List<Map<String,Object>> searchResults = new ArrayList<>();
    private boolean sizeExceeded;

    public List<Map<String, Object>> getSearchResults() {
        return searchResults;
    }

    public void setSearchResults(List<Map<String, Object>> searchResults) {
        this.searchResults = searchResults;
    }

    public boolean isSizeExceeded() {
        return sizeExceeded;
    }

    public void setSizeExceeded(boolean sizeExceeded) {
        this.sizeExceeded = sizeExceeded;
    }
}
