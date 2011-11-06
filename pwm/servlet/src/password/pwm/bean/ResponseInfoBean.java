package password.pwm.bean;

import com.novell.ldapchai.cr.Challenge;

import java.util.Locale;
import java.util.Map;

public class ResponseInfoBean {
    final private Map<Challenge,String> crMap;
    final private Locale locale;
    final int minRandoms;
    final String csIdentifier;

    public ResponseInfoBean(Map<Challenge, String> crMap, Locale locale, int minRandoms, String csIdentifier) {
        this.crMap = crMap;
        this.locale = locale;
        this.minRandoms = minRandoms;
        this.csIdentifier = csIdentifier;
    }

    public Map<Challenge, String> getCrMap() {
        return crMap;
    }

    public Locale getLocale() {
        return locale;
    }

    public int getMinRandoms() {
        return minRandoms;
    }

    public String getCsIdentifier() {
        return csIdentifier;
    }
}
