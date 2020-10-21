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

package password.pwm.util;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class FormMap implements Serializable, Map<String, String>
{
    private final HashMap<String, String> backingMap = new HashMap<>();

    public FormMap( )
    {
    }

    public FormMap( final Map inputStringMap )
    {
        if ( inputStringMap != null )
        {
            for ( final Object entrySet : inputStringMap.entrySet() )
            {
                final Entry entry = ( Entry ) entrySet;
                final Object key = entry.getKey();
                if ( key != null )
                {
                    final Object value = entry.getValue();
                    if ( value != null )
                    {
                        backingMap.put( key.toString(), value.toString() );
                    }
                }
            }
        }
    }

    @Override
    public int size( )
    {
        return backingMap.size();
    }

    @Override
    public boolean isEmpty( )
    {
        return backingMap.isEmpty();
    }

    @Override
    public boolean containsKey( final Object key )
    {
        return backingMap.containsKey( key );
    }

    @Override
    public boolean containsValue( final Object value )
    {
        return backingMap.containsKey( value );
    }

    @Override
    public String get( final Object key )
    {
        return backingMap.get( key );
    }

    public String get( final String key, final String defaultValue )
    {
        return backingMap.getOrDefault( key, defaultValue );
    }

    @Override
    public String put( final String key, final String value )
    {
        return backingMap.put( key, value );
    }

    @Override
    public String remove( final Object key )
    {
        return backingMap.remove( key );
    }

    @Override
    public void putAll( final Map<? extends String, ? extends String> m )
    {
        backingMap.putAll( m );
    }

    @Override
    public void clear( )
    {
        backingMap.clear();
    }

    @Override
    public Set<String> keySet( )
    {
        return backingMap.keySet();
    }

    @Override
    public Collection<String> values( )
    {
        return backingMap.values();
    }

    @Override
    public Set<Entry<String, String>> entrySet( )
    {
        return backingMap.entrySet();
    }
}
