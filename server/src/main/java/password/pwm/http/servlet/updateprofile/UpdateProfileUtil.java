/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2019 The PWM Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package password.pwm.http.servlet.updateprofile;

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.PwmApplication;
import password.pwm.bean.EmailItemBean;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.TokenDestinationItem;
import password.pwm.bean.UserIdentity;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.profile.LdapProfile;
import password.pwm.config.profile.UpdateProfileProfile;
import password.pwm.config.value.data.ActionConfiguration;
import password.pwm.config.value.data.FormConfiguration;
import password.pwm.error.PwmDataValidationException;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.JspUrl;
import password.pwm.http.ProcessStatus;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmRequestAttribute;
import password.pwm.http.bean.UpdateProfileBean;
import password.pwm.ldap.LdapOperationsHelper;
import password.pwm.ldap.UserInfo;
import password.pwm.ldap.UserInfoFactory;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.token.TokenType;
import password.pwm.svc.token.TokenUtil;
import password.pwm.util.form.FormUtility;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;
import password.pwm.util.operations.ActionExecutor;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class UpdateProfileUtil
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( UpdateProfileUtil.class );

    private UpdateProfileUtil()
    {
    }

    static Map<FormConfiguration, String> readFromJsonRequest(
            final PwmRequest pwmRequest,
            final UpdateProfileProfile updateProfileProfile,
            final UpdateProfileBean updateProfileBean
    )
            throws PwmDataValidationException, PwmUnrecoverableException, IOException
    {
        final List<FormConfiguration> formFields = updateProfileProfile.readSettingAsForm( PwmSetting.UPDATE_PROFILE_FORM );

        final Map<FormConfiguration, String> formValueMap = FormUtility.readFormValuesFromMap( pwmRequest.readBodyAsJsonStringMap(), formFields, pwmRequest.getLocale() );

        return updateBeanFormData( formFields, formValueMap, updateProfileBean );
    }

    static Map<FormConfiguration, String> updateBeanFormData(
            final List<FormConfiguration> formFields,
            final Map<FormConfiguration, String> formValueMap,
            final UpdateProfileBean updateProfileBean
    )
    {
        final LinkedHashMap<FormConfiguration, String> newFormValueMap = new LinkedHashMap<>();
        for ( final FormConfiguration formConfiguration : formFields )
        {
            if ( formConfiguration.isReadonly() )
            {
                final String existingValue = updateProfileBean.getFormData().get( formConfiguration.getName() );
                newFormValueMap.put( formConfiguration, existingValue );
            }
            else
            {
                if ( formConfiguration.getType() != FormConfiguration.Type.photo )
                {
                    newFormValueMap.put( formConfiguration, formValueMap.get( formConfiguration ) );
                }
            }
        }

        updateProfileBean.getFormData().putAll( FormUtility.asStringMap( newFormValueMap ) );

        return newFormValueMap;
    }

    static void verifyFormAttributes(
            final PwmApplication pwmApplication,
            final UserIdentity userIdentity,
            final Locale userLocale,
            final Map<FormConfiguration, String> formValues,
            final boolean allowResultCaching
    )
            throws PwmOperationalException, PwmUnrecoverableException
    {
        // see if the values meet form requirements.
        FormUtility.validateFormValues( pwmApplication.getConfig(), formValues, userLocale );

        final List<FormUtility.ValidationFlag> validationFlags = new ArrayList<>();
        if ( allowResultCaching )
        {
            validationFlags.add( FormUtility.ValidationFlag.allowResultCaching );
        }

        // check unique fields against ldap
        FormUtility.validateFormValueUniqueness(
                pwmApplication,
                formValues,
                userLocale,
                Collections.singletonList( userIdentity ),
                validationFlags.toArray( new FormUtility.ValidationFlag[ validationFlags.size() ] )
        );
    }

    static void sendProfileUpdateEmailNotice(
            final PwmApplication pwmApplication,
            final MacroMachine macroMachine,
            final UserInfo userInfo,
            final Locale locale,
            final SessionLabel sessionLabel
    )
            throws PwmUnrecoverableException, ChaiUnavailableException
    {
        final Configuration config = pwmApplication.getConfig();

        final EmailItemBean configuredEmailSetting = config.readSettingAsEmail( PwmSetting.EMAIL_UPDATEPROFILE, locale );
        pwmApplication.getEmailQueue().submitEmail(
                configuredEmailSetting,
                userInfo,
                macroMachine
        );

        if ( configuredEmailSetting == null )
        {
            LOGGER.debug( sessionLabel, () -> "skipping send profile update email for '" + userInfo.getUserIdentity().toDisplayString() + "' no email configured" );
        }
    }

    static void forwardToForm( final PwmRequest pwmRequest, final UpdateProfileProfile updateProfileProfile, final UpdateProfileBean updateProfileBean )
            throws ServletException, PwmUnrecoverableException, IOException
    {
        final List<FormConfiguration> form = updateProfileProfile.readSettingAsForm( PwmSetting.UPDATE_PROFILE_FORM );
        final Map<FormConfiguration, String> formValueMap = formMapFromBean( updateProfileProfile, updateProfileBean );
        pwmRequest.addFormInfoToRequestAttr( form, formValueMap, false, false );
        final List<FormConfiguration> links = updateProfileProfile.readSettingAsForm( PwmSetting.UPDATE_PROFILE_CUSTOMLINKS );
        pwmRequest.setAttribute( PwmRequestAttribute.FormCustomLinks, new ArrayList<>( links ) );
        pwmRequest.forwardToJsp( JspUrl.UPDATE_ATTRIBUTES );
    }

    static void forwardToEnterCode( final PwmRequest pwmRequest, final UpdateProfileProfile updateProfileProfile, final UpdateProfileBean updateProfileBean )
            throws ServletException, PwmUnrecoverableException, IOException
    {
        final TokenDestinationItem tokenDestinationItem = tokenDestinationItemForCurrentValidation(
                pwmRequest,
                updateProfileBean,
                updateProfileProfile );
        pwmRequest.setAttribute( PwmRequestAttribute.TokenDestItems, tokenDestinationItem );
        pwmRequest.forwardToJsp( JspUrl.UPDATE_ATTRIBUTES_ENTER_CODE );
    }

    static void forwardToConfirmForm( final PwmRequest pwmRequest, final UpdateProfileProfile updateProfileProfile, final UpdateProfileBean updateProfileBean )
            throws ServletException, PwmUnrecoverableException, IOException
    {
        final List<FormConfiguration> form = updateProfileProfile.readSettingAsForm( PwmSetting.UPDATE_PROFILE_FORM );
        final Map<FormConfiguration, String> formValueMap = formMapFromBean( updateProfileProfile, updateProfileBean );
        pwmRequest.addFormInfoToRequestAttr( form, formValueMap, true, false );
        pwmRequest.forwardToJsp( JspUrl.UPDATE_ATTRIBUTES_CONFIRM );
    }

    static Map<FormConfiguration, String> formMapFromBean(
            final UpdateProfileProfile updateProfileProfile,
            final UpdateProfileBean updateProfileBean
    )
            throws PwmUnrecoverableException
    {

        final List<FormConfiguration> form = updateProfileProfile.readSettingAsForm( PwmSetting.UPDATE_PROFILE_FORM );
        final Map<FormConfiguration, String> formValueMap = new LinkedHashMap<>();
        for ( final FormConfiguration formConfiguration : form )
        {
            formValueMap.put(
                    formConfiguration,
                    updateProfileBean.getFormData().keySet().contains( formConfiguration.getName() )
                            ? updateProfileBean.getFormData().get( formConfiguration.getName() )
                            : ""
            );
        }
        return formValueMap;
    }

    static Map<String, String> formDataFromLdap( final PwmRequest pwmRequest, final UpdateProfileProfile updateProfileProfile )
            throws PwmUnrecoverableException
    {
        final UserInfo userInfo = pwmRequest.getPwmSession().getUserInfo();
        final List<FormConfiguration> formFields = updateProfileProfile.readSettingAsForm( PwmSetting.UPDATE_PROFILE_FORM );
        final Map<FormConfiguration, String> formMap = new LinkedHashMap<>();
        FormUtility.populateFormMapFromLdap( formFields, pwmRequest.getLabel(), formMap, userInfo );
        return FormUtility.asStringMap( formMap );
    }

    static Map<String, TokenDestinationItem.Type> determineTokenValidationsRequired(
            final PwmRequest pwmRequest,
            final UpdateProfileBean updateProfileBean,
            final UpdateProfileProfile updateProfileProfile
    )
            throws PwmUnrecoverableException
    {
        final List<FormConfiguration> formFields = updateProfileProfile.readSettingAsForm( PwmSetting.UPDATE_PROFILE_FORM );
        final LdapProfile ldapProfile = pwmRequest.getUserInfoIfLoggedIn().getLdapProfile( pwmRequest.getConfig() );
        final Map<String, TokenDestinationItem.Type> workingMap = new LinkedHashMap<>( FormUtility.identifyFormItemsNeedingPotentialTokenValidation(
                ldapProfile,
                formFields
        ) );

        final Set<TokenDestinationItem.Type> interestedTypes = new LinkedHashSet<>(  );
        if ( updateProfileProfile.readSettingAsBoolean( PwmSetting.UPDATE_PROFILE_EMAIL_VERIFICATION ) )
        {
            interestedTypes.add( TokenDestinationItem.Type.email );
        }
        if ( updateProfileProfile.readSettingAsBoolean( PwmSetting.UPDATE_PROFILE_SMS_VERIFICATION ) )
        {
            interestedTypes.add( TokenDestinationItem.Type.sms );
        }

        if ( !JavaHelper.isEmpty( workingMap ) )
        {
            final Map<String, String> ldapData = formDataFromLdap( pwmRequest, updateProfileProfile );
            final Map<String, String> updateData = updateProfileBean.getFormData();

            for ( final Iterator<Map.Entry<String, TokenDestinationItem.Type>> iter = workingMap.entrySet().iterator(); iter.hasNext(); )
            {
                final Map.Entry<String, TokenDestinationItem.Type> entry = iter.next();
                final String attrName = entry.getKey();
                final TokenDestinationItem.Type type = entry.getValue();

                if ( !interestedTypes.contains( type ) )
                {
                    iter.remove();
                }
                else if ( updateData.containsKey( attrName ) )
                {
                    final String updateValue = updateData.get( attrName );
                    final String ldapValue = ldapData.get( attrName );
                    if ( StringUtil.nullSafeEqualsIgnoreCase( updateValue, ldapValue ) )
                    {
                        iter.remove();
                    }
                }
            }
        }

        return Collections.unmodifiableMap( workingMap );
    }


    static ProcessStatus checkForTokenVerificationProgress(
            final PwmRequest pwmRequest,
            final UpdateProfileBean updateProfileBean,
            final UpdateProfileProfile updateProfileProfile
    )
            throws PwmUnrecoverableException, ServletException, IOException
    {
        final Map<String, TokenDestinationItem.Type> requiredTokenValidations = determineTokenValidationsRequired(
                pwmRequest,
                updateProfileBean,
                updateProfileProfile
        );

        if ( !requiredTokenValidations.isEmpty() )
        {
            final Set<String> remainingValidations = new HashSet<>( requiredTokenValidations.keySet() );
            remainingValidations.removeAll( updateProfileBean.getCompletedTokenFields() );

            if ( !remainingValidations.isEmpty() )
            {
                if ( StringUtil.isEmpty( updateProfileBean.getCurrentTokenField() ) )
                {
                    updateProfileBean.setCurrentTokenField( remainingValidations.iterator().next() );
                    updateProfileBean.setTokenSent( false );
                }

                if ( !updateProfileBean.isTokenSent() )
                {
                    final TokenDestinationItem tokenDestinationItem = tokenDestinationItemForCurrentValidation( pwmRequest, updateProfileBean, updateProfileProfile );
                    final TimeDuration tokenLifetime = tokenDestinationItem.getType() == TokenDestinationItem.Type.email
                            ? updateProfileProfile.getTokenDurationEmail( pwmRequest.getConfig() )
                            : updateProfileProfile.getTokenDurationSMS( pwmRequest.getConfig() );

                    TokenUtil.initializeAndSendToken(
                            pwmRequest.commonValues(),
                            TokenUtil.TokenInitAndSendRequest.builder()
                                    .userInfo( pwmRequest.getPwmSession().getUserInfo() )
                                    .tokenDestinationItem( tokenDestinationItem )
                                    .emailToSend( PwmSetting.EMAIL_UPDATEPROFILE_VERIFICATION )
                                    .tokenType( TokenType.UPDATE )
                                    .smsToSend( PwmSetting.SMS_UPDATE_PROFILE_TOKEN_TEXT )
                                    .tokenLifetime( tokenLifetime )
                                    .build()
                    );
                    updateProfileBean.setTokenSent( true );

                }

                forwardToEnterCode( pwmRequest, updateProfileProfile, updateProfileBean );
                return ProcessStatus.Halt;
            }
        }

        return ProcessStatus.Continue;
    }

    @SuppressWarnings( "checkstyle:ParameterNumber" )
    public static void doProfileUpdate(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final Locale locale,
            final UserInfo userInfo,
            final MacroMachine macroMachine,
            final UpdateProfileProfile updateProfileProfile,
            final Map<String, String> formValues,
            final ChaiUser theUser
    )
            throws PwmUnrecoverableException, ChaiUnavailableException, PwmOperationalException
    {
        final List<FormConfiguration> formFields = updateProfileProfile.readSettingAsForm( PwmSetting.UPDATE_PROFILE_FORM );
        final Map<FormConfiguration, String> formMap = FormUtility.readFormValuesFromMap( formValues, formFields, locale );

        // verify form meets the form requirements (may be redundant, but shouldn't hurt)
        verifyFormAttributes( pwmApplication, userInfo.getUserIdentity(), locale, formMap, false );

        // write values.
        LOGGER.info( () -> "updating profile for " + userInfo.getUserIdentity() );

        LdapOperationsHelper.writeFormValuesToLdap( theUser, formMap, macroMachine, false );

        postUpdateActionsAndEmail( pwmApplication, sessionLabel, locale, userInfo.getUserIdentity(), updateProfileProfile );

        // success, so forward to success page
        pwmApplication.getStatisticsManager().incrementValue( Statistic.UPDATE_ATTRIBUTES );
    }

    private static void postUpdateActionsAndEmail(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final Locale locale,
            final UserIdentity userIdentity,
            final UpdateProfileProfile updateProfileProfile
    )
            throws PwmUnrecoverableException, ChaiUnavailableException, PwmOperationalException
    {
        // obtain new macro machine (with a new UserInfo) so old cached values won't be used for next op
        final UserInfo reloadedUserInfo = UserInfoFactory.newUserInfo(
                pwmApplication,
                sessionLabel,
                locale,
                userIdentity,
                pwmApplication.getProxiedChaiUser( userIdentity ).getChaiProvider() );
        final MacroMachine reloadedMacroMachine = MacroMachine.forUser( pwmApplication, sessionLabel, reloadedUserInfo, null, null );

        {
            // execute configured actions
            final List<ActionConfiguration> actions = updateProfileProfile.readSettingAsAction( PwmSetting.UPDATE_PROFILE_WRITE_ATTRIBUTES );
            if ( actions != null && !actions.isEmpty() )
            {
                LOGGER.debug( sessionLabel, () -> "executing configured actions to user " + reloadedUserInfo.getUserIdentity() );

                final ActionExecutor actionExecutor = new ActionExecutor.ActionExecutorSettings( pwmApplication, reloadedUserInfo.getUserIdentity() )
                        .setExpandPwmMacros( true )
                        .setMacroMachine( reloadedMacroMachine )
                        .createActionExecutor();

                actionExecutor.executeActions( actions, sessionLabel );
            }
        }

        sendProfileUpdateEmailNotice( pwmApplication, reloadedMacroMachine, reloadedUserInfo, locale, sessionLabel );
    }

    static TokenDestinationItem tokenDestinationItemForCurrentValidation(
            final PwmRequest pwmRequest,
            final UpdateProfileBean updateProfileBean,
            final UpdateProfileProfile updateProfileProfile
    )
    {
        final List<FormConfiguration> formFields = updateProfileProfile.readSettingAsForm( PwmSetting.UPDATE_PROFILE_FORM );
        final LdapProfile ldapProfile = pwmRequest.getUserInfoIfLoggedIn().getLdapProfile( pwmRequest.getConfig() );
        final Map<String, TokenDestinationItem.Type> tokenTypeMap = FormUtility.identifyFormItemsNeedingPotentialTokenValidation(
                ldapProfile,
                formFields
        );

        final String value = updateProfileBean.getFormData().get( updateProfileBean.getCurrentTokenField() );
        final TokenDestinationItem.Type type = tokenTypeMap.get( updateProfileBean.getCurrentTokenField() );
        return TokenDestinationItem.builder()
                .display( value )
                .id( "1" )
                .value( value )
                .type( type )
                .build();
    }
}
