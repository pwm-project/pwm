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

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface JsonProvider
{
    enum Flag
    {
        PrettyPrint,
    }

    <T> T deserialize( String jsonString, Class<T> classOfT );

    <T> T deserialize( String jsonString, Type type );

    <V> List<V> deserializeList( String jsonString, Class<V> classOfV );

    <K, V> Map<K, V> deserializeMap( String jsonString, Class<K> classOfK, Class<V> classOfV );

    Map<String, String> deserializeStringMap( String jsonString );

    List<String> deserializeStringList( String jsonString );

    <T> String serialize( T srcObject, Flag... flags );

    <T> String serialize( T srcObject, Class<T> classOfT, Flag... flags );

    <T> String serialize( T srcObject, Class<T> classOfT, Type type, Flag... flags );

    <K, V> String serializeMap( Map<K, V> srcObject, Flag... flags );

    <K, V> String serializeMap( Map<K, V> srcObject, Class<K> parameterizedKey, Class<V> parameterizedValue, Flag... flags );

    <V> String serializeCollection( Collection<V> srcObject, Flag... flags );

    String serializeStringMap( Map<String, String> srcObject, Flag... flags );

    default <T> T cloneUsingJson( final T srcObject, final Class<T> classOfT )
    {
        final String json = serialize( srcObject, classOfT );
        return deserialize( json, classOfT );
    }

    default Type newParameterizedType( final Type rawType, final Type... parameterizedTypes )
    {
        return new ParameterizedType()
        {
            @Override
            public Type[] getActualTypeArguments()
            {
                return parameterizedTypes;
            }

            @Override
            public Type getRawType()
            {
                return rawType;
            }

            @Override
            public Type getOwnerType()
            {
                return null;
            }
        };
    }

}
