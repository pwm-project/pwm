package password.pwm.config.stored;

import java.io.Serializable;

class StoredConfigReferenceBean implements StoredConfigReference, Serializable {
    private final StoredConfigReference.Type type;
    private final String key;
    private final String profileID;

    StoredConfigReferenceBean(Type type, String key, String profileID) {
        if (type == null) {
            throw new NullPointerException("type can not be null");
        }

        if (key == null) {
            throw new NullPointerException("key can not be null");
        }

        this.type = type;
        this.key = key;
        this.profileID = profileID;
    }

    @Override
    public Type getType() {
        return type;
    }

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public String getProfileID() {
        return profileID;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        StoredConfigReferenceBean that = (StoredConfigReferenceBean) o;

        if (type != that.type) return false;
        if (!key.equals(that.key)) return false;
        return !(profileID != null ? !profileID.equals(that.profileID) : that.profileID != null);

    }

    @Override
    public int hashCode() {
        int result = type.hashCode();
        result = 31 * result + key.hashCode();
        result = 31 * result + (profileID != null ? profileID.hashCode() : 0);
        return result;
    }
}
