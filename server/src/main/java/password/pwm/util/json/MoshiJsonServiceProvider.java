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

package password.pwm.util.json;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;
import password.pwm.util.java.CollectionUtil;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;

class MoshiJsonServiceProvider implements JsonProvider
{
    private static final Moshi GENERIC_MOSHI = getMoshi();

    private static Moshi getMoshi( final Flag... flags )
    {
        if ( GENERIC_MOSHI != null && ( flags == null || flags.length <= 0 ) )
        {
            return GENERIC_MOSHI;
        }

        final Moshi.Builder moshiBuilder = new Moshi.Builder();
        MoshiJsonAdaptors.registerTypeAdapters( moshiBuilder, flags );
        return moshiBuilder.build();
    }

    @Override
    public Map<String, String> deserializeStringMap( final String jsonString )
    {
        final Type type = Types.newParameterizedType( Map.class, String.class, String.class );
        final Map<String, String> deserializeMap = deserializeImpl( jsonString, type );
        return Collections.unmodifiableMap( CollectionUtil.stripNulls( deserializeMap ) );
    }

    @Override
    public List<String> deserializeStringList( final String jsonString )
    {
        final Type type = Types.newParameterizedType( List.class, String.class );
        final List<String> deserializedList = deserializeImpl( jsonString, type );
        return List.copyOf( CollectionUtil.stripNulls( deserializedList ) );
    }

    @Override
    public <V> List<V> deserializeList( final String jsonString, final Class<V> classOfV )
    {
        final Type type = Types.newParameterizedType( List.class, classOfV );
        final List<V> deserializedList = deserializeImpl( jsonString, type );
        return List.copyOf( CollectionUtil.stripNulls( deserializedList ) );
    }

    @Override
    public <K, V> Map<K, V> deserializeMap( final String jsonString, final Class<K> classOfK, final Class<V> classOfV )
    {
        final Type type = Types.newParameterizedType( Map.class, classOfK, classOfV );
        final Map<K, V> deserializeMap = deserializeImpl( jsonString, type );
        return Collections.unmodifiableMap( CollectionUtil.stripNulls( deserializeMap ) );    }

    @Override
    public <T> T deserialize( final String jsonString, final Class<T> classOfT )
    {
        return deserializeImpl( jsonString, classOfT );
    }

    @Override
    public <T> T deserialize( final String jsonString, final Type type )
    {
        return deserializeImpl( jsonString, type );
    }

    @Override
    public <T> String serialize( final T srcObject, final Class<T> classOfT, final Type type, final Flag... flags )
    {
        final Type moshiType = Types.newParameterizedType( classOfT, type );
        return serializeImpl( srcObject, moshiType, flags );
    }

    @Override
    public <K, V> String serializeMap( final Map<K, V> srcObject, final Flag... flags )
    {
        return serializeImpl( srcObject, Map.class, flags );
    }

    @Override
    public <T> String serialize( final T srcObject, final Flag... flags )
    {
        return serializeImpl( srcObject, unknownClassResolver( srcObject ), flags );
    }

    @Override
    public <T> String serialize( final T srcObject, final Class<T> classOfT, final Flag... flags )
    {
        return serializeImpl( srcObject, classOfT, flags );    }

    @Override
    public <K, V> String serializeMap( final Map<K, V> srcObject, final Class<K> parameterizedKey, final Class<V> parameterizedValue, final Flag... flags )
    {
        final Type moshiType = Types.newParameterizedType( Map.class, parameterizedKey, parameterizedValue );
        return serializeImpl( srcObject, moshiType, flags );
    }

    @Override
    public <V> String serializeCollection( final Collection<V> srcObject, final Flag... flags )
    {
        return serializeImpl( srcObject, unknownClassResolver( srcObject ), flags );
    }

    @Override
    public String serializeStringMap( final Map<String, String> srcObject, final Flag... flags )
    {
        final Type moshiType = Types.newParameterizedType( Map.class, String.class, String.class );
        return serializeImpl( srcObject, moshiType, flags );
    }

    private <T> T deserializeImpl( final String jsonString, final Type type )
    {
        final Moshi moshi = getMoshi();
        final JsonAdapter<T> adapter = moshi.adapter( type );

        try
        {
            return adapter.fromJson( jsonString );
        }
        catch ( final IOException e )
        {
            throw new IllegalStateException( e.getMessage(), e );
        }
    }

    private <T> String serializeImpl( final T object, final Type type, final Flag... flags )
    {
        final Moshi moshi = getMoshi();
        final JsonAdapter<T> jsonAdapter = MoshiJsonAdaptors.applyFlagsToAdapter( moshi.adapter( type ), flags );
        return jsonAdapter.toJson( object );
    }

    static Class<?> unknownClassResolver( final Object srcObject )
    {
        if ( srcObject instanceof List )
        {
            return List.class;
        }
        else if ( srcObject instanceof SortedSet )
        {
            return SortedSet.class;
        }
        else if ( srcObject instanceof Set )
        {
            return Set.class;
        }
        else if ( srcObject instanceof SortedMap )
        {
            return SortedMap.class;
        }
        else if ( srcObject instanceof Map )
        {
            return Map.class;
        }
        return srcObject.getClass();
    }
}
