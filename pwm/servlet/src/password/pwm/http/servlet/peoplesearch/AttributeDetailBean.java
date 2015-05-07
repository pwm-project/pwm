package password.pwm.http.servlet.peoplesearch;

import password.pwm.config.FormConfiguration;

import java.io.Serializable;
import java.util.Collection;

class AttributeDetailBean implements Serializable {
    private String name;
    private String label;
    private FormConfiguration.Type type;
    private String value;
    private Collection<UserReferenceBean> userReferences;
    private boolean searchable;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public FormConfiguration.Type getType() {
        return type;
    }

    public void setType(FormConfiguration.Type type) {
        this.type = type;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public Collection<UserReferenceBean> getUserReferences() {
        return userReferences;
    }

    public void setUserReferences(Collection<UserReferenceBean> userReferences) {
        this.userReferences = userReferences;
    }

    public boolean isSearchable() {
        return searchable;
    }

    public void setSearchable(boolean searchable) {
        this.searchable = searchable;
    }


}
