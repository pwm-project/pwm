package password.pwm.bean;

import java.io.Serializable;
import java.util.Date;

public class FormNonce implements Serializable {
    String sessionGUID;
    Date timestamp;
    int reqCounter;

    public FormNonce(String sessionGUID, Date timestamp,  int reqCounter) {
        this.sessionGUID = sessionGUID;
        this.timestamp = timestamp;
        this.reqCounter = reqCounter;
    }

    public String getSessionGUID() {
        return sessionGUID;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public int getRequestID() {
        return reqCounter;
    }

}
