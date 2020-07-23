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

package password.pwm.ldap.schema;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.provider.ChaiProvider;
import com.novell.ldapchai.provider.DirectoryVendor;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.logging.PwmLogger;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SchemaManager
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( SchemaManager.class );

    private static final Map<DirectoryVendor, Class<? extends SchemaExtender>> IMPLEMENTATIONS;

    static
    {
        final Map<DirectoryVendor, Class<? extends SchemaExtender>> implMap = new HashMap<>();
        implMap.put( DirectoryVendor.EDIRECTORY, EdirSchemaExtender.class );
        IMPLEMENTATIONS = Collections.unmodifiableMap( implMap );
    }

    protected static SchemaExtender implForChaiProvider( final ChaiProvider chaiProvider ) throws PwmUnrecoverableException
    {
        if ( !chaiProvider.isConnected() )
        {
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_DIRECTORY_UNAVAILABLE,
                    "provider is not connected" ) );
        }
        try
        {
            if ( chaiProvider.getDirectoryVendor() != DirectoryVendor.EDIRECTORY )
            {
                throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_DIRECTORY_UNAVAILABLE,
                        "directory vendor is not supported" ) );
            }
            final List<String> urls = chaiProvider.getChaiConfiguration().bindURLsAsList();
            if ( urls.size() > 1 )
            {
                throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_DIRECTORY_UNAVAILABLE,
                        "provider used for schema extension must have only a single ldap url defined" ) );
            }

            final DirectoryVendor vendor = chaiProvider.getDirectoryVendor();
            final Class<? extends SchemaExtender> implClass = IMPLEMENTATIONS.get( vendor );
            final SchemaExtender schemaExtenderImpl = implClass.newInstance();
            schemaExtenderImpl.init( chaiProvider );
            return schemaExtenderImpl;
        }
        catch ( final ChaiUnavailableException e )
        {
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_DIRECTORY_UNAVAILABLE, e.getMessage() ) );
        }
        catch ( final Exception e )
        {
            final String errorMsg = "error instantiating schema extender: " + e.getMessage();
            LOGGER.error( () -> errorMsg );
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_INTERNAL, errorMsg ) );
        }

    }

    public static SchemaOperationResult extendSchema( final ChaiProvider chaiProvider ) throws PwmUnrecoverableException
    {
        return implForChaiProvider( chaiProvider ).extendSchema();
    }

    public static SchemaOperationResult checkExistingSchema( final ChaiProvider chaiProvider ) throws PwmUnrecoverableException
    {
        return implForChaiProvider( chaiProvider ).checkExistingSchema();
    }


}
