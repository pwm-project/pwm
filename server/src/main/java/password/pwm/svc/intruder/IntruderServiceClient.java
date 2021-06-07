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

package password.pwm.svc.intruder;

import password.pwm.PwmDomain;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.config.value.data.FormConfiguration;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmSession;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class IntruderServiceClient
{
    private IntruderServiceClient()
    {
    }

    public static void checkAddressAndSession( final PwmDomain pwmDomain, final PwmSession pwmSession )
            throws PwmUnrecoverableException
    {
        final IntruderDomainService intruderService = pwmDomain.getIntruderService();

        if ( pwmSession != null )
        {
            final String subject = pwmSession.getSessionStateBean().getSrcAddress();
            intruderService.check( IntruderRecordType.ADDRESS, subject );
            final int maxAllowedAttempts = ( int ) pwmDomain.getConfig().readSettingAsLong( PwmSetting.INTRUDER_SESSION_MAX_ATTEMPTS );
            if ( maxAllowedAttempts != 0 && pwmSession.getSessionStateBean().getIntruderAttempts().get() > maxAllowedAttempts )
            {
                throw new PwmUnrecoverableException( PwmError.ERROR_INTRUDER_SESSION );
            }
        }
    }

    public static void markAddressAndSession( final PwmDomain pwmDomain, final PwmSession pwmSession )
            throws PwmUnrecoverableException
    {
        final IntruderDomainService intruderService = pwmDomain.getIntruderService();

        if ( pwmSession != null )
        {
            final String subject = pwmSession.getSessionStateBean().getSrcAddress();
            pwmSession.getSessionStateBean().incrementIntruderAttempts();
            intruderService.mark( IntruderRecordType.ADDRESS, subject, pwmSession.getLabel() );
        }
    }

    public static void clearAddressAndSession( final PwmDomain pwmDomain, final PwmSession pwmSession )
            throws PwmUnrecoverableException
    {
        final IntruderDomainService intruderService = pwmDomain.getIntruderService();

        if ( pwmSession != null )
        {
            final String subject = pwmSession.getSessionStateBean().getSrcAddress();
            intruderService.clear( IntruderRecordType.ADDRESS, subject );
            pwmSession.getSessionStateBean().clearIntruderAttempts();
            pwmSession.getSessionStateBean().setSessionIdRecycleNeeded( true );
        }
    }

    public static void checkUserIdentity( final PwmDomain pwmDomain, final UserIdentity userIdentity )
            throws PwmUnrecoverableException
    {
        final IntruderDomainService intruderService = pwmDomain.getIntruderService();

        if ( userIdentity != null )
        {
            final String subject = userIdentity.toDelimitedKey();
            intruderService.check( IntruderRecordType.USER_ID, subject );
        }
    }

    public static void markUserIdentity( final PwmRequest pwmRequest, final UserIdentity userIdentity )
            throws PwmUnrecoverableException
    {
        markUserIdentity( pwmRequest.getPwmDomain(), pwmRequest.getLabel(), userIdentity );
    }

    public static void markUserIdentity( final PwmDomain pwmDomain, final SessionLabel sessionLabel, final UserIdentity userIdentity )
            throws PwmUnrecoverableException
    {
        final IntruderDomainService intruderService = pwmDomain.getIntruderService();

        if ( userIdentity != null )
        {
            final String subject = userIdentity.toDelimitedKey();
            intruderService.mark( IntruderRecordType.USER_ID, subject, sessionLabel );
        }
    }

    public static void clearUserIdentity( final PwmRequest pwmRequest, final UserIdentity userIdentity )
            throws PwmUnrecoverableException
    {
        final IntruderDomainService intruderService = pwmRequest.getPwmDomain().getIntruderService();

        if ( userIdentity != null )
        {
            final String subject = userIdentity.toDelimitedKey();
            intruderService.clear( IntruderRecordType.USER_ID, subject );
        }
    }

    public static void markAttributes( final PwmRequest pwmRequest, final Map<FormConfiguration, String> formValues )
            throws PwmUnrecoverableException
    {
        markAttributes( pwmRequest.getPwmDomain(), formValues, pwmRequest.getLabel() );
    }

    public static void markAttributes( final PwmDomain pwmDomain, final Map<FormConfiguration, String> formValues, final SessionLabel sessionLabel )
            throws PwmUnrecoverableException
    {
        final IntruderDomainService intruderService = pwmDomain.getIntruderService();

        final List<String> subjects = attributeFormToList( formValues );
        for ( final String subject : subjects )
        {
            intruderService.mark( IntruderRecordType.ATTRIBUTE, subject, sessionLabel );
        }
    }

    public static void clearAttributes( final PwmDomain pwmDomain, final Map<FormConfiguration, String> formValues )
            throws PwmUnrecoverableException
    {
        final IntruderDomainService intruderService = pwmDomain.getIntruderService();

        final List<String> subjects = attributeFormToList( formValues );
        for ( final String subject : subjects )
        {
            intruderService.clear( IntruderRecordType.ATTRIBUTE, subject );
        }
    }

    public static void checkAttributes( final PwmDomain pwmDomain, final Map<FormConfiguration, String> formValues )
            throws PwmUnrecoverableException
    {
        final IntruderDomainService intruderService = pwmDomain.getIntruderService();

        final List<String> subjects = attributeFormToList( formValues );
        for ( final String subject : subjects )
        {
            intruderService.check( IntruderRecordType.ATTRIBUTE, subject );
        }
    }

    private static List<String> attributeFormToList( final Map<FormConfiguration, String> formValues )
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
        return Collections.unmodifiableList( returnList );
    }
}
