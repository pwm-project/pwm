package password.pwm.bean;

import java.io.Serializable;
import java.util.Date;

public class FormNonce implements Serializable {
    String sessionGUID;
    Date timestap;
    int reqCounter;

    public FormNonce(String sessionGUID, Date timestap,  int reqCounter) {
        this.sessionGUID = sessionGUID;
        this.timestap = timestap;
        this.reqCounter = reqCounter;
    }

    public String getSessionGUID() {
        return sessionGUID;
    }

    public Date getTimestap() {
        return timestap;
    }

    public int getRequestID() {
        return reqCounter;
    }

}
