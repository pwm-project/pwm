package password.pwm.http.servlet.peoplesearch;

import java.io.Serializable;
import java.util.List;

class OrgChartData implements Serializable {
    private OrgChartReferenceBean parent;
    private List<OrgChartReferenceBean> siblings;

    public OrgChartReferenceBean getParent() {
        return parent;
    }

    public void setParent(OrgChartReferenceBean parent) {
        this.parent = parent;
    }

    public List<OrgChartReferenceBean> getSiblings() {
        return siblings;
    }

    public void setSiblings(List<OrgChartReferenceBean> siblings) {
        this.siblings = siblings;
    }
}
