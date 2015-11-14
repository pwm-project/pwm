package password.pwm.config.stored;

import java.io.Serializable;

class StoredConfigReferenceBean implements StoredConfigReference, Serializable, Comparable {
    private RecordType recordType;
    private String recordID;
    private String profileID;

    StoredConfigReferenceBean(RecordType type, String recordID, String profileID) {
        if (type == null) {
            throw new NullPointerException("recordType can not be null");
        }

        if (recordID == null) {
            throw new NullPointerException("recordID can not be null");
        }

        this.recordType = type;
        this.recordID = recordID;
        this.profileID = profileID;
    }

    public RecordType getRecordType() {
        return recordType;
    }

    public String getRecordID() {
        return recordID;
    }

    @Override
    public String getProfileID() {
        return profileID;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof StoredConfigReference && toString().equals(o);

    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    @Override
    public String toString() {
        return this.getRecordType().toString()
                + "-"
                + (this.getProfileID() == null ? "" : this.getProfileID())
                + "-"
                + this.getRecordID();
    }

    @Override
    public int compareTo(Object o) {
        return toString().compareTo(o.toString());
    }
}
