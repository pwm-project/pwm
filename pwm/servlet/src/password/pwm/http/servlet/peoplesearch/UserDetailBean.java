package password.pwm.http.servlet.peoplesearch;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

class UserDetailBean implements Serializable {
    private List<String> displayNames;
    private String userKey;
    private Map<String, AttributeDetailBean> detail;
    private String photoURL;
    private boolean hasOrgChart;
    private String orgChartParentKey;

    public List<String> getDisplayNames() {
        return displayNames;
    }

    public void setDisplayNames(List<String> displayNames) {
        this.displayNames = displayNames;
    }

    public String getUserKey() {
        return userKey;
    }

    public void setUserKey(String userKey) {
        this.userKey = userKey;
    }

    public Map<String, AttributeDetailBean> getDetail() {
        return detail;
    }

    public void setDetail(Map<String, AttributeDetailBean> detail) {
        this.detail = detail;
    }

    public String getPhotoURL() {
        return photoURL;
    }

    public void setPhotoURL(String photoURL) {
        this.photoURL = photoURL;
    }

    public boolean isHasOrgChart() {
        return hasOrgChart;
    }

    public void setHasOrgChart(boolean hasOrgChart) {
        this.hasOrgChart = hasOrgChart;
    }

    public String getOrgChartParentKey() {
        return orgChartParentKey;
    }

    public void setOrgChartParentKey(String orgChartParentKey) {
        this.orgChartParentKey = orgChartParentKey;
    }
}
