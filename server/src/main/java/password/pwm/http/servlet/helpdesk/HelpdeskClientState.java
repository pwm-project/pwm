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

package password.pwm.http.servlet.helpdesk;

import password.pwm.AppProperty;
import password.pwm.bean.UserIdentity;
import password.pwm.config.option.IdentityVerificationMethod;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmRequestContext;
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.logging.PwmLogger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record HelpdeskClientState(
        UserIdentity actor,
        List<HelpdeskVerificationRecord> records
)
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( HelpdeskClientState.class );

    public HelpdeskClientState(
            final UserIdentity actor,
            final List<HelpdeskVerificationRecord> records
    )
    {
        this.actor = Objects.requireNonNull( actor );
        this.records = CollectionUtil.stripNulls( records ).stream()
                .sorted()
                .toList();
    }

    public HelpdeskClientState addRecord(
            final UserIdentity identity,
            final IdentityVerificationMethod method )
    {
        final List<HelpdeskVerificationRecord> outputList = CollectionUtil.addListItems(
                removeRecord( identity, method ),
                new HelpdeskVerificationRecord( identity, method, Instant.now() ) );

        return new HelpdeskClientState( actor, outputList );
    }

    private List<HelpdeskVerificationRecord> removeRecord(
            final UserIdentity identity,
            final IdentityVerificationMethod method
    )
    {
        return records().stream()
                .filter( record -> !record.matches( identity, method ) )
                .toList();
    }

    public boolean hasRecord( final UserIdentity identity, final IdentityVerificationMethod method )
    {
        return getRecord( identity, method ).isPresent();
    }

    private Optional<HelpdeskVerificationRecord> getRecord(
            final UserIdentity identity,
            final IdentityVerificationMethod method )
    {
        return records.stream().filter( record -> record.matches( identity, method ) ).findFirst();
    }

    private HelpdeskClientState purgeOldRecords( final TimeDuration maximumAge )
    {
        return new HelpdeskClientState(
                this.actor,
                this.records.stream()
                        .filter( record -> TimeDuration.fromCurrent( record.timestamp() ).isShorterThan( maximumAge ) )
                        .toList() );
    }

    public List<HelpdeskVerificationDisplayRecord> asDisplayableValidationRecords(
            final PwmRequestContext pwmRequestContext
    )
            throws PwmUnrecoverableException
    {
        final List<HelpdeskVerificationDisplayRecord> returnList = new ArrayList<>( records.size() );
        for ( final HelpdeskVerificationRecord record : records )
        {
            returnList.add( record.toViewableRecord( pwmRequestContext ) );
        }

        Collections.sort( returnList );

        return List.copyOf( returnList );
    }

    private static TimeDuration figureMaxAge( final PwmRequest pwmRequest )
    {
        final int maxAgeSeconds = Integer.parseInt( pwmRequest.getDomainConfig()
                .readAppProperty( AppProperty.HELPDESK_VERIFICATION_TIMEOUT_SECONDS ) );
        return TimeDuration.of( maxAgeSeconds, TimeDuration.Unit.SECONDS );
    }


    String toClientString( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException
    {
        final TimeDuration maxAge = figureMaxAge( pwmRequest );
        final HelpdeskClientState purgedState = this.purgeOldRecords( maxAge );
        return pwmRequest.encryptObjectToString( purgedState );
    }

    public static HelpdeskClientState fromClientString(
            final PwmRequest pwmRequest,
            final String rawValue
    )
    {
        final UserIdentity actor = pwmRequest.getUserInfoIfLoggedIn();

        HelpdeskClientState inputState = null;
        if ( !StringUtil.isEmpty( rawValue ) )
        {
            try
            {
                inputState = pwmRequest.decryptObject( rawValue, HelpdeskClientState.class );
                if ( inputState != null && !Objects.equals( inputState.actor(), actor ) )
                {
                    inputState = null;
                }
            }
            catch ( final Exception e )
            {
                LOGGER.trace( pwmRequest, () -> "unable to deserializing client verification state, will ignore; error: " + e.getMessage() );
            }
        }

        if ( inputState == null )
        {
            return new HelpdeskClientState( actor, List.of() );
        }

        final TimeDuration maxAge = figureMaxAge( pwmRequest );

        final HelpdeskClientState outputRecord = inputState.purgeOldRecords( maxAge );

        {
            LOGGER.debug( pwmRequest, () -> "read current state: " + JsonFactory.get().serialize( outputRecord ) );
        }

        return outputRecord;
    }

}


