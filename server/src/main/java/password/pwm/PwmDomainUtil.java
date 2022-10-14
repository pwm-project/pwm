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
import password.pwm.config.stored.StoredConfigKey;
import password.pwm.config.stored.StoredConfigurationUtil;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
        LOGGER.trace( pwmApplication.getSessionLabel(), () -> "beginning domain initializations" );

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

        LOGGER.trace( pwmApplication.getSessionLabel(), () -> "completed domain initialization for domains",
                TimeDuration.fromCurrent( domainInitStartTime ) );
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

    static void reInitDomains(
            final PwmApplication pwmApplication,
            final AppConfig newConfig,
            final AppConfig oldConfig
    )
            throws PwmUnrecoverableException
    {
        final Map<DomainModifyCategory, Set<DomainID>> categorizedDomains = categorizeDomainModifications( newConfig, oldConfig );

        categorizedDomains.forEach( (  modifyCategory, domainIDSet ) -> domainIDSet.forEach( domainID ->
                LOGGER.trace( pwmApplication.getSessionLabel(), () -> "domain '" + domainID
                        + "' configuration modification detected as: " + modifyCategory ) ) );

        final Set<PwmDomain> deletedDomains = pwmApplication.domains().entrySet().stream()
                .filter( e -> categorizedDomains.get( DomainModifyCategory.removed ).contains( e.getKey() ) )
                .map( Map.Entry::getValue ).collect( Collectors.toSet() );


        final Set<PwmDomain> newDomains = pwmApplication.domains().entrySet().stream()
                .filter( e -> categorizedDomains.get( DomainModifyCategory.created ).contains( e.getKey() ) )
                .map( Map.Entry::getValue ).collect( Collectors.toSet() );


        final Map<DomainID, PwmDomain> returnDomainMap = new TreeMap<>( pwmApplication.domains().entrySet().stream()
                .filter( e -> categorizedDomains.get( DomainModifyCategory.unchanged ).contains( e.getKey() ) )
                .collect( Collectors.toMap( Map.Entry::getKey, Map.Entry::getValue ) ) );

        for ( final DomainID modifiedDomainID : categorizedDomains.get( DomainModifyCategory.modified ) )
        {
            LOGGER.trace( pwmApplication.getSessionLabel(), () -> "domain '" + modifiedDomainID
                    + "' configuration has changed and requires a restart" );
            deletedDomains.add( pwmApplication.domains().get( modifiedDomainID ) );
            final PwmDomain newDomain = new PwmDomain( pwmApplication, modifiedDomainID );
            newDomains.add( newDomain );
            returnDomainMap.put( modifiedDomainID, newDomain );
        }

        pwmApplication.setDomains( returnDomainMap );

        if ( newDomains.isEmpty() && deletedDomains.isEmpty() )
        {
            LOGGER.debug( pwmApplication.getSessionLabel(), () -> "no domain-level settings have been changed, restart of domain services is not required" );
        }

        if ( !newDomains.isEmpty() )
        {
            initDomains( pwmApplication, newDomains );
        }

        if ( !deletedDomains.isEmpty() )
        {
            processDeletedDomains( pwmApplication, deletedDomains );
        }
    }

    private static void processDeletedDomains(
            final PwmApplication pwmApplication,
            final Set<PwmDomain> deletedDomains
    )
    {
        if ( deletedDomains.isEmpty() )
        {
            return;
        }

        final Instant startTime = Instant.now();
        LOGGER.trace( pwmApplication.getSessionLabel(), () -> "shutting down obsoleted domain services" );
        deletedDomains.forEach( PwmDomain::shutdown );
        LOGGER.debug( pwmApplication.getSessionLabel(), () -> "shut down obsoleted domain services completed",
                TimeDuration.fromCurrent( startTime ) );
    }

    enum DomainModifyCategory
    {
        removed,
        unchanged,
        modified,
        created,
    }

    public static Map<DomainModifyCategory, Set<DomainID>> categorizeDomainModifications(
            final AppConfig newConfig,
            final AppConfig oldConfig
    )
    {
        {
            final Instant newInstant = newConfig.getStoredConfiguration().modifyTime();
            final Instant oldInstant = oldConfig.getStoredConfiguration().modifyTime();
            if ( newInstant != null && oldInstant != null && newInstant.isBefore( oldInstant ) )
            {
                throw new IllegalStateException( "refusing request to categorize changes due to oldConfig "
                        + "being newer than new config" );
            }
        }

        final Set<StoredConfigKey> modifiedValues = StoredConfigurationUtil.changedValues(
                newConfig.getStoredConfiguration(),
                oldConfig.getStoredConfiguration() );

        return CLASSIFIERS.entrySet().stream()
                .collect( Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().categorize( newConfig, oldConfig, modifiedValues )
                ) );
    }

    interface DomainModificationClassifier
    {
        Set<DomainID> categorize( AppConfig newConfig, AppConfig oldConfig, Set<StoredConfigKey> modifiedValues );
    }

    private static final Map<DomainModifyCategory, DomainModificationClassifier> CLASSIFIERS = Map.of(
            DomainModifyCategory.removed, new RemovalClassifier(),
            DomainModifyCategory.created, new CreationClassifier(),
            DomainModifyCategory.unchanged, new UnchangedClassifier(),
            DomainModifyCategory.modified, new ModifiedClassifier() );

    private static class RemovalClassifier implements DomainModificationClassifier
    {
        @Override
        public Set<DomainID> categorize(
                final AppConfig newConfig,
                final AppConfig oldConfig,
                final Set<StoredConfigKey> modifiedValues
        )
        {
            final Set<DomainID> removedDomains = new HashSet<>( oldConfig.getDomainConfigs().keySet() );
            removedDomains.removeAll( newConfig.getDomainConfigs().keySet() );
            return CollectionUtil.stripNulls( removedDomains );
        }
    }

    private static class CreationClassifier implements DomainModificationClassifier
    {
        @Override
        public Set<DomainID> categorize(
                final AppConfig newConfig,
                final AppConfig oldConfig,
                final Set<StoredConfigKey> modifiedValues
        )
        {
            final Set<DomainID> createdDomains = new HashSet<>( newConfig.getDomainConfigs().keySet() );
            createdDomains.removeAll( oldConfig.getDomainConfigs().keySet() );
            return CollectionUtil.stripNulls( createdDomains );
        }
    }

    private static class UnchangedClassifier implements DomainModificationClassifier
    {
        @Override
        public Set<DomainID> categorize(
                final AppConfig newConfig,
                final AppConfig oldConfig,
                final Set<StoredConfigKey> modifiedValues
        )
        {
            final Set<DomainID> persistentDomains = new HashSet<>(
                    CollectionUtil.setUnion(
                            newConfig.getDomainConfigs().keySet(),
                            oldConfig.getDomainConfigs().keySet() ) );
            persistentDomains.removeAll( StoredConfigKey.uniqueDomains( modifiedValues ) );
            return Set.copyOf( persistentDomains );
        }
    }

    private static class ModifiedClassifier implements DomainModificationClassifier
    {
        @Override
        public Set<DomainID> categorize(
                final AppConfig newConfig,
                final AppConfig oldConfig,
                final Set<StoredConfigKey> modifiedValues
        )
        {
            final Set<DomainID> modifiedDomains = new HashSet<>(
                    CollectionUtil.setUnion(
                            newConfig.getDomainConfigs().keySet(),
                            oldConfig.getDomainConfigs().keySet() ) );
            modifiedDomains.retainAll( StoredConfigKey.uniqueDomains( modifiedValues ) );
            return Set.copyOf( modifiedDomains );
        }
    }
}
