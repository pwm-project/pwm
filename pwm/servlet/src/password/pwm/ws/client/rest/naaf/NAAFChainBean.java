package password.pwm.ws.client.rest.naaf;

import java.io.Serializable;
import java.util.List;

class NAAFChainBean implements Serializable {
    private String name;
    private boolean is_trusted;
    private String short_name;
    private List<String> methods;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean is_trusted() {
        return is_trusted;
    }

    public void setIs_trusted(boolean is_trusted) {
        this.is_trusted = is_trusted;
    }

    public String getShort_name() {
        return short_name;
    }

    public void setShort_name(String short_name) {
        this.short_name = short_name;
    }

    public List<String> getMethods() {
        return methods;
    }

    public void setMethods(List<String> methods) {
        this.methods = methods;
    }
}
