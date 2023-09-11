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

package password.pwm.svc.email;

import password.pwm.AppProperty;
import password.pwm.config.AppConfig;
import password.pwm.config.PwmSetting;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

record EmailServiceSettings(
         TimeDuration connectionSendItemDuration,
         TimeDuration queueRetryTimeout,
         TimeDuration queueDiscardAge,
         int connectionSendItemLimit,
         int maxThreads,
         int queueMaxItems,
         Set<Integer> retryableStatusResponses
)
{
    private static final EmailServiceSettings EMPTY = new EmailServiceSettings(
            TimeDuration.ZERO,
            TimeDuration.ZERO,
            TimeDuration.ZERO,
            0,
            0,
            0,
            Set.of() );

    static EmailServiceSettings empty()
    {
        return EMPTY;
    }

    static EmailServiceSettings fromConfiguration( final AppConfig appConfig )
    {
        return new EmailServiceSettings(
                 TimeDuration.of(
                        Integer.parseInt( appConfig.readAppProperty( AppProperty.QUEUE_EMAIL_MAX_SECONDS_PER_CONNECTION ) ),
                        TimeDuration.Unit.SECONDS ),
                 TimeDuration.of(
                        Long.parseLong( appConfig.readAppProperty( AppProperty.QUEUE_EMAIL_RETRY_TIMEOUT_MS ) ),
                        TimeDuration.Unit.MILLISECONDS ),
                 TimeDuration.of( appConfig.readSettingAsLong( PwmSetting.EMAIL_MAX_QUEUE_AGE ), TimeDuration.Unit.SECONDS ),
                 Integer.parseInt( appConfig.readAppProperty( AppProperty.QUEUE_EMAIL_MAX_ITEMS_PER_CONNECTION ) ),
                 Integer.parseInt( appConfig.readAppProperty( AppProperty.QUEUE_EMAIL_MAX_THREADS ) ),
                 Integer.parseInt( appConfig.readAppProperty( AppProperty.QUEUE_EMAIL_MAX_COUNT ) ),
                 readRetryableStatusCodes( appConfig ) );
    }

    private static Set<Integer> readRetryableStatusCodes( final AppConfig appConfig )
    {
        final String rawAppProp = appConfig.readAppProperty( AppProperty.SMTP_RETRYABLE_SEND_RESPONSE_STATUSES );
        if ( StringUtil.isEmpty( rawAppProp ) )
        {
            return Collections.emptySet();
        }

        final String[] split = rawAppProp.split( "," );
        final Set<Integer> returnData = new HashSet<>( split.length );
        for ( final String loopString : split )
        {
            final Integer loopInt = Integer.parseInt( loopString );
            returnData.add( loopInt );
        }
        return Collections.unmodifiableSet( returnData );
    }


}
