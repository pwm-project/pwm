package password.pwm.http.servlet.accountinfo;

import lombok.Builder;
import lombok.Value;
import password.pwm.PwmApplication;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.ViewStatusFields;
import password.pwm.config.profile.PwmPasswordPolicy;
import password.pwm.config.value.data.FormConfiguration;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.http.bean.DisplayElement;
import password.pwm.http.tag.PasswordRequirementsTag;
import password.pwm.i18n.Display;
import password.pwm.ldap.UserInfo;
import password.pwm.svc.event.UserAuditRecord;
import password.pwm.util.LocaleHelper;
import password.pwm.util.form.FormUtility;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Value
@Builder
public class AccountInformationBean implements Serializable {
    private static final PwmLogger LOGGER = PwmLogger.forClass(AccountInformationBean.class);

    @Value
    public static class ActivityRecord implements Serializable {
        private Instant timestamp;
        private String label;
    }

    private List<DisplayElement> accountInfo;
    private List<DisplayElement> formData;
    private List<ActivityRecord> auditData;
    private List<String> passwordRules;

    static AccountInformationBean makeUserAccountInfoBean(
            final PwmRequest pwmRequest,
            final UserInfo userInfo,
            final Locale locale

    )
            throws PwmUnrecoverableException
    {
        final Instant startTime = Instant.now();
        final AccountInformationBeanBuilder builder = new AccountInformationBean.AccountInformationBeanBuilder();

        builder.accountInfo(makeAccountInfo(pwmRequest, userInfo, locale));
        builder.formData(makeFormInfo(pwmRequest, locale));
        builder.auditData(makeAuditInfo(pwmRequest));
        builder.passwordRules(makePasswordRules(pwmRequest));

        LOGGER.trace(pwmRequest, "generated account information bean in " + TimeDuration.compactFromCurrent(startTime));
        return builder.build();
    }

    private static List<String> makePasswordRules(
            final PwmRequest pwmRequest)
            throws PwmUnrecoverableException
    {
        final PwmPasswordPolicy pwmPasswordPolicy = pwmRequest.getPwmSession().getUserInfo().getPasswordPolicy();
        final MacroMachine macroMachine = pwmRequest.getPwmSession().getSessionManager().getMacroMachine(pwmRequest.getPwmApplication());
        final List<String> rules = PasswordRequirementsTag.getPasswordRequirementsStrings(pwmPasswordPolicy, pwmRequest.getConfig(), pwmRequest.getLocale(), macroMachine);
        return Collections.unmodifiableList(rules);
    }

    private static List<ActivityRecord> makeAuditInfo(
            final PwmRequest pwmRequest
    ) {

        if (!pwmRequest.getConfig().readSettingAsBoolean(PwmSetting.ACCOUNT_INFORMATION_HISTORY)) {
            return Collections.emptyList();
        }

        final List<UserAuditRecord> auditRecords = new ArrayList<>();
        try {
            auditRecords.addAll(pwmRequest.getPwmApplication().getAuditManager().readUserHistory(pwmRequest.getPwmSession()));
        } catch (PwmUnrecoverableException e) {
            LOGGER.debug(pwmRequest, "error reading audit data for user: " + e.getMessage());
        }

        final List<ActivityRecord> returnData  = new ArrayList<>();
        for (final UserAuditRecord userAuditRecord : auditRecords) {
            returnData.add(new ActivityRecord(
                    userAuditRecord.getTimestamp(),
                    userAuditRecord.getEventCode().getLocalizedString(pwmRequest.getConfig(), pwmRequest.getLocale())
            ));
        }

        return Collections.unmodifiableList(returnData);
    }

