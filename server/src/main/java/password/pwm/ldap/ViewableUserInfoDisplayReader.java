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

package password.pwm.ldap;

import password.pwm.bean.LocalSessionStateBean;
import password.pwm.bean.ResponseInfoBean;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.ViewStatusFields;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.bean.DisplayElement;
import password.pwm.i18n.Display;
import password.pwm.util.LocaleHelper;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ViewableUserInfoDisplayReader
{
    private ViewableUserInfoDisplayReader( )
    {
    }

    @SuppressWarnings( "checkstyle:MethodLength" )
    public static List<DisplayElement> makeDisplayData(
            final Set<ViewStatusFields> viewStatusFields,
            final Configuration config,
            final UserInfo userInfo,
            final LocalSessionStateBean localSessionStateBean,
            final Locale locale
    )
            throws PwmUnrecoverableException
    {
        final List<DisplayElement> accountInfo = new ArrayList<>();
        final DataElementMaker maker = new DataElementMaker( viewStatusFields, config, locale, accountInfo );

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

        if ( config.getLdapProfiles().size() > 1 )
        {
            final String ldapProfileID = userInfo.getUserIdentity().getLdapProfileID();
            final String value = config.getLdapProfiles().get( ldapProfileID ).getDisplayName( locale );
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
                ViewStatusFields.AccountEnabled,
                Display.Field_AccountEnabled,
                userInfo.isAccountEnabled()
        );

        maker.add(
                ViewStatusFields.AccountExpired,
                Display.Field_AccountExpired,
                userInfo.isAccountExpired()
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

        {
            final Instant lastLoginTime = userInfo.getLastLdapLoginTime();
            maker.add(
                    ViewStatusFields.LastLoginTime,
                    Display.Field_LastLoginTime,
                    lastLoginTime
            );

            if ( lastLoginTime != null )
            {
                maker.add(
                        ViewStatusFields.LastLoginTimeDelta,
                        Display.Field_LastLoginTimeDelta,
                        TimeDuration.fromCurrent( lastLoginTime ).asLongString( locale )
                );
            }
        }

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
                    ? TimeDuration.fromCurrent( userInfo.getPasswordLastModifiedTime() ).asLongString( locale )
                    : LocaleHelper.getLocalizedMessage( locale, Display.Value_NotApplicable, config );
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
                ViewStatusFields.IntruderDetect,
                Display.Field_PasswordLocked,
                userInfo.isPasswordLocked()
        );

        {
            final ResponseInfoBean responseInfoBean = userInfo.getResponseInfoBean();
            maker.add(
                    ViewStatusFields.ResponsesStored,
                    Display.Field_ResponsesStored,
                    responseInfoBean != null
            );

            maker.add(
                    ViewStatusFields.ResponsesNeeded,
                    Display.Field_ResponsesNeeded,
                    userInfo.isRequiresResponseConfig()
            );


            if ( responseInfoBean != null )
            {
                maker.add(
                        ViewStatusFields.ResponsesTimestamp,
                        Display.Field_ResponsesTimestamp,
                        responseInfoBean.getTimestamp()
                );
            }
        }

        if ( userInfo.getResponseInfoBean() != null )
        {
            maker.add(
                    ViewStatusFields.ResponsesTimestamp,
                    Display.Field_ResponsesTimestamp,
                    userInfo.getResponseInfoBean().getTimestamp()
            );
        }

        {
            maker.add(
                    ViewStatusFields.OTPStored,
                    Display.Field_OTP_Stored,
                    userInfo.getOtpUserRecord() != null
            );

            if ( userInfo.getOtpUserRecord() != null )
            {
                maker.add(
                        ViewStatusFields.OTPTimestamp,
                        Display.Field_OTP_Timestamp,
                        userInfo.getOtpUserRecord().getTimestamp()
                );
            }
        }

        if ( localSessionStateBean != null )
        {
            maker.add(
                    ViewStatusFields.NetworkAddress,
                    Display.Field_NetworkAddress,
                    localSessionStateBean.getSrcAddress()
            );

            maker.add(
                    ViewStatusFields.NetworkHost,
                    Display.Field_NetworkHost,
                    localSessionStateBean.getSrcHostname()
            );

            {
                final String value = localSessionStateBean.getLogoutURL() == null
                        ? config.readSettingAsString( PwmSetting.URL_LOGOUT )
                        : localSessionStateBean.getLogoutURL();

                maker.add(
                        ViewStatusFields.LogoutURL,
                        Display.Field_LogoutURL,
                        value
                );
            }

            {
                final String value = localSessionStateBean.getForwardURL() == null
                        ? config.readSettingAsString( PwmSetting.URL_FORWARD )
                        : localSessionStateBean.getForwardURL();

                maker.add(
                        ViewStatusFields.ForwardURL,
                        Display.Field_ForwardURL,
                        value
                );
            }
        }


        return Collections.unmodifiableList( accountInfo );
    }

    private static class DataElementMaker
    {
        private final Configuration config;
        private final Set<ViewStatusFields> viewStatusFields;
        private final Locale locale;
        private final List<DisplayElement> list;

        DataElementMaker( final Set<ViewStatusFields> viewStatusFields, final Configuration config, final Locale locale, final List<DisplayElement> list )
        {
            this.config = config;
            this.viewStatusFields = viewStatusFields;
            this.locale = locale;
            this.list = list;
        }

        void add( final ViewStatusFields viewStatusField, final Display display, final Instant instant )
        {

            if ( !viewStatusFields.contains( viewStatusField ) )
            {
                return;
            }

            final String strValue = instant == null
                    ? LocaleHelper.getLocalizedMessage( locale, Display.Value_NotApplicable, config )
                    : JavaHelper.toIsoDate( instant );

            list.add( new DisplayElement(
                    display.name(),
                    DisplayElement.Type.timestamp,
                    LocaleHelper.getLocalizedMessage( locale, display, config ),
                    strValue
            ) );
        }

        void add( final ViewStatusFields viewStatusField, final Display display, final boolean value )
        {
            add( viewStatusField, display, LocaleHelper.booleanString(
                    value,
                    locale,
                    config
            ) );
        }

        void add( final ViewStatusFields viewStatusField, final Display display, final String value )
        {

            if ( !viewStatusFields.contains( viewStatusField ) )
            {
                return;
            }

            final String strValue = StringUtil.isEmpty( value )
                    ? LocaleHelper.getLocalizedMessage( locale, Display.Value_NotApplicable, config )
                    : value;

            list.add( new DisplayElement(
                    display.name(),
                    DisplayElement.Type.string,
                    LocaleHelper.getLocalizedMessage( locale, display, config ),
                    strValue
            ) );
        }
    }
}
