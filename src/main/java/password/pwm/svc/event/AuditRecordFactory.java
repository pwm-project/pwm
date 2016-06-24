package password.pwm.svc.event;

import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.bean.UserInfoBean;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmSession;
import password.pwm.i18n.PwmDisplayBundle;
import password.pwm.ldap.LdapOperationsHelper;
import password.pwm.util.JsonUtil;
import password.pwm.util.LocaleHelper;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;

import java.util.Date;
import java.util.Map;

public class AuditRecordFactory {
    private final static PwmLogger LOGGER = PwmLogger.forClass(AuditRecordFactory.class);

    private final PwmApplication pwmApplication;
    private final MacroMachine macroMachine;

    public AuditRecordFactory(PwmApplication pwmApplication) throws PwmUnrecoverableException {
        this.pwmApplication = pwmApplication;
        this.macroMachine = MacroMachine.forNonUserSpecific(pwmApplication, null);
    }

    public AuditRecordFactory(final PwmApplication pwmApplication, final MacroMachine macroMachine) {
        this.pwmApplication = pwmApplication;
        this.macroMachine = macroMachine;
    }

    public AuditRecordFactory(final PwmApplication pwmApplication, final PwmSession pwmSession) throws PwmUnrecoverableException {
        this.pwmApplication = pwmApplication;
        this.macroMachine = pwmSession.getSessionManager().getMacroMachine(pwmApplication);
    }
    public AuditRecordFactory(final PwmRequest pwmRequest) throws PwmUnrecoverableException {
        this.pwmApplication = pwmRequest.getPwmApplication();
        this.macroMachine = pwmRequest.getPwmSession().getSessionManager().getMacroMachine(pwmApplication);
    }

    public HelpdeskAuditRecord createHelpdeskAuditRecord(
            final AuditEvent eventCode,
            final UserIdentity perpetrator,
            final String message,
            final UserIdentity target,
            final String sourceAddress,
            final String sourceHost
    )
    {
        String perpUserDN = null, perpUserID = null, perpLdapProfile = null, targetUserDN = null, targetUserID = null, targetLdapProfile = null;
        if (perpetrator != null) {
            perpUserDN = perpetrator.getUserDN();
            perpLdapProfile = perpetrator.getLdapProfileID();
            try {
                perpUserID = LdapOperationsHelper.readLdapUsernameValue(pwmApplication,perpetrator);
            } catch (Exception e) {
                LOGGER.error("unable to read userID for " + perpetrator + ", error: " + e.getMessage());
            }
        }
        if (target != null) {
            targetUserDN = target.getUserDN();
            targetLdapProfile = target.getLdapProfileID();
            try {
                targetUserID = LdapOperationsHelper.readLdapUsernameValue(pwmApplication,target);
            } catch (Exception e) {
                LOGGER.error("unable to read userID for " + perpetrator + ", error: " + e.getMessage());
            }
        }

        final HelpdeskAuditRecord record = new HelpdeskAuditRecord(new Date(), eventCode, perpUserID, perpUserDN, perpLdapProfile, message, targetUserID, targetUserDN,
                targetLdapProfile, sourceAddress, sourceHost);
        record.narrative = makeNarrativeString(record);
        return record;
    }

    public UserAuditRecord createUserAuditRecord(
            final AuditEvent eventCode,
            final UserIdentity perpetrator,
            final String message,
            final String sourceAddress,
            final String sourceHost
    )
    {
        String perpUserDN = null, perpUserID = null, perpLdapProfile = null;
        if (perpetrator != null) {
            perpUserDN = perpetrator.getUserDN();
            perpLdapProfile = perpetrator.getLdapProfileID();
            try {
                perpUserID = LdapOperationsHelper.readLdapUsernameValue(pwmApplication,perpetrator);
            } catch (Exception e) {
                LOGGER.error("unable to read userID for " + perpetrator + ", error: " + e.getMessage());
            }
        }

        final UserAuditRecord record = new UserAuditRecord(new Date(), eventCode, perpUserID, perpUserDN, perpLdapProfile, message, sourceAddress, sourceHost);
        record.narrative = this.makeNarrativeString(record);
        return record;
    }

    public SystemAuditRecord createSystemAuditRecord(
            final AuditEvent eventCode,
            final String message
    )
    {
        final SystemAuditRecord record = new SystemAuditRecord(eventCode, message, pwmApplication.getInstanceID());
        record.narrative = this.makeNarrativeString(record);
        return record;
    }

    public UserAuditRecord createUserAuditRecord(
            final AuditEvent eventCode,
            final UserIdentity perpetrator,
            final SessionLabel sessionLabel
    )
    {
        return createUserAuditRecord(
                eventCode,
                perpetrator,
                sessionLabel,
                null
        );
    }

    public UserAuditRecord createUserAuditRecord(
            final AuditEvent eventCode,
            final UserIdentity perpetrator,
            final SessionLabel sessionLabel,
            final String message
    )
    {
        return createUserAuditRecord(
                eventCode,
                perpetrator,
                message,
                sessionLabel != null ? sessionLabel.getSrcAddress() : null,
                sessionLabel != null ? sessionLabel.getSrcHostname() : null
        );
    }

    public UserAuditRecord createUserAuditRecord(
            final AuditEvent eventCode,
            final UserInfoBean userInfoBean,
            final PwmSession pwmSession
    )
    {
        return createUserAuditRecord(
                eventCode,
                userInfoBean.getUserIdentity(),
                null,
                pwmSession.getSessionStateBean().getSrcAddress(),
                pwmSession.getSessionStateBean().getSrcHostname()
        );
    }


    private String makeNarrativeString(AuditRecord auditRecord) {
        final PwmDisplayBundle pwmDisplayBundle = auditRecord.getEventCode().getNarrative();

        String outputString = LocaleHelper.getLocalizedMessage(PwmConstants.DEFAULT_LOCALE, pwmDisplayBundle, pwmApplication.getConfig());

        if (macroMachine != null) {
            outputString = macroMachine.expandMacros(outputString);
        }

        final Map<String,String> recordFields = JsonUtil.deserializeStringMap(JsonUtil.serialize(auditRecord));
        for (final String key : recordFields.keySet()) {
            final String value = recordFields.get(key);
            final String parametrizedKey = "%" + key + "%";
            outputString = outputString.replace(parametrizedKey, value);
        }

        return outputString;
    }
}
