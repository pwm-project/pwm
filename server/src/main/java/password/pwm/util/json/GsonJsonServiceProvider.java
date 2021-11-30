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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.squareup.moshi.Types;
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.logging.PwmLogger;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.List;
import java.util.Map;

class GsonJsonServiceProvider implements JsonProvider
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( GsonJsonServiceProvider.class );

    private static final Gson GENERIC_GSON = GsonJsonAdaptors.registerTypeAdapters( new GsonBuilder() )
            .disableHtmlEscaping()
            .create();

    private static Gson getGson( final Flag... flags )
    {
        if ( flags == null || flags.length == 0 )
        {
            return GENERIC_GSON;
        }

        final GsonBuilder gsonBuilder = GsonJsonAdaptors.registerTypeAdapters( new GsonBuilder() );

        if ( JavaHelper.enumArrayContainsValue( flags, JsonProvider.Flag.PrettyPrint ) )
        {
            gsonBuilder.setPrettyPrinting();
        }

        return gsonBuilder.create();
    }

    @Override
    public <T> T deserialize( final String jsonString, final TypeToken typeToken )
    {
        return getGson().fromJson( jsonString, typeToken.getType() );
    }

    @Override
    public <T> T deserialize( final String jsonString, final Class<T> classOfT )
    {
        return getGson().fromJson( jsonString, classOfT );
    }

    @Override
    public <V> List<V> deserializeList( final String jsonString, final Class<V> classOfV )
    {
        final Type type = Types.newParameterizedType( List.class, classOfV );
        return List.copyOf( getGson().fromJson( jsonString, type ) );
    }

    @Override
    public <K, V> Map<K, V> deserializeMap( final String jsonString, final Class<K> classOfK, final Class<V> classOfV )
    {
        final Type type = Types.newParameterizedType( Map.class, classOfK, classOfV );
        return Map.copyOf( getGson().fromJson( jsonString, type ) );
    }


    @Override
    public Map<String, String> deserializeStringMap( final String jsonString )
    {
        final Map<String, String> readMap = getGson().fromJson( jsonString, new TypeToken<Map<String, String>>()
        {
        }.getType() );

        return Map.copyOf( CollectionUtil.stripNulls( readMap ) );
    }

    @Override
    public List<String> deserializeStringList( final String jsonString )
    {
        final List<String> readList = getGson().fromJson( jsonString, new TypeToken<List<String>>()
        {
        }.getType() );

        return List.copyOf( CollectionUtil.stripNulls( readList ) );

    }

    @Override
    public Map<String, Object> deserializeMap( final String jsonString )
    {
        return Map.copyOf( getGson().fromJson( jsonString, new TypeToken<Map<String, Object>>()
        {
        }.getType() ) );
    }


    @Override
    public <T> String serialize( final T srcObject, final Flag... flags )
    {
        return getGson( flags ).toJson( srcObject );
    }

    @Override
    public <T> String serialize( final T srcObject, final Class<T> classOfT, final Flag... flags )
    {
        return getGson( flags ).toJson( srcObject );
    }

    @Override
    public <T> String serialize( final T srcObject, final Class<T> classOfT, final Type type, final Flag... flags )
    {
        final Type types = Types.newParameterizedType( classOfT, type );
        return getGson( flags ).toJson( srcObject, types );
    }

    @Override
    public String serializeMap( final Map srcObject, final Flag... flags )
    {
        return getGson( flags ).toJson( srcObject );
    }


    @Override
    public <K, V> String serializeMap( final Map<K, V> srcObject, final Class<K> parameterizedKey, final Class<V> parameterizedValue, final Flag... flags )
    {
        final Type type = Types.newParameterizedType( Map.class, parameterizedKey, parameterizedValue );
        return getGson( flags ).toJson( srcObject, type );
    }

    @Override
    public <V> String serializeStringMap( final V srcObject, final Flag... flags )
    {
        final Type type = new TypeToken<Map<String, String>>()
        {
        }.getType();
        return getGson( flags ).toJson( srcObject, type );
    }



    @Override
    public <V> String serializeCollection( final Collection<V> srcObject, final Flag... flags )
    {
        return getGson( flags ).toJson( srcObject );
    }
}
