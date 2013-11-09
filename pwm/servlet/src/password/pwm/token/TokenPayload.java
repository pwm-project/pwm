package password.pwm.token;

import java.io.Serializable;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.Set;

public class TokenPayload implements Serializable {
    private final java.util.Date date;
    private final String name;
    private final Map<String,String> data;
    private final String dn;
    private final Set<String> dest;
    private final String guid;

    TokenPayload(final String name, final Map<String, String> data, final String dn, final Set<String> dest, final String guid) {
        this.date = new Date();
        this.data = data == null ? Collections.<String,String>emptyMap() : Collections.unmodifiableMap(data);
        this.name = name;
        this.dn = dn;
        this.dest = dest == null ? Collections.<String>emptySet() : Collections.unmodifiableSet(dest);
        this.guid = guid;
    }

    public Date getDate() {
        return date;
    }

    public String getName() {
        return name;
    }

    public Map<String, String> getData() {
        return data;
    }

    public String getDN() {
        return dn;
    }

    public Set<String> getDest() {
        return dest;
    }

    public String getGuid() {
        return guid;
    }
}
