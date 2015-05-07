package password.pwm.http.servlet.peoplesearch;

import java.util.ArrayList;
import java.util.List;

class OrgChartReferenceBean {
    public String userKey;
    public List<String> displayNames = new ArrayList<>();
    public String photoURL;
    public boolean hasMoreNodes;

    public String getPhotoURL() {
        return photoURL;
    }

    public void setPhotoURL(String photoURL) {
        this.photoURL = photoURL;
    }

    public boolean isHasMoreNodes() {
        return hasMoreNodes;
    }

    public void setHasMoreNodes(boolean hasMoreNodes) {
        this.hasMoreNodes = hasMoreNodes;
    }

    public String getUserKey() {
        return userKey;
    }

    public void setUserKey(String userKey) {
        this.userKey = userKey;
    }

    public List<String> getDisplayNames() {
        return displayNames;
    }

    public void setDisplayNames(List<String> displayNames) {
        this.displayNames = displayNames;
    }
}
