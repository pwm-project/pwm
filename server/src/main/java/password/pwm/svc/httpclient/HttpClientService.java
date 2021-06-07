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
import password.pwm.bean.DomainID;
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
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

public class HttpClientService extends AbstractPwmService implements PwmService
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( HttpClientService.class );

    private Class<PwmHttpClientProvider> httpClientClass;

    private final Map<PwmHttpClientConfiguration, ThreadLocal<PwmHttpClientProvider>> clients = new ConcurrentHashMap<>(  );
    private final Map<PwmHttpClientProvider, Object> issuedClients = Collections.synchronizedMap( new WeakHashMap<>(  ) );

    private final StatisticCounterBundle<StatsKey> stats = new StatisticCounterBundle<>( StatsKey.class );

    enum StatsKey
    {
        requests,
        requestBytes,
        responseBytes,
        createdClients,
        reusedClients,
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
    public void close()
    {
        for ( final PwmHttpClient pwmHttpClient : new HashSet<>( issuedClients.keySet() ) )
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
    }

    public PwmHttpClient getPwmHttpClient()
            throws PwmUnrecoverableException
    {
        return this.getPwmHttpClient( PwmHttpClientConfiguration.builder().build() );
    }

    public PwmHttpClient getPwmHttpClient( final PwmHttpClientConfiguration pwmHttpClientConfiguration )
            throws PwmUnrecoverableException
    {
        Objects.requireNonNull( pwmHttpClientConfiguration );

        final ThreadLocal<PwmHttpClientProvider> threadLocal = clients.computeIfAbsent(
                pwmHttpClientConfiguration,
                clientConfig -> new ThreadLocal<>() );

        final PwmHttpClient existingClient = threadLocal.get();
        if ( existingClient != null && existingClient.isOpen() )
        {
            stats.increment( StatsKey.reusedClients );
            return existingClient;
        }

        try
        {
            final PwmHttpClientProvider newClient = httpClientClass.getDeclaredConstructor().newInstance();
            newClient.init( getPwmApplication(), this, pwmHttpClientConfiguration );
            issuedClients.put( newClient, null );
            threadLocal.set( newClient );
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
        final Map<String, String> debugMap = new HashMap<>( stats.debugStats() );
        debugMap.put( "weakReferences", Integer.toString( issuedClients.size() ) );
        debugMap.put( "referencedConfigs", Integer.toString( clients.size() ) );
        return ServiceInfoBean.builder()
                .debugProperties( debugMap )
                .build();

    }
}
