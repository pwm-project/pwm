package password.pwm.bean;

import lombok.AllArgsConstructor;
import lombok.Getter;
import password.pwm.config.Configuration;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.ldap.UserInfo;
import password.pwm.util.ValueObfuscator;
import password.pwm.util.java.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Getter
@AllArgsConstructor
public class TokenDestinationItem {
    private String id;
    private String display;
    private String value;
    private Type type;

    public enum Type {
        sms,
        email,
    }

    public static List<TokenDestinationItem> allFromConfig(final Configuration configuration, final UserInfo userInfo)
            throws PwmUnrecoverableException
    {
        final ValueObfuscator valueObfuscator = new ValueObfuscator(configuration);
        int counter = 0;

        final List<TokenDestinationItem> results = new ArrayList<>();

        {
            final String smsValue = userInfo.getUserSmsNumber();
            if (!StringUtil.isEmpty(smsValue)) {
                results.add(new TokenDestinationItem(
                        String.valueOf(++counter),
                        valueObfuscator.maskPhone(smsValue),
                        smsValue,
                        Type.sms
                ));
            }
        }

        {
            final String emailValue = userInfo.getUserEmailAddress();
            if (!StringUtil.isEmpty(emailValue)) {
                results.add(new TokenDestinationItem(
                        String.valueOf(++counter),
                        valueObfuscator.maskEmail(emailValue),
                        emailValue,
                        Type.email
                ));
            }
        }

        return Collections.unmodifiableList(results);
    }
}
