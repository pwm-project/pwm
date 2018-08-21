/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package password.pwm.http.servlet.accountinfo;

import lombok.Builder;
import lombok.Value;
import password.pwm.PwmApplication;
import password.pwm.bean.SessionLabel;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.ViewStatusFields;
import password.pwm.config.profile.PwmPasswordPolicy;
import password.pwm.config.value.data.FormConfiguration;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.http.bean.DisplayElement;
import password.pwm.http.tag.PasswordRequirementsTag;
import password.pwm.ldap.UserInfo;
import password.pwm.ldap.ViewableUserInfoDisplayReader;
import password.pwm.svc.event.UserAuditRecord;
import password.pwm.util.form.FormUtility;
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

@Value
@Builder
public class AccountInformationBean implements Serializable
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( AccountInformationBean.class );

    @Value
    public static class ActivityRecord implements Serializable
    {
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

        builder.accountInfo( ViewableUserInfoDisplayReader.makeDisplayData(
                pwmRequest.getConfig().readSettingAsOptionList( PwmSetting.ACCOUNT_INFORMATION_VIEW_STATUS_VALUES, ViewStatusFields.class ),
                pwmRequest.getConfig(),
                userInfo,
                pwmRequest.getPwmSession().getSessionStateBean(),
                locale
        ) );
        builder.formData( makeFormInfo( pwmRequest, locale ) );
        builder.auditData( makeAuditInfo(
                pwmRequest.getPwmApplication(),
                pwmRequest.getSessionLabel(),
                userInfo,
                pwmRequest.getLocale()
        ) );
        builder.passwordRules( makePasswordRules( pwmRequest ) );

        LOGGER.trace( pwmRequest, "generated account information bean in " + TimeDuration.compactFromCurrent( startTime ) );
        return builder.build();
    }

    private static List<String> makePasswordRules(
            final PwmRequest pwmRequest )
            throws PwmUnrecoverableException
    {
        final PwmPasswordPolicy pwmPasswordPolicy = pwmRequest.getPwmSession().getUserInfo().getPasswordPolicy();
        final MacroMachine macroMachine = pwmRequest.getPwmSession().getSessionManager().getMacroMachine( pwmRequest.getPwmApplication() );
        final List<String> rules = PasswordRequirementsTag.getPasswordRequirementsStrings( pwmPasswordPolicy, pwmRequest.getConfig(), pwmRequest.getLocale(), macroMachine );
        return Collections.unmodifiableList( rules );
    }

    public static List<ActivityRecord> makeAuditInfo(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final UserInfo userInfo,
            final Locale locale
    )
    {

        if ( !pwmApplication.getConfig().readSettingAsBoolean( PwmSetting.ACCOUNT_INFORMATION_HISTORY ) )
        {
            return Collections.emptyList();
        }

        final List<UserAuditRecord> auditRecords = new ArrayList<>();
        try
        {
            auditRecords.addAll( pwmApplication.getAuditManager().readUserHistory( userInfo ) );
        }
        catch ( PwmUnrecoverableException e )
        {
            LOGGER.debug( sessionLabel, "error reading audit data for user: " + e.getMessage() );
        }

        final List<ActivityRecord> returnData = new ArrayList<>();
        for ( final UserAuditRecord userAuditRecord : auditRecords )
        {
            returnData.add( new ActivityRecord(
                    userAuditRecord.getTimestamp(),
                    userAuditRecord.getEventCode().getLocalizedString( pwmApplication.getConfig(), locale )
            ) );
        }

        return Collections.unmodifiableList( returnData );
    }

    private static List<DisplayElement> makeFormInfo(
            final PwmRequest pwmRequest,
            final Locale locale
    )
            throws PwmUnrecoverableException
    {
        final List<DisplayElement> returnData = new ArrayList<>();

        final List<FormConfiguration> formConfiguration = pwmRequest.getConfig().readSettingAsForm( PwmSetting.ACCOUNT_INFORMATION_VIEW_FORM );
        if ( formConfiguration != null && !formConfiguration.isEmpty() )
        {
            final Map<FormConfiguration, List<String>> ldapValues = FormUtility.populateFormMapFromLdap(
                    formConfiguration,
                    pwmRequest.getSessionLabel(),
                    pwmRequest.getPwmSession().getUserInfo(),
                    FormUtility.Flag.ReturnEmptyValues
            );
            for ( final Map.Entry<FormConfiguration, List<String>> entry : ldapValues.entrySet() )
            {
                final FormConfiguration formConfig = entry.getKey();
                final List<String> values = entry.getValue();

                final String display = formConfig.isMultivalue()
                        ? StringUtil.collectionToString( values, ", " )
                        : values.isEmpty() ? "" : values.iterator().next();

                returnData.add( new DisplayElement(
                        formConfig.getName(),
                        DisplayElement.Type.string,
                        formConfig.getLabel( locale ),
                        display
                ) );
            }
        }

        return Collections.unmodifiableList( returnData );
    }
}
