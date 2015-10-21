package password.pwm.svc.wordlist;

import java.io.Serializable;
import java.util.Date;

public class StoredWordlistDataBean implements Serializable {
    private boolean completed;
    private boolean builtin;
    private Date storeDate;
    private String sha1hash;
    private int size;

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public boolean isBuiltin() {
        return builtin;
    }

    public void setBuiltin(boolean builtin) {
        this.builtin = builtin;
    }

    public Date getStoreDate() {
        return storeDate;
    }

    public void setStoreDate(Date storeDate) {
        this.storeDate = storeDate;
    }

    public String getSha1hash() {
        return sha1hash;
    }

    public void setSha1hash(String sha1hash) {
        this.sha1hash = sha1hash;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }
}
