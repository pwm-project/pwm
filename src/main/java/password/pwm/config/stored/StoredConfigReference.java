package password.pwm.config.stored;

public interface StoredConfigReference {
    Type getType();
    String getKey();
    String getProfileID();

    enum Type {
        Setting;
    }
}
