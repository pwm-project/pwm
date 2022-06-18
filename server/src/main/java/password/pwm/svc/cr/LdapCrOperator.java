/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2021 The PWM Project
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

package password.pwm.svc.cr;

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.cr.ChaiCrFactory;
import com.novell.ldapchai.cr.ChaiResponseSet;
import com.novell.ldapchai.cr.ResponseSet;
import com.novell.ldapchai.exception.ChaiError;
import com.novell.ldapchai.exception.ChaiException;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.bean.ResponseInfoBean;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.DomainConfig;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.config.profile.LdapProfile;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.time.Instant;
import java.util.Optional;

public class LdapCrOperator implements CrOperator
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( LdapCrOperator.class );

    private final DomainConfig config;

    public LdapCrOperator( final DomainConfig config )
    {
        this.config = config;
    }

    @Override
    public void close( )
    {
    }

    @Override
    public Optional<ResponseSet> readResponseSet( final SessionLabel sessionLabel, final ChaiUser theUser, final UserIdentity userIdentity, final String userGuid )
            throws PwmUnrecoverableException
    {
        try
        {
            return Optional.ofNullable( ChaiCrFactory.readChaiResponseSet( theUser ) );
        }
        catch ( final ChaiException e )
        {
            LOGGER.debug( sessionLabel, () -> "ldap error reading response set: " + e.getMessage() );
        }
        return Optional.empty();
    }

    @Override
    public Optional<ResponseInfoBean> readResponseInfo( final SessionLabel sessionLabel, final ChaiUser theUser, final UserIdentity userIdentity, final String userGUID )
            throws PwmUnrecoverableException
    {
        try
        {
            final Optional<ResponseSet> responseSet = readResponseSet( sessionLabel, theUser, userIdentity, userGUID );
            return responseSet.isEmpty() ? Optional.empty() : Optional.of( CrOperators.convertToNoAnswerInfoBean( responseSet.get(), DataStorageMethod.LDAP ) );
        }
        catch ( final ChaiException e )
        {
            final String errorMsg = "unexpected error reading response info " + e.getMessage();
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_RESPONSES_NORESPONSES, errorMsg ) );
        }
    }

    @Override
    public void clearResponses( final SessionLabel sessionLabel, final UserIdentity userIdentity, final ChaiUser theUser, final String userGuid )
            throws PwmUnrecoverableException
    {
        final LdapProfile ldapProfile = userIdentity.getLdapProfile( config.getAppConfig() );
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
            LOGGER.info( sessionLabel, () -> "cleared responses for user to chai-ldap format" );
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
            throw new PwmUnrecoverableException( errorInfo, e );
        }
        catch ( final ChaiUnavailableException e )
        {
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_DIRECTORY_UNAVAILABLE, e.getMessage() ) );
        }
    }

    @Override
    public void writeResponses(
            final SessionLabel sessionLabel,
            final UserIdentity userIdentity,
            final ChaiUser theUser,
            final String userGuid,
            final ResponseInfoBean responseInfoBean
    )
            throws PwmUnrecoverableException
    {
        final Instant startTime = Instant.now();
        final LdapProfile ldapProfile = userIdentity.getLdapProfile( config.getAppConfig() );
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
            LOGGER.info( sessionLabel, () -> "saved responses for user to chai-ldap format", TimeDuration.fromCurrent( startTime ) );
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
            throw new PwmUnrecoverableException( errorInfo, e );
        }
    }
}
