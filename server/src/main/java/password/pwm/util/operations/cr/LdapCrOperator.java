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

package password.pwm.util.operations.cr;

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.cr.ChaiCrFactory;
import com.novell.ldapchai.cr.ChaiResponseSet;
import com.novell.ldapchai.cr.ResponseSet;
import com.novell.ldapchai.exception.ChaiError;
import com.novell.ldapchai.exception.ChaiException;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.bean.ResponseInfoBean;
import password.pwm.bean.UserIdentity;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.config.profile.LdapProfile;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.logging.PwmLogger;

public class LdapCrOperator implements CrOperator
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( LdapCrOperator.class );

    private final Configuration config;

    public LdapCrOperator( final Configuration config )
    {
        this.config = config;
    }

    public void close( )
    {
    }

    public ResponseSet readResponseSet( final ChaiUser theUser, final UserIdentity userIdentity, final String userGuid )
            throws PwmUnrecoverableException
    {
        try
        {
            return ChaiCrFactory.readChaiResponseSet( theUser );
        }
        catch ( final ChaiException e )
        {
            LOGGER.debug( () -> "ldap error reading response set: " + e.getMessage(), e );
        }
        return null;
    }

    public ResponseInfoBean readResponseInfo( final ChaiUser theUser, final UserIdentity userIdentity, final String userGUID )
            throws PwmUnrecoverableException
    {
        try
        {
            final ResponseSet responseSet = readResponseSet( theUser, userIdentity, userGUID );
            return responseSet == null ? null : CrOperators.convertToNoAnswerInfoBean( responseSet, DataStorageMethod.LDAP );
        }
        catch ( final ChaiException e )
        {
            final String errorMsg = "unexpected error reading response info " + e.getMessage();
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_RESPONSES_NORESPONSES, errorMsg ) );
        }
    }

    public void clearResponses( final UserIdentity userIdentity, final ChaiUser theUser, final String userGuid )
            throws PwmUnrecoverableException
    {
        final LdapProfile ldapProfile = userIdentity.getLdapProfile( config );
        final String ldapStorageAttribute = ldapProfile.readSettingAsString( PwmSetting.CHALLENGE_USER_ATTRIBUTE );
        if ( ldapStorageAttribute == null || ldapStorageAttribute.length() < 1 )
        {
            final String errorMsg = "ldap storage attribute is not configured, unable to clear user responses";
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INVALID_CONFIG, errorMsg );
            throw new PwmUnrecoverableException( errorInformation );

        }
        try
        {
            final String currentValue = theUser.readStringAttribute( ldapStorageAttribute );
            if ( currentValue != null && currentValue.length() > 0 )
            {
                theUser.deleteAttribute( ldapStorageAttribute, null );
            }
            LOGGER.info( () -> "cleared responses for user to chai-ldap format" );
        }
        catch ( final ChaiOperationException e )
        {
            final String errorMsg;
            if ( e.getErrorCode() == ChaiError.NO_ACCESS )
            {
                errorMsg = "permission error clearing responses to ldap attribute '"
                        + ldapStorageAttribute
                        + "', user does not appear to have correct permissions to clear responses: " + e.getMessage();
            }
            else
            {
                errorMsg = "error clearing responses to ldap attribute '" + ldapStorageAttribute + "': " + e.getMessage();
            }
            final ErrorInformation errorInfo = new ErrorInformation( PwmError.ERROR_WRITING_RESPONSES, errorMsg );
            final PwmUnrecoverableException pwmOE = new PwmUnrecoverableException( errorInfo );
            pwmOE.initCause( e );
            throw pwmOE;
        }
        catch ( final ChaiUnavailableException e )
        {
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_DIRECTORY_UNAVAILABLE, e.getMessage() ) );
        }
    }

    public void writeResponses( final UserIdentity userIdentity, final ChaiUser theUser, final String userGuid, final ResponseInfoBean responseInfoBean )
            throws PwmUnrecoverableException
    {
        final LdapProfile ldapProfile = userIdentity.getLdapProfile( config );
        final String ldapStorageAttribute = ldapProfile.readSettingAsString( PwmSetting.CHALLENGE_USER_ATTRIBUTE );
        if ( ldapStorageAttribute == null || ldapStorageAttribute.length() < 1 )
        {
            final String errorMsg = "ldap storage attribute is not configured, unable to write user responses";
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INVALID_CONFIG, errorMsg );
            throw new PwmUnrecoverableException( errorInformation );
        }
        try
        {
            final ChaiResponseSet responseSet = ChaiCrFactory.newChaiResponseSet(
                    responseInfoBean.getCrMap(),
                    responseInfoBean.getHelpdeskCrMap(),
                    responseInfoBean.getLocale(),
                    responseInfoBean.getMinRandoms(),
                    theUser.getChaiProvider().getChaiConfiguration(),
                    responseInfoBean.getCsIdentifier()
            );
            ChaiCrFactory.writeChaiResponseSet( responseSet, theUser );
            LOGGER.info( () -> "saved responses for user to chai-ldap format" );
        }
        catch ( final ChaiException e )
        {
            final String errorMsg;
            if ( e.getErrorCode() == ChaiError.NO_ACCESS )
            {
                errorMsg = "permission error writing user responses to ldap attribute '"
                        + ldapStorageAttribute
                        + "', user does not appear to have correct permissions to save responses: " + e.getMessage();
            }
            else
            {
                errorMsg = "error writing user responses to ldap attribute '" + ldapStorageAttribute + "': " + e.getMessage();
            }
            final ErrorInformation errorInfo = new ErrorInformation( PwmError.ERROR_WRITING_RESPONSES, errorMsg );
            final PwmUnrecoverableException pwmOE = new PwmUnrecoverableException( errorInfo );
            pwmOE.initCause( e );
            throw pwmOE;
        }
    }
}
