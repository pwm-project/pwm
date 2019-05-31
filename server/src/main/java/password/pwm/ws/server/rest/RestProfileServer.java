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

package password.pwm.ws.server.rest;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import lombok.Data;
import password.pwm.PwmConstants;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.WebServiceUsage;
import password.pwm.config.profile.ProfileType;
import password.pwm.config.profile.ProfileUtility;
import password.pwm.config.profile.UpdateProfileProfile;
import password.pwm.config.value.data.FormConfiguration;
import password.pwm.config.value.data.UserPermission;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpContentType;
import password.pwm.http.HttpMethod;
import password.pwm.http.PwmHttpRequestWrapper;
import password.pwm.http.servlet.updateprofile.UpdateProfileUtil;
import password.pwm.i18n.Message;
import password.pwm.ldap.LdapPermissionTester;
import password.pwm.ldap.UserInfo;
import password.pwm.ldap.UserInfoFactory;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsManager;
import password.pwm.util.FormMap;
import password.pwm.util.form.FormUtility;
import password.pwm.util.java.StringUtil;
import password.pwm.util.macro.MacroMachine;
import password.pwm.ws.server.RestMethodHandler;
import password.pwm.ws.server.RestRequest;
import password.pwm.ws.server.RestResultBean;
import password.pwm.ws.server.RestServlet;
import password.pwm.ws.server.RestUtility;
import password.pwm.ws.server.RestWebServer;

import javax.servlet.annotation.WebServlet;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@WebServlet(
        urlPatterns = {
                PwmConstants.URL_PREFIX_PUBLIC + PwmConstants.URL_PREFIX_REST + "/profile",
        }
)
@RestWebServer( webService = WebServiceUsage.RandomPassword, requireAuthentication = false )
public class RestProfileServer extends RestServlet
{

    private static final String FIELD_USERNAME = "username";

    @Data
    public static class JsonProfileData implements Serializable
    {
        private String username;
        private Map<String, String> profile;
        private List<FormConfiguration> formDefinition;
    }

    @Override
    public void preCheckRequest( final RestRequest request ) throws PwmUnrecoverableException
    {
        if ( !request.getPwmApplication().getConfig().readSettingAsBoolean( PwmSetting.UPDATE_PROFILE_ENABLE ) )
        {
            throw new PwmUnrecoverableException( PwmError.ERROR_SERVICE_NOT_AVAILABLE, "update profile module is not enabled" );
        }
    }

    @RestMethodHandler( method = HttpMethod.GET, produces = HttpContentType.json )
    public RestResultBean doGetProfileJsonData( final RestRequest restRequest )
            throws PwmUnrecoverableException
    {
        final String username = restRequest.readParameterAsString( "username", PwmHttpRequestWrapper.Flag.BypassValidation );

        try
        {
            return doGetProfileDataImpl( restRequest, username );
        }
        catch ( PwmUnrecoverableException e )
        {
            return RestResultBean.fromError( restRequest, e.getErrorInformation() );
        }
        catch ( Exception e )
        {
            final String errorMsg = "unexpected error building json response: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INTERNAL, errorMsg );
            return RestResultBean.fromError( restRequest, errorInformation );
        }
    }

    private static RestResultBean doGetProfileDataImpl(
            final RestRequest restRequest,
            final String username
    )
            throws PwmUnrecoverableException, ChaiUnavailableException
    {
        final TargetUserIdentity targetUserIdentity = RestUtility.resolveRequestedUsername( restRequest, username );

        final String updateProfileID = ProfileUtility.discoverProfileIDforUser(
                restRequest.getPwmApplication(),
                restRequest.getSessionLabel(),
                targetUserIdentity.getUserIdentity(),
                ProfileType.UpdateAttributes
        );

        if ( StringUtil.isEmpty( updateProfileID ) )
        {
            throw new PwmUnrecoverableException( PwmError.ERROR_NO_PROFILE_ASSIGNED );
        }

        final UpdateProfileProfile updateProfileProfile = restRequest.getPwmApplication().getConfig().getUpdateAttributesProfile().get( updateProfileID );

        final Map<String, String> profileData = new HashMap<>();
        {
            final Map<FormConfiguration, String> formData = new HashMap<>();
            for ( final FormConfiguration formConfiguration : updateProfileProfile.readSettingAsForm( PwmSetting.UPDATE_PROFILE_FORM ) )
            {
                formData.put( formConfiguration, "" );
            }
            final List<FormConfiguration> formFields = updateProfileProfile.readSettingAsForm( PwmSetting.UPDATE_PROFILE_FORM );

            final UserInfo userInfo = UserInfoFactory.newUserInfo(
                    restRequest.getPwmApplication(),
                    restRequest.getSessionLabel(),
                    restRequest.getLocale(),
                    targetUserIdentity.getUserIdentity(),
                    targetUserIdentity.getChaiProvider()
            );
            FormUtility.populateFormMapFromLdap( formFields, restRequest.getSessionLabel(), formData, userInfo );

            for ( final Map.Entry<FormConfiguration, String> entry : formData.entrySet() )
            {
                final FormConfiguration formConfig = entry.getKey();
                profileData.put( formConfig.getName(), entry.getValue() );
            }
        }

        final JsonProfileData outputData = new JsonProfileData();
        outputData.profile = profileData;
        outputData.formDefinition = updateProfileProfile.readSettingAsForm( PwmSetting.UPDATE_PROFILE_FORM );
        final RestResultBean restResultBean = RestResultBean.withData( outputData );
        StatisticsManager.incrementStat( restRequest.getPwmApplication(), Statistic.REST_PROFILE );
        return restResultBean;
    }

