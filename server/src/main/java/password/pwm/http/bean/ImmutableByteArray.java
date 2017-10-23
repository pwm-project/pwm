package password.pwm.http.bean;

import java.io.Serializable;
import java.util.Arrays;

public class ImmutableByteArray implements Serializable {
    private final byte[] bytes;

    public ImmutableByteArray(final byte[] bytes) {
        this.bytes = bytes == null ? null : Arrays.copyOf(bytes, bytes.length);
    }

    public byte[] getBytes() {
        return bytes == null ? null : Arrays.copyOf(bytes, bytes.length);
    }
}