    private static List<DisplayElement> makeFormInfo(
            final PwmRequest pwmRequest,
            final Locale locale
    )
            throws PwmUnrecoverableException
    {
        final List<DisplayElement> returnData  = new ArrayList<>();

        final List<FormConfiguration> formConfiguration = pwmRequest.getConfig().readSettingAsForm(PwmSetting.ACCOUNT_INFORMATION_VIEW_FORM);
        if (formConfiguration != null && !formConfiguration.isEmpty()) {
            final Map<FormConfiguration, List<String>> ldapValues = FormUtility.populateFormMapFromLdap(
                    formConfiguration,
                    pwmRequest.getSessionLabel(),
                    pwmRequest.getPwmSession().getUserInfo(),
                    FormUtility.Flag.ReturnEmptyValues
            );
            for (final Map.Entry<FormConfiguration, List<String>> entry : ldapValues.entrySet()) {
                final FormConfiguration formConfig = entry.getKey();
                final List<String> values = entry.getValue();

                final String display = formConfig.isMultivalue()
                        ? StringUtil.collectionToString(values, ", ")
                        : values.isEmpty() ? "" : values.iterator().next();

                returnData.add(new DisplayElement(
                        formConfig.getName(),
                        DisplayElement.Type.string,
                        formConfig.getLabel(locale),
                        display
                ));
            }
        }

        return Collections.unmodifiableList(returnData);
    }


    private static List<DisplayElement> makeAccountInfo(
            final PwmRequest pwmRequest,
            final UserInfo userInfo,
            final Locale locale
    )
            throws PwmUnrecoverableException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final List<DisplayElement> accountInfo = new ArrayList<>();
        final DataElementMaker maker = new DataElementMaker(pwmApplication, locale, accountInfo);

        maker.add(
                ViewStatusFields.Username,
                Display.Field_Username,
                userInfo.getUsername()
        );

        maker.add(
                ViewStatusFields.UserDN,
                Display.Field_UserDN,
                userInfo.getUserIdentity().getUserDN()
        );

        if (pwmApplication.getConfig().getLdapProfiles().size() > 1) {
            final String ldapProfileID = userInfo.getUserIdentity().getLdapProfileID();
            final String value = pwmApplication.getConfig().getLdapProfiles().get(ldapProfileID).getDisplayName(locale);
            maker.add(
                    ViewStatusFields.UserDN,
                    Display.Field_LdapProfile,
                    value
            );
        }

        maker.add(
                ViewStatusFields.UserEmail,
                Display.Field_UserEmail,
                userInfo.getUserEmailAddress()
        );

        maker.add(
                ViewStatusFields.UserSMS,
                Display.Field_UserSMS,
                userInfo.getUserSmsNumber()
        );

        maker.add(
                ViewStatusFields.GUID,
                Display.Field_UserGUID,
                userInfo.getUserGuid()
        );

        maker.add(
                ViewStatusFields.AccountExpirationTime,
                Display.Field_AccountExpirationTime,
                userInfo.getAccountExpirationTime()
        );

        maker.add(
                ViewStatusFields.PasswordExpired,
                Display.Field_PasswordExpired,
                userInfo.getPasswordStatus().isExpired()
        );

        maker.add(
                ViewStatusFields.PasswordPreExpired,
                Display.Field_PasswordPreExpired,
                userInfo.getPasswordStatus().isPreExpired()
        );

        maker.add(
                ViewStatusFields.PasswordWarnPeriod,
                Display.Field_PasswordWithinWarningPeriod,
                userInfo.getPasswordStatus().isWarnPeriod()
        );

        maker.add(
                ViewStatusFields.PasswordViolatesPolicy,
                Display.Field_PasswordViolatesPolicy,
                userInfo.getPasswordStatus().isViolatesPolicy()
        );

        maker.add(
                ViewStatusFields.PasswordSetTime,
                Display.Field_PasswordSetTime,
                userInfo.getPasswordLastModifiedTime()
        );

        {
            final String value = userInfo.getPasswordLastModifiedTime() != null
                    ? TimeDuration.fromCurrent(userInfo.getPasswordLastModifiedTime()).asLongString(locale)
                    : LocaleHelper.getLocalizedMessage(locale, Display.Value_NotApplicable, pwmApplication.getConfig());
            maker.add(
                    ViewStatusFields.PasswordSetTimeDelta,
                    Display.Field_PasswordSetTimeDelta,
                    value
            );
        }

        maker.add(
                ViewStatusFields.PasswordExpireTime,
                Display.Field_PasswordExpirationTime,
                userInfo.getPasswordExpirationTime()
        );