    @RestMethodHandler( method = HttpMethod.POST, consumes = HttpContentType.json, produces = HttpContentType.json )
    public RestResultBean doPostProfileData( final RestRequest restRequest ) throws IOException, PwmUnrecoverableException
    {

        final JsonProfileData jsonInput = RestUtility.deserializeJsonBody( restRequest, JsonProfileData.class );

        try
        {
            return doPostProfileDataImpl( restRequest, jsonInput );
        }
        catch ( Exception e )
        {
            final String errorMsg = "unexpected error building json response: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INTERNAL, errorMsg );
            return RestResultBean.fromError( restRequest, errorInformation );
        }
    }

    private static RestResultBean doPostProfileDataImpl(
            final RestRequest restRequest,
            final JsonProfileData jsonInput
    )
            throws PwmUnrecoverableException, ChaiUnavailableException, PwmOperationalException
    {
        final String username = RestUtility.readValueFromJsonAndParam(
                jsonInput.getUsername(),
                restRequest.readParameterAsString( FIELD_USERNAME ),
                FIELD_USERNAME, RestUtility.ReadValueFlag.optional
        );

        final TargetUserIdentity targetUserIdentity = RestUtility.resolveRequestedUsername( restRequest, username );

        final String updateProfileID = ProfileUtility.discoverProfileIDforUser(
                restRequest.getPwmApplication(),
                restRequest.getSessionLabel(),
                targetUserIdentity.getUserIdentity(),
                ProfileType.UpdateAttributes
        );

        if ( StringUtil.isEmpty( updateProfileID ) )
        {
            throw new PwmUnrecoverableException( PwmError.ERROR_NO_PROFILE_ASSIGNED );
        }

        final UpdateProfileProfile updateProfileProfile = restRequest.getPwmApplication().getConfig().getUpdateAttributesProfile().get( updateProfileID );

        {
            final List<UserPermission> userPermission = updateProfileProfile.readSettingAsUserPermission( PwmSetting.UPDATE_PROFILE_QUERY_MATCH );
            final boolean result = LdapPermissionTester.testUserPermissions(
                    restRequest.getPwmApplication(),
                    restRequest.getSessionLabel(),
                    targetUserIdentity.getUserIdentity(),
                    userPermission
            );

            if ( !result )
            {
                throw new PwmUnrecoverableException( PwmError.ERROR_UNAUTHORIZED );
            }
        }


        final FormMap inputFormData = new FormMap( jsonInput.profile );
        final List<FormConfiguration> profileForm = updateProfileProfile.readSettingAsForm( PwmSetting.UPDATE_PROFILE_FORM );
        final Set<String> attributesInRequest = new HashSet<>( inputFormData.keySet() );
        final Map<FormConfiguration, String> profileFormData = new HashMap<>();
        for ( final FormConfiguration formConfiguration : profileForm )
        {
            if ( !formConfiguration.isReadonly() && inputFormData.containsKey( formConfiguration.getName() ) )
            {
                profileFormData.put( formConfiguration, inputFormData.get( formConfiguration.getName() ) );
                attributesInRequest.remove( formConfiguration.getName() );
            }
        }

        if ( !attributesInRequest.isEmpty() )
        {
            throw new PwmUnrecoverableException( PwmError.ERROR_REST_INVOCATION_ERROR, "unknown profile data field '" + attributesInRequest.iterator().next() + "'" );
        }

        final UserInfo userInfo = UserInfoFactory.newUserInfo(
                restRequest.getPwmApplication(),
                restRequest.getSessionLabel(),
                restRequest.getLocale(),
                targetUserIdentity.getUserIdentity(),
                targetUserIdentity.getChaiProvider()
        );

        final MacroMachine macroMachine = MacroMachine.forUser(
                restRequest.getPwmApplication(),
                restRequest.getLocale(),
                restRequest.getSessionLabel(),
                targetUserIdentity.getUserIdentity()
        );

        UpdateProfileUtil.doProfileUpdate(
                restRequest.getPwmApplication(),
                restRequest.getSessionLabel(),
                restRequest.getLocale(),
                userInfo,
                macroMachine,
                updateProfileProfile,
                FormUtility.asStringMap( profileFormData ),
                targetUserIdentity.getChaiUser()
        );

        StatisticsManager.incrementStat( restRequest.getPwmApplication(), Statistic.REST_PROFILE );
        return RestResultBean.forSuccessMessage( restRequest, Message.Success_UpdateProfile );
    }
}
