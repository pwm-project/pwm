package password.pwm.http.bean;

import lombok.Getter;

import java.io.Serializable;
import java.util.List;

@Getter
public class DisplayElement implements Serializable
{
    private String key;
    private Type type;
    private String label;
    private String value;
    private List<String> values;

    public enum Type
    {
        string,
        timestamp,
        number,
        multiString,
    }

    public DisplayElement( final String key, final Type type, final String label, final String value )
    {
        this.key = key;
        this.type = type;
        this.label = label;
        this.value = value;
    }

    public DisplayElement( final String key, final Type type, final String label, final List<String> values )
    {
        this.key = key;
        this.type = type;
        this.label = label;
        this.values = values;
    }
}
