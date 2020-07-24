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

package password.pwm.svc.email;

import lombok.Builder;
import lombok.Value;
import password.pwm.AppProperty;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Value
@Builder
public class EmailServiceSettings implements Serializable
{
    private static final long serialVersionUID = 0L;

    private final TimeDuration connectionSendItemDuration;
    private final TimeDuration queueRetryTimeout;
    private final TimeDuration queueDiscardAge;
    private final int connectionSendItemLimit;
    private final int maxThreads;
    private final int queueMaxItems;
    private final Set<Integer> retryableStatusResponses;


    static EmailServiceSettings fromConfiguration( final Configuration configuration )
    {
        return builder()
                .maxThreads( Integer.parseInt( configuration.readAppProperty( AppProperty.QUEUE_EMAIL_MAX_THREADS ) ) )
                .connectionSendItemDuration( TimeDuration.of(
                        Integer.parseInt( configuration.readAppProperty( AppProperty.QUEUE_EMAIL_MAX_SECONDS_PER_CONNECTION ) ),
                        TimeDuration.Unit.SECONDS ) )
                .connectionSendItemLimit( Integer.parseInt( configuration.readAppProperty( AppProperty.QUEUE_EMAIL_MAX_ITEMS_PER_CONNECTION ) ) )
                .queueRetryTimeout( TimeDuration.of(
                        Long.parseLong( configuration.readAppProperty( AppProperty.QUEUE_EMAIL_RETRY_TIMEOUT_MS ) ),
                        TimeDuration.Unit.MILLISECONDS )
                )
                .queueDiscardAge( TimeDuration.of( configuration.readSettingAsLong( PwmSetting.EMAIL_MAX_QUEUE_AGE ), TimeDuration.Unit.SECONDS ) )
                .queueMaxItems( Integer.parseInt( configuration.readAppProperty( AppProperty.QUEUE_EMAIL_MAX_COUNT ) ) )
                .retryableStatusResponses( readRetryableStatusCodes( configuration ) )
                .build();
    }

    private static Set<Integer> readRetryableStatusCodes( final Configuration configuration )
    {
        final String rawAppProp = configuration.readAppProperty( AppProperty.SMTP_RETRYABLE_SEND_RESPONSE_STATUSES );
        if ( StringUtil.isEmpty( rawAppProp ) )
        {
            return Collections.emptySet();
        }

        final Set<Integer> returnData = new HashSet<>();
        for ( final String loopString : rawAppProp.split( "," ) )
        {
            final Integer loopInt = Integer.parseInt( loopString );
            returnData.add( loopInt );
        }
        return Collections.unmodifiableSet( returnData );
    }


}