        maker.add(
                ViewStatusFields.ResponsesStored,
                Display.Field_ResponsesStored,
                userInfo.getResponseInfoBean() != null
        );

        maker.add(
                ViewStatusFields.ResponsesStored,
                Display.Field_ResponsesStored,
                userInfo.getResponseInfoBean() != null
        );

        if (userInfo.getResponseInfoBean() != null) {
            maker.add(
                    ViewStatusFields.ResponsesTimestamp,
                    Display.Field_ResponsesTimestamp,
                    userInfo.getResponseInfoBean().getTimestamp()
            );
        }

        if (pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.OTP_ENABLED)) {
            maker.add(
                    ViewStatusFields.OTPStored,
                    Display.Field_OTP_Stored,
                    userInfo.getOtpUserRecord() != null
            );

            if (userInfo.getOtpUserRecord() != null) {
                maker.add(
                        ViewStatusFields.OTPTimestamp,
                        Display.Field_OTP_Timestamp,
                        userInfo.getOtpUserRecord().getTimestamp()
                );
            }
        }

        maker.add(
                ViewStatusFields.NetworkAddress,
                Display.Field_NetworkAddress,
                pwmRequest.getPwmSession().getSessionStateBean().getSrcAddress()
        );

        maker.add(
                ViewStatusFields.NetworkHost,
                Display.Field_NetworkHost,
                pwmRequest.getPwmSession().getSessionStateBean().getSrcHostname()
        );

        maker.add(
                ViewStatusFields.LogoutURL,
                Display.Field_LogoutURL,
                pwmRequest.getLogoutURL()
        );

        maker.add(
                ViewStatusFields.ForwardURL,
                Display.Field_ForwardURL,
                pwmRequest.getForwardUrl()
        );

        return Collections.unmodifiableList(accountInfo);
    }

    private static class DataElementMaker {
        private final PwmApplication pwmApplication;
        private final Locale locale;
        private final List<DisplayElement> list;

        DataElementMaker(final PwmApplication pwmApplication, final Locale locale, final List<DisplayElement> list) {
            this.pwmApplication = pwmApplication;
            this.locale = locale;
            this.list = list;
        }

        void add(final ViewStatusFields viewStatusField, final Display display, final Instant instant) {
            final Set<ViewStatusFields> viewStatusFields = pwmApplication.getConfig().readSettingAsOptionList(PwmSetting.ACCOUNT_INFORMATION_VIEW_STATUS_VALUES,ViewStatusFields.class);

            if (!viewStatusFields.contains(viewStatusField)) {
                return;
            }

            final String strValue = instant == null
                    ? LocaleHelper.getLocalizedMessage(locale, Display.Value_NotApplicable, pwmApplication.getConfig())
                    : JavaHelper.toIsoDate(instant);

            list.add(new DisplayElement(
                    display.name(),
                    DisplayElement.Type.timestamp,
                    LocaleHelper.getLocalizedMessage(locale, display, pwmApplication.getConfig()),
                    strValue
            ));
        }

        void add(final ViewStatusFields viewStatusField, final Display display, final boolean value) {
            add(viewStatusField, display, LocaleHelper.booleanString(
                    value,
                    locale,
                    pwmApplication.getConfig()
            ));
        }

        void add(final ViewStatusFields viewStatusField, final Display display, final String value) {
            final Set<ViewStatusFields> viewStatusFields = pwmApplication.getConfig().readSettingAsOptionList(PwmSetting.ACCOUNT_INFORMATION_VIEW_STATUS_VALUES,ViewStatusFields.class);

            if (!viewStatusFields.contains(viewStatusField)) {
                return;
            }

            final String strValue = StringUtil.isEmpty(value)
                    ? LocaleHelper.getLocalizedMessage(locale, Display.Value_NotApplicable, pwmApplication.getConfig())
                    : value;

            list.add(new DisplayElement(
                    display.name(),
                    DisplayElement.Type.string,
                    LocaleHelper.getLocalizedMessage(locale, display, pwmApplication.getConfig()),
                    strValue
            ));
        }
    }
}
