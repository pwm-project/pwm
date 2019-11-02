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

package password.pwm.i18n;

import password.pwm.PwmConstants;
import password.pwm.util.java.JavaHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;

public enum PwmLocaleBundle
{
    DISPLAY( Display.class ),
    ERRORS( Error.class ),
    MESSAGE( Message.class ),

    CONFIG( Config.class, Flag.AdminOnly ),
    ADMIN( Admin.class, Flag.AdminOnly ),
    HEALTH( Health.class, Flag.AdminOnly ),
    CONFIG_GUIDE( ConfigGuide.class, Flag.AdminOnly ),;

    private final Class<? extends PwmDisplayBundle> theClass;

    enum Flag
    {
        AdminOnly,
    }

    private final Flag[] flags;
    private Set<String> keys;

    PwmLocaleBundle( final Class<? extends PwmDisplayBundle> theClass, final Flag... flags )
    {
        this.theClass = theClass;
        this.flags = flags;
    }

    public Class<? extends PwmDisplayBundle> getTheClass( )
    {
        return theClass;
    }

    public boolean isAdminOnly( )
    {
        return JavaHelper.enumArrayContainsValue( flags, Flag.AdminOnly );
    }

    public static Optional<PwmLocaleBundle> forKey( final String key )
    {
        for ( final PwmLocaleBundle pwmLocaleBundle : PwmLocaleBundle.values() )
        {
            if ( Objects.equals( key, pwmLocaleBundle.name() )
                    || Objects.equals( key, pwmLocaleBundle.getKey() )
            )
            {
                return Optional.of( pwmLocaleBundle );
            }
        }

        return Optional.empty();
    }

    public String getKey()
    {
        return getTheClass().getName();
    }

    public Set<String> getKeys( )
    {
        if ( keys == null )
        {
            final ResourceBundle defaultBundle = ResourceBundle.getBundle( this.getTheClass().getName(), PwmConstants.DEFAULT_LOCALE );
            keys = Collections.unmodifiableSet( new HashSet<>( defaultBundle.keySet() ) );
        }
        return keys;
    }

    public static Collection<PwmLocaleBundle> allValues( )
    {
        return Collections.unmodifiableList( Arrays.asList( PwmLocaleBundle.values() ) );
    }

    public static Collection<PwmLocaleBundle> userFacingValues( )
    {
        final List<PwmLocaleBundle> returnValue = new ArrayList<>( allValues() );
        for ( final Iterator<PwmLocaleBundle> iter = returnValue.iterator(); iter.hasNext(); )
        {
            if ( iter.next().isAdminOnly() )
            {
                iter.remove();
            }
        }
        return Collections.unmodifiableList( returnValue );
    }
}
