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

package password.pwm;

import password.pwm.bean.DomainID;
import password.pwm.config.AppConfig;
import password.pwm.config.DomainConfig;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

class PwmDomainUtil
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( PwmDomainUtil.class );

    static Map<DomainID, PwmDomain> createDomainInstances( final PwmApplication pwmApplication )
            throws PwmUnrecoverableException
    {
        final Map<DomainID, PwmDomain> domainMap = new TreeMap<>();

        if ( pwmApplication.getPwmEnvironment().isInternalRuntimeInstance() )
        {
            final DomainID domainID = pwmApplication.getPwmEnvironment().getConfig().getAdminDomainID();
            domainMap.put( domainID, new PwmDomain( pwmApplication, domainID ) );
        }
        else
        {
            for ( final String domainIdString : pwmApplication.getPwmEnvironment().getConfig().getDomainIDs() )
            {
                final DomainID domainID = DomainID.create( domainIdString );
                final PwmDomain newDomain = new PwmDomain( pwmApplication, domainID );
                domainMap.put( domainID, newDomain );
            }
        }

        return Collections.unmodifiableMap( domainMap );
    }

    static void initDomains(
            final PwmApplication pwmApplication,
            final Collection<PwmDomain> domains

    )
            throws PwmUnrecoverableException
    {
        final Instant domainInitStartTime = Instant.now();
        LOGGER.trace( () -> "beginning domain initializations" );

        final List<Callable<Optional<PwmUnrecoverableException>>> callables = domains.stream()
                .map( DomainInitializingCallable::new )
                .collect( Collectors.toUnmodifiableList() );

        final  List<Optional<PwmUnrecoverableException>> domainStartException = pwmApplication.getPwmScheduler()
                .executeImmediateThreadPerJobAndAwaitCompletion( PwmApplication.DOMAIN_STARTUP_THREADS, callables, pwmApplication.getSessionLabel(), PwmDomainUtil.class );

        final Optional<PwmUnrecoverableException> domainStartupException = domainStartException.stream()
                .filter( Optional::isPresent )
                .map( Optional::get )
                .findAny();

        if ( domainStartupException.isPresent() )
        {
            throw domainStartupException.get();
        }

        LOGGER.trace( () -> "completed domain initialization for domains", () -> TimeDuration.fromCurrent( domainInitStartTime ) );
    }

    private static class DomainInitializingCallable implements Callable<Optional<PwmUnrecoverableException>>
    {
        private final PwmDomain pwmDomain;

        DomainInitializingCallable( final PwmDomain pwmDomain )
        {
            this.pwmDomain = pwmDomain;
        }

        @Override
        public Optional<PwmUnrecoverableException> call()
                throws Exception
        {
            try
            {
                pwmDomain.initialize();
                return Optional.empty();
            }
            catch ( final PwmUnrecoverableException e )
            {
                return Optional.of( e );
            }
        }
    }

    static Map<DomainID, PwmDomain> reInitDomains(
            final PwmApplication pwmApplication,
            final AppConfig newConfig,
            final AppConfig oldConfig
    )
            throws PwmUnrecoverableException
    {
        final Map<DomainModifyCategory, Set<DomainID>> categorizedDomains = categorizeDomainModifications( newConfig, oldConfig );

        final Set<PwmDomain> deletedDomains = pwmApplication.domains().entrySet().stream()
                .filter( e -> categorizedDomains.get( DomainModifyCategory.obsolete ).contains( e.getKey() ) )
                .map( Map.Entry::getValue ).collect( Collectors.toSet() );

        final Set<PwmDomain> newDomains = pwmApplication.domains().entrySet().stream()
                .filter( e -> categorizedDomains.get( DomainModifyCategory.created ).contains( e.getKey() ) )
                .map( Map.Entry::getValue ).collect( Collectors.toSet() );

        final Map<DomainID, PwmDomain> returnDomainMap = new TreeMap<>( pwmApplication.domains().entrySet().stream()
                .filter( e -> categorizedDomains.get( DomainModifyCategory.unchanged ).contains( e.getKey() ) )
                .collect( Collectors.toMap( Map.Entry::getKey, Map.Entry::getValue ) ) );

        for ( final DomainID modifiedDomainID : categorizedDomains.get( DomainModifyCategory.modified ) )
        {
            deletedDomains.add( pwmApplication.domains().get( modifiedDomainID ) );
            final PwmDomain newDomain = new PwmDomain( pwmApplication, modifiedDomainID );
            newDomains.add( newDomain );
            returnDomainMap.put( modifiedDomainID, newDomain );
        }


        initDomains( pwmApplication, newDomains );

        processDeletedDomains( pwmApplication, deletedDomains );

        return Collections.unmodifiableMap( returnDomainMap );
    }

    private static void processDeletedDomains(
            final PwmApplication pwmApplication,
            final Set<PwmDomain> deletedDomains
    )
    {
        // 1 minute later ( to avoid interrupting any in-progress requests, shutdown any obsoleted domains
        if ( !deletedDomains.isEmpty() )
        {
            pwmApplication.getPwmScheduler().immediateExecuteRunnableInNewThread( () ->
                    {
                        TimeDuration.MINUTE.pause();
                        final Instant startTime = Instant.now();
                        LOGGER.trace( pwmApplication.getSessionLabel(), () -> "shutting down obsoleted domain services" );
                        deletedDomains.forEach( PwmDomain::shutdown );
                        LOGGER.debug( pwmApplication.getSessionLabel(), () -> "shut down obsoleted domain services completed",
                                () -> TimeDuration.fromCurrent( startTime ) );
                    },
                    pwmApplication.getSessionLabel(),
                    "obsoleted domain cleanup" );
        }

    }

    enum DomainModifyCategory
    {
        obsolete,
        unchanged,
        modified,
        created,
    }

    private static Map<DomainModifyCategory, Set<DomainID>> categorizeDomainModifications(
            final AppConfig newConfig,
            final AppConfig oldConfig
    )
    {
        final Map<DomainModifyCategory, Set<DomainID>> types = new EnumMap<>( DomainModifyCategory.class );

        {
            final Set<DomainID> obsoleteDomains = new HashSet<>( oldConfig.getDomainConfigs().keySet() );
            obsoleteDomains.removeAll( newConfig.getDomainConfigs().keySet() );
            types.put( DomainModifyCategory.obsolete, Collections.unmodifiableSet( obsoleteDomains ) );
        }

        {
            final Set<DomainID> createdDomains = new HashSet<>( newConfig.getDomainConfigs().keySet() );
            createdDomains.removeAll( oldConfig.getDomainConfigs().keySet() );
            types.put( DomainModifyCategory.created, Collections.unmodifiableSet( createdDomains ) );
        }

        final Set<DomainID> unchangedDomains = new HashSet<>();
        final Set<DomainID> modifiedDomains = new HashSet<>();
        for ( final DomainID domainID : newConfig.getDomainConfigs().keySet() )
        {
            final DomainConfig newDomainConfig = newConfig.getDomainConfigs().get( domainID );
            final String oldValueHash = oldConfig.getDomainConfigs().get( newDomainConfig.getDomainID() ).getValueHash();

            if ( Objects.equals( oldValueHash, newDomainConfig.getValueHash() ) )
            {
                unchangedDomains.add( domainID );
            }
            else
            {
                modifiedDomains.add( domainID );
            }
        }
        types.put( DomainModifyCategory.unchanged, Collections.unmodifiableSet( unchangedDomains ) );
        types.put( DomainModifyCategory.modified, Collections.unmodifiableSet( modifiedDomains ) );
        return Collections.unmodifiableMap( types );
    }
}
