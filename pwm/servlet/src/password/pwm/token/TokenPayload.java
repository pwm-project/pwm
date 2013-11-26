package password.pwm.token;

import password.pwm.bean.UserIdentity;

import java.io.Serializable;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.Set;

public class TokenPayload implements Serializable {
    private final java.util.Date date;
    private final String name;
    private final Map<String,String> data;
    private final UserIdentity user;
    private final Set<String> dest;
    private final String guid;

    TokenPayload(final String name, final Map<String, String> data, final UserIdentity user, final Set<String> dest, final String guid) {
        this.date = new Date();
        this.data = data == null ? Collections.<String,String>emptyMap() : Collections.unmodifiableMap(data);
        this.name = name;
        this.user = user;
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

    public UserIdentity getUserIdentity() {
        return user;
    }

    public Set<String> getDest() {
        return dest;
    }

    public String getGuid() {
        return guid;
    }
}
