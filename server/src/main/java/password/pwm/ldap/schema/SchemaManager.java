/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
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
        catch ( ChaiUnavailableException e )
        {
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_DIRECTORY_UNAVAILABLE, e.getMessage() ) );
        }
        catch ( Exception e )
        {
            final String errorMsg = "error instantiating schema extender: " + e.getMessage();
            LOGGER.error( errorMsg );
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
