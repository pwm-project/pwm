/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2019 The PWM Project
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

import password.pwm.PwmApplication;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthRecord;
import password.pwm.svc.PwmService;
import password.pwm.util.logging.PwmLogger;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class HttpClientService implements PwmService
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( HttpClientService.class );

    private PwmApplication pwmApplication;

    private final Map<PwmHttpClientConfiguration, ThreadLocal<PwmHttpClient>> clients = new ConcurrentHashMap<>(  );
    private final Map<PwmHttpClient, Object> issuedClients = Collections.synchronizedMap( new WeakHashMap<>(  ) );

    private final Map<StatsKey, AtomicInteger> stats = new HashMap<>( );

    enum StatsKey
    {
        createdClients,
        reusedClients,
    }

    public HttpClientService()
            throws PwmUnrecoverableException
    {
        for ( final StatsKey statsKey : StatsKey.values() )
        {
            stats.put( statsKey, new AtomicInteger( 0 ) );
        }
    }

    @Override
    public STATUS status()
    {
        return STATUS.OPEN;
    }

    @Override
    public void init( final PwmApplication pwmApplication )
            throws PwmException
    {
        this.pwmApplication = pwmApplication;
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

        final ThreadLocal<PwmHttpClient> threadLocal = clients.computeIfAbsent(
                pwmHttpClientConfiguration,
                clientConfig -> new ThreadLocal<>() );

        final PwmHttpClient existingClient = threadLocal.get();
        if ( existingClient != null && !existingClient.isClosed() )
        {
            stats.get( StatsKey.reusedClients ).incrementAndGet();
            return existingClient;
        }

        final PwmHttpClient newClient = new PwmHttpClient( pwmApplication, pwmHttpClientConfiguration );
        issuedClients.put( newClient, null );
        threadLocal.set( newClient );
        stats.get( StatsKey.createdClients ).incrementAndGet();
        return newClient;
    }

    @Override
    public List<HealthRecord> healthCheck()
    {
        return Collections.emptyList();
    }

    @Override
    public ServiceInfoBean serviceInfo()
    {
        final Map<String, String> debugMap = new HashMap<>(  );
        stats.forEach( ( key, value ) -> debugMap.put( key.name(), value.toString() ) );
        debugMap.put( "weakReferences", Integer.toString( issuedClients.size() ) );
        debugMap.put( "referencedConfigs", Integer.toString( clients.size() ) );
        return new ServiceInfoBean( Collections.emptyList(), debugMap );
    }
}
