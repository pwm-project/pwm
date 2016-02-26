package password.pwm.http.servlet.helpdesk;

import password.pwm.bean.UserIdentity;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

public class HelpdeskVerificationStateBean implements Serializable {
    private UserIdentity actor;
    private List<HelpdeskValidationRecord> records;

    public HelpdeskVerificationStateBean(UserIdentity actor, List<HelpdeskValidationRecord> records) {
        this.actor = actor;
        this.records = records;
    }

    public UserIdentity getActor() {
        return actor;
    }

    public List<HelpdeskValidationRecord> getRecords() {
        return records;
    }

    static class HelpdeskValidationRecord {
        private Date timestamp;
        private UserIdentity identity;

        public HelpdeskValidationRecord(Date timestamp, UserIdentity identity) {
            this.timestamp = timestamp;
            this.identity = identity;
        }

        public Date getTimestamp() {
            return timestamp;
        }

        public UserIdentity getIdentity() {
            return identity;
        }
    }
}
