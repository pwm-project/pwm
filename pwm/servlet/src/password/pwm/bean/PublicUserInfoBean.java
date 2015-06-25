package password.pwm.bean;

import password.pwm.config.Configuration;
import password.pwm.config.profile.PwmPasswordRule;
import password.pwm.http.tag.PasswordRequirementsTag;

import java.io.Serializable;
import java.util.*;

public class PublicUserInfoBean implements Serializable {
    public String userDN;
    public String ldapProfile;
    public String userID;
    public String userEmailAddress;
    public Date passwordExpirationTime;
    public Date passwordLastModifiedTime;
    public boolean requiresNewPassword;
    public boolean requiresResponseConfig;
    public boolean requiresUpdateProfile;
    public boolean requiresInteraction;

    public PasswordStatus passwordStatus;
    public Map<String, String> passwordPolicy;
    public List<String> passwordRules;
    public Map<String, String> attributes;

    public static PublicUserInfoBean fromUserInfoBean(final UserInfoBean userInfoBean, final Configuration config, final Locale locale) {
        final PublicUserInfoBean publicUserInfoBean = new PublicUserInfoBean();
        publicUserInfoBean.userDN = (userInfoBean.getUserIdentity() == null) ? "" : userInfoBean.getUserIdentity().getUserDN();
        publicUserInfoBean.ldapProfile = (userInfoBean.getUserIdentity() == null) ? "" : userInfoBean.getUserIdentity().getLdapProfileID();
        publicUserInfoBean.userID = userInfoBean.getUsername();
        publicUserInfoBean.userEmailAddress = userInfoBean.getUserEmailAddress();
        publicUserInfoBean.passwordExpirationTime = userInfoBean.getPasswordExpirationTime();
        publicUserInfoBean.passwordLastModifiedTime = userInfoBean.getPasswordLastModifiedTime();
        publicUserInfoBean.passwordStatus = userInfoBean.getPasswordState();

        publicUserInfoBean.requiresNewPassword = userInfoBean.isRequiresNewPassword();
        publicUserInfoBean.requiresResponseConfig = userInfoBean.isRequiresResponseConfig();
        publicUserInfoBean.requiresUpdateProfile = userInfoBean.isRequiresResponseConfig();
        publicUserInfoBean.requiresInteraction = userInfoBean.isRequiresNewPassword()
                || userInfoBean.isRequiresResponseConfig()
                || userInfoBean.isRequiresUpdateProfile()
                || userInfoBean.getPasswordState().isWarnPeriod();


        publicUserInfoBean.passwordPolicy = new HashMap<>();
        for (final PwmPasswordRule rule : PwmPasswordRule.values()) {
            publicUserInfoBean.passwordPolicy.put(rule.name(), userInfoBean.getPasswordPolicy().getValue(rule));
        }

        publicUserInfoBean.passwordRules = PasswordRequirementsTag.getPasswordRequirementsStrings(
                userInfoBean.getPasswordPolicy(),
                config,
                locale
        );

        if (userInfoBean.getCachedAttributeValues() != null && !userInfoBean.getCachedAttributeValues().isEmpty()) {
            publicUserInfoBean.attributes = Collections.unmodifiableMap(userInfoBean.getCachedAttributeValues());
        }

        return publicUserInfoBean;
    }
}
