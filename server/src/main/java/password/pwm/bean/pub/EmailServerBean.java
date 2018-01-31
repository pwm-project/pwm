package password.pwm.bean.pub;

import lombok.AllArgsConstructor;
import lombok.Getter;
import password.pwm.util.PasswordData;

@Getter
@AllArgsConstructor
public class EmailServerBean {
    private final String serverAddress;
    private final int port;
    private final String defaultFrom;
    private final String username;
    private final PasswordData password;
    private boolean tried;

    public String toDebugString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("ServerAddress: ").append(serverAddress).append(", port: ").append(port).append(", Username: ").append(username);
        return sb.toString();
    }

    public void setTried(final boolean newTried) {
        tried = newTried;
    }
}

