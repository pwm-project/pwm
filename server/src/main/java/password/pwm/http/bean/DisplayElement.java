package password.pwm.http.bean;

import lombok.Value;

import java.io.Serializable;

@Value
public class DisplayElement implements Serializable {
    private String key;
    private Type type;
    private String label;
    private String value;

    public enum Type {
        string,
        timestamp,
        number,
    }
}
