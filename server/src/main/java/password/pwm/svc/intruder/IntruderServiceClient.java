/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2020 The PWM Project
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

package password.pwm.svc.intruder;

import password.pwm.PwmApplication;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.config.value.data.FormConfiguration;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmSession;
import password.pwm.svc.PwmService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class IntruderServiceClient
{
    private final PwmApplication pwmApplication;
    private final IntruderService intruderService;

    protected IntruderServiceClient( final PwmApplication pwmApplication, final IntruderService intruderService )
    {
        this.pwmApplication = Objects.requireNonNull( pwmApplication );
        this.intruderService = Objects.requireNonNull( intruderService );
    }

    public static void checkUserIdentity( final PwmApplication pwmApplication, final UserIdentity userIdentity ) throws PwmUnrecoverableException
    {
        if ( pwmApplication != null )
        {
            final IntruderService intruderService = pwmApplication.getIntruderService();
            if ( intruderService != null && intruderService.status() == PwmService.STATUS.OPEN )
            {
                intruderService.convenience().checkUserIdentity( userIdentity );
            }
        }
    }

    public void markAddressAndSession( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException
    {
        if ( pwmRequest != null )
        {
            final String subject = pwmRequest.getPwmSession().getSessionStateBean().getSrcAddress();
            pwmRequest.getPwmSession().getSessionStateBean().incrementIntruderAttempts();
            intruderService.mark( IntruderRecordType.ADDRESS, subject, pwmRequest.getLabel() );
        }
    }

    public void checkAddressAndSession( final PwmSession pwmSession )
            throws PwmUnrecoverableException
    {
        if ( pwmSession != null )
        {
            final String subject = pwmSession.getSessionStateBean().getSrcAddress();
            intruderService.check( IntruderRecordType.ADDRESS, subject );
            final int maxAllowedAttempts = ( int ) pwmApplication.getConfig().readSettingAsLong( PwmSetting.INTRUDER_SESSION_MAX_ATTEMPTS );
            if ( maxAllowedAttempts != 0 && pwmSession.getSessionStateBean().getIntruderAttempts().get() > maxAllowedAttempts )
            {
                throw new PwmUnrecoverableException( PwmError.ERROR_INTRUDER_SESSION );
            }
        }
    }

    public void clearAddressAndSession( final PwmSession pwmSession )
            throws PwmUnrecoverableException
    {
        if ( pwmSession != null )
        {
            final String subject = pwmSession.getSessionStateBean().getSrcAddress();
            intruderService.clear( IntruderRecordType.ADDRESS, subject );
            pwmSession.getSessionStateBean().clearIntruderAttempts();
            pwmSession.getSessionStateBean().setSessionIdRecycleNeeded( true );
        }
    }

    public void markUserIdentity( final UserIdentity userIdentity, final SessionLabel sessionLabel )
            throws PwmUnrecoverableException
    {
        if ( userIdentity != null )
        {
            final String subject = userIdentity.toDelimitedKey();
            intruderService.mark( IntruderRecordType.USER_ID, subject, sessionLabel );
        }
    }

    public void markUserIdentity( final UserIdentity userIdentity, final PwmRequest pwmRequest )
            throws PwmUnrecoverableException
    {
        if ( userIdentity != null )
        {
            final String subject = userIdentity.toDelimitedKey();
            intruderService.mark( IntruderRecordType.USER_ID, subject, pwmRequest.getLabel() );
        }
    }

    public void checkUserIdentity( final UserIdentity userIdentity )
            throws PwmUnrecoverableException
    {
        if ( userIdentity != null )
        {
            final String subject = userIdentity.toDelimitedKey();
            intruderService.check( IntruderRecordType.USER_ID, subject );
        }
    }

    public void clearUserIdentity( final UserIdentity userIdentity )
            throws PwmUnrecoverableException
    {
        if ( userIdentity != null )
        {
            final String subject = userIdentity.toDelimitedKey();
            intruderService.clear( IntruderRecordType.USER_ID, subject );
        }
    }

    public void markAttributes( final Map<FormConfiguration, String> formValues, final SessionLabel sessionLabel )
            throws PwmUnrecoverableException
    {
        final List<String> subjects = attributeFormToList( formValues );
        for ( final String subject : subjects )
        {
            intruderService.mark( IntruderRecordType.ATTRIBUTE, subject, sessionLabel );
        }
    }

    public void clearAttributes( final Map<FormConfiguration, String> formValues )
            throws PwmUnrecoverableException
    {
        final List<String> subjects = attributeFormToList( formValues );
        for ( final String subject : subjects )
        {
            intruderService.clear( IntruderRecordType.ATTRIBUTE, subject );
        }
    }

    public void checkAttributes( final Map<FormConfiguration, String> formValues )
            throws PwmUnrecoverableException
    {
        final List<String> subjects = attributeFormToList( formValues );
        for ( final String subject : subjects )
        {
            intruderService.check( IntruderRecordType.ATTRIBUTE, subject );
        }
    }

    private List<String> attributeFormToList( final Map<FormConfiguration, String> formValues )
    {
        final List<String> returnList = new ArrayList<>();
        if ( formValues != null )
        {
            for ( final Map.Entry<FormConfiguration, String> entry : formValues.entrySet() )
            {
                final FormConfiguration formConfiguration = entry.getKey();
                final String value = entry.getValue();
                if ( value != null && value.length() > 0 )
                {
                    returnList.add( formConfiguration.getName() + ":" + value );
                }
            }
        }
        return returnList;
    }

}
