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

package password.pwm.svc.httpclient;

import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.DomainID;
import password.pwm.bean.SessionLabel;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthRecord;
import password.pwm.svc.AbstractPwmService;
import password.pwm.svc.PwmService;
import password.pwm.util.java.StatisticCounterBundle;
import password.pwm.util.logging.PwmLogger;

import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public class HttpClientService extends AbstractPwmService implements PwmService
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( HttpClientService.class );

    private Class<PwmHttpClientProvider> httpClientClass;

    private final Set<PwmHttpClient> issuedClients = Collections.synchronizedSet( Collections.newSetFromMap( new WeakHashMap<>() ) );

    private final StatisticCounterBundle<StatsKey> stats = new StatisticCounterBundle<>( StatsKey.class );

    enum StatsKey
    {
        requests,
        requestBytes,
        responseBytes,
        createdClients,
    }

    public HttpClientService()
    {
    }

    @Override
    protected Set<PwmApplication.Condition> openConditions()
    {
        return Collections.emptySet();
    }

    @Override
    protected STATUS postAbstractInit( final PwmApplication pwmApplication, final DomainID domainID )
            throws PwmException
    {
        final String implClassName = pwmApplication.getConfig().readAppProperty( AppProperty.HTTP_CLIENT_IMPLEMENTATION );
        try
        {
            this.httpClientClass = ( Class<PwmHttpClientProvider> ) this.getClass().getClassLoader().loadClass( implClassName );
        }
        catch ( final ClassNotFoundException e )
        {
            final ErrorInformation errorInformation = new ErrorInformation(
                    PwmError.ERROR_INTERNAL, "unable to load pwmHttpClass implementation: " + e.getMessage() );
            setStartupError( errorInformation );
            throw new PwmUnrecoverableException( errorInformation );
        }

        return STATUS.OPEN;
    }

    @Override
    public void shutdownImpl()
    {
        for ( final PwmHttpClient pwmHttpClient : new HashSet<>( issuedClients ) )
        {
            try
            {
                pwmHttpClient.close();
            }
            catch ( final Exception e )
            {
                LOGGER.debug( () -> "error closing pwmHttpClient instance: " + e.getMessage() );
            }
        }
        issuedClients.clear();
    }

    public PwmHttpClient getPwmHttpClient( final SessionLabel sessionLabel )
            throws PwmUnrecoverableException
    {
        return this.getPwmHttpClient( null, sessionLabel );
    }

    public PwmHttpClient getPwmHttpClient(
            final PwmHttpClientConfiguration pwmHttpClientConfiguration,
            final SessionLabel sessionLabel
    )
            throws PwmUnrecoverableException
    {
        if ( status() != STATUS.OPEN )
        {
            throw new PwmUnrecoverableException( PwmError.ERROR_SERVICE_NOT_AVAILABLE, "unable to create new pwmHttpClient, service is closed" );
        }

        final PwmHttpClientConfiguration effectiveConfig = pwmHttpClientConfiguration == null
                ? PwmHttpClientConfiguration.builder().build()
                : pwmHttpClientConfiguration;

        try
        {
            final PwmHttpClientProvider newClient = httpClientClass.getDeclaredConstructor().newInstance();
            newClient.init( getPwmApplication(), this, effectiveConfig, sessionLabel );
            stats.increment( StatsKey.createdClients );
            return newClient;
        }
        catch ( final InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e )
        {
            throw PwmUnrecoverableException.newException( PwmError.ERROR_INTERNAL, "unable to initialize pwmHttpClass implementation: " + e.getMessage() );
        }
    }

    protected StatisticCounterBundle<StatsKey> getStats()
    {
        return stats;
    }

    @Override
    public List<HealthRecord> serviceHealthCheck()
    {
        return Collections.emptyList();
    }

    @Override
    public ServiceInfoBean serviceInfo()
    {
        final Map<String, String> debugMap = new HashMap<>( stats.debugStats( PwmConstants.DEFAULT_LOCALE ) );
        debugMap.put( "issuedClients", Integer.toString( issuedClients.size() ) );
        debugMap.put( "openClients", Long.toString( openClients() ) );
        return ServiceInfoBean.builder()
                .debugProperties( debugMap )
                .build();

    }

    private long openClients()
    {
        return new HashSet<>( issuedClients ).stream()
                .filter( PwmHttpClient::isOpen )
                .count();
    }
}
