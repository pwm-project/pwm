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

package password.pwm.util.java;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.reflect.TypeToken;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;
import password.pwm.PwmConstants;
import password.pwm.bean.DomainID;
import password.pwm.error.PwmInternalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.ldap.PwmLdapVendor;
import password.pwm.util.PasswordData;
import password.pwm.util.logging.PwmLogger;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Type;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.text.DateFormat;
import java.text.ParseException;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TimeZone;
import java.util.concurrent.atomic.LongAdder;

public class JsonUtil
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( JsonUtil.class );

    public enum Flag
    {
        PrettyPrint,
    }

    interface PwmJsonServiceProvider
    {
        <T> T deserialize( String json, Class<T> classOfT );

        <T> T deserialize( String jsonString, TypeToken typeToken );

        <T> T deserialize( String jsonString, Type type );

        <V> List<V> deserializeList( String json, Class<V> classOfV );

        <K, V> Map<K, V> deserializeMap( String json, Class<K> classOfK, Class<V> classOfV );

        List<String> deserializeStringList( String jsonString );

        Map<String, String> deserializeStringMap( String jsonString );

        Map<String, Object> deserializeMap( String jsonString );

        String serialize( Serializable object, Flag... flags );

        String serializeMap( Map object, Flag... flags );

        <V> String serializeList( List<V> object, Class<V> parameterizedValue, Flag... flags );

        <K, V> String serializeMap( Map<K, V> object, Class<K> parameterizedKey, Class<V> parameterizedValue, Flag... flags );

        String serializeCollection( Collection object, Flag... flags );

        <T> T cloneUsingJson( Serializable srcObject, Class<T> classOfT );
    }

    private static final PwmJsonServiceProvider GSON_PROVIDER = new GsonPwmJsonServiceProvider();
    private static final PwmJsonServiceProvider MOSHI_PROVIDER = new MoshiPwmJsonServiceProvider();
    private static final PwmJsonServiceProvider PROVIDER = MOSHI_PROVIDER;

    public static <T> T deserialize( final String jsonString, final TypeToken typeToken )
    {
        return PROVIDER.deserialize( jsonString, typeToken );
    }

    public static <T> T deserialize( final String jsonString, final Type type )
    {
        return PROVIDER.deserialize( jsonString, type );
    }

    public static <T> T deserialize( final String json, final Class<T> classOfT )
    {
        return PROVIDER.deserialize( json, classOfT );
    }

    public static <V> List<V> deserializeList( final String json, final Class<V> classOfV )
    {
        return PROVIDER.deserializeList( json, classOfV );
    }

    public static <K, V> Map<K, V> deserializeMap( final String json, final Class<K> classOfK, final Class<V> classOfV )
    {
        return PROVIDER.deserializeMap( json, classOfK, classOfV );
    }

    public static List<String> deserializeStringList( final String jsonString )
    {
        return PROVIDER.deserializeStringList( jsonString );
    }

    public static Map<String, String> deserializeStringMap( final String jsonString )
    {
        return PROVIDER.deserializeStringMap( jsonString );
    }

    public static Map<String, Object> deserializeMap( final String jsonString )
    {
        return PROVIDER.deserializeMap( jsonString );
    }

    public static String serialize( final Serializable object, final Flag... flags )
    {
        return PROVIDER.serialize( object, flags );
    }

    public static String serializeMap( final Map object, final Flag... flags )
    {
        return PROVIDER.serializeMap( object, flags );
    }

    public static String serializeStringMap( final Map<String, String> object, final Flag... flags )
    {
        return PROVIDER.serializeMap( object, String.class, String.class, flags );
    }

    public static <V> String serializeList( final List<V> object, final Class<V> parameterizedKey, final Flag... flags )
    {
        return PROVIDER.serializeList( object, parameterizedKey, flags );
    }

    public static <K, V> String serializeMap( final Map<K, V> object, final Class<K> parameterizedKey, final Class<V> parameterizedValue, final Flag... flags )
    {
        return PROVIDER.serializeMap( object, parameterizedKey, parameterizedValue, flags );
    }

    public static String serializeCollection( final Collection object, final Flag... flags )
    {
        return PROVIDER.serializeCollection( object, flags );
    }

    public static <T> T cloneUsingJson( final Serializable srcObject, final Class<T> classOfT )
    {
        return PROVIDER.cloneUsingJson( srcObject, classOfT );
    }

    private static class MoshiPwmJsonServiceProvider implements PwmJsonServiceProvider
    {
        private static final Moshi GENERIC_MOSHI = getMoshi();

        private static Moshi getMoshi( final Flag... flags )
        {
            if ( GENERIC_MOSHI != null && ( flags == null || flags.length <= 0 ) )
            {
                return GENERIC_MOSHI;
            }

            final Moshi.Builder moshiBuilder = new Moshi.Builder();
            JsonAdaptors.registerTypeAdapters( moshiBuilder );
            return moshiBuilder.build();
        }

        @Override
        public List<String> deserializeStringList( final String jsonString )
        {
            final Moshi moshi = getMoshi();
            final Type type = Types.newParameterizedType( List.class, String.class );
            final JsonAdapter<List<String>> adapter = moshi.adapter( type );

            try
            {
                return List.copyOf( Objects.requireNonNull( adapter.fromJson( jsonString ) ) );
            }
            catch ( final IOException e )
            {
                throw new RuntimeException( e.getMessage() );
            }
        }

        @Override
        public <T> T deserialize( final String jsonString, final Type type )
        {
           return deserializeImpl( jsonString, type );
        }

        public <T> T deserializeImpl( final String jsonString, final Type type )
        {
            final Moshi moshi = getMoshi();
            final JsonAdapter<T> adapter = moshi.adapter( type );

            try
            {
                return adapter.fromJson( jsonString );
            }
            catch ( final IOException e )
            {
                throw new RuntimeException( e.getMessage() );
            }
        }

        @Override
        public Map<String, String> deserializeStringMap( final String jsonString )
        {
            final Type type = Types.newParameterizedType( Map.class, String.class, String.class );
            return Map.copyOf( deserialize( jsonString, type ) );
        }

        @Override
        public Map<String, Object> deserializeMap( final String jsonString )
        {
            final Type type = Types.newParameterizedType( Map.class, String.class, Object.class );
            return Map.copyOf( deserialize( jsonString, type ) );
        }

        @Override
        public <V> List<V> deserializeList( final String json, final Class<V> classOfV )
        {
            final Type type = Types.newParameterizedType( List.class, classOfV );
            return List.copyOf( deserialize( json, type ) );
        }

        @Override
        public <K, V> Map<K, V> deserializeMap( final String json, final Class<K> classOfK, final Class<V> classOfV )
        {
            final Type type = Types.newParameterizedType( Map.class, classOfK, classOfV );
            return Map.copyOf( deserialize( json, type ) );
        }

        @Override
        public <T> T deserialize( final String jsonString, final Class<T> classOfT )
        {
            return deserializeImpl( jsonString, classOfT );
        }

        @Override
        public <T> T deserialize( final String jsonString, final TypeToken typeToken )
        {
            final Type type = Types.newParameterizedType( typeToken.getRawType() );
            return deserialize( jsonString, type );
        }

        private <T> String serialize( final T object, final Type type, final Flag... flags )
        {
            final Moshi moshi = getMoshi();
            final JsonAdapter<T> jsonAdapter = JsonAdaptors.applyFlagsToAdapter( moshi.adapter( type ), flags );
            return jsonAdapter.toJson( object );
        }

        @Override
        public String serialize( final Serializable object, final Flag... flags )
        {
            final Type type;
            if ( object instanceof Collection )
            {
                type = Collection.class;
            }
            else if  ( object instanceof Map )
            {
                type = Map.class;
            }
            else
            {
                type = object.getClass();
            }

            return serialize( object, type, flags );
        }

        @Override
        public String serializeMap( final Map object, final Flag... flags )
        {
            final Type type = Types.newParameterizedType( Map.class );
            return serialize( object, type, flags );
        }

        @Override
        public <V> String serializeList( final List<V> object, final Class<V> parameterizedValue, final Flag... flags )
        {
            final Type type = Types.newParameterizedType( List.class, parameterizedValue );
            return serialize( object, type );
        }

        @Override
        public <K, V> String serializeMap( final Map<K, V> object, final Class<K> parameterizedKey, final Class<V> parameterizedValue, final Flag... flags )
        {
            final Type type = Types.newParameterizedType( Map.class, parameterizedKey, parameterizedValue );
            return serialize( object, type );
        }

        @Override
        public String serializeCollection( final Collection object, final Flag... flags )
        {
            final Type type = Types.subtypeOf( Collection.class );
            return serialize( object, type, flags );
        }

        @Override
        public <T> T cloneUsingJson( final Serializable srcObject, final Class<T> classOfT )
        {
            final String jsonObj = this.serialize( srcObject );
            return this.deserialize( jsonObj, classOfT );
        }


    }

    private static class GsonPwmJsonServiceProvider implements PwmJsonServiceProvider
    {
        private static final Gson GENERIC_GSON = registerTypeAdapters( new GsonBuilder() )
                .disableHtmlEscaping()
                .create();

        private static Gson getGson( final Flag... flags )
        {
            if ( flags == null || flags.length == 0 )
            {
                return GENERIC_GSON;
            }

            final GsonBuilder gsonBuilder = registerTypeAdapters( new GsonBuilder() );

            if ( JavaHelper.enumArrayContainsValue( flags, Flag.PrettyPrint ) )
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
        public <T> T deserialize( final String jsonString, final Type type )
        {
            return getGson().fromJson( jsonString, type );
        }

        @Override
        public <T> T deserialize( final String json, final Class<T> classOfT )
        {
            return getGson().fromJson( json, classOfT );
        }

        @Override
        public <V> List<V> deserializeList( final String json, final Class<V> classOfV )
        {
            final Type type = Types.newParameterizedType( List.class, classOfV );
            return List.copyOf( getGson().fromJson( json, type ) );
        }

        @Override
        public <K, V> Map<K, V> deserializeMap( final String json, final Class<K> classOfK, final Class<V> classOfV )
        {
            final Type type = Types.newParameterizedType( Map.class, classOfK, classOfV );
            return Map.copyOf( getGson().fromJson( json, type ) );
        }


        @Override
        public Map<String, String> deserializeStringMap( final String jsonString )
        {
            return Map.copyOf( getGson().fromJson( jsonString, new TypeToken<Map<String, String>>()
            {
            }.getType() ) );
        }

        @Override
        public Map<String, Object> deserializeMap( final String jsonString )
        {
            return Map.copyOf( getGson().fromJson( jsonString, new TypeToken<Map<String, Object>>()
            {
            }.getType() ) );
        }

        @Override
        public String serialize( final Serializable object, final Flag... flags )
        {
            return getGson( flags ).toJson( object );
        }

        @Override
        public String serializeMap( final Map object, final Flag... flags )
        {
            return getGson( flags ).toJson( object );
        }


        @Override
        public <V> String serializeList( final List<V> object, final Class<V> parameterizedValue, final Flag... flags )
        {
            final Type type = Types.newParameterizedType( List.class, parameterizedValue );
            return getGson( flags ).toJson( object, type );
        }

        @Override
        public <K, V> String serializeMap( final Map<K, V> object, final Class<K> parameterizedKey, final Class<V> parameterizedValue, final Flag... flags )
        {
            final Type type = Types.newParameterizedType( Map.class, parameterizedKey, parameterizedValue );
            return getGson( flags ).toJson( object, type );
        }

        @Override
        public String serializeCollection( final Collection object, final Flag... flags )
        {
            return getGson( flags ).toJson( object );
        }

        public List<String> deserializeStringList( final String jsonString )
        {
            return List.copyOf( getGson().fromJson( jsonString, new TypeToken<List<Object>>()
            {
            }.getType() ) );
        }

        public <T> T cloneUsingJson( final Serializable srcObject, final Class<T> classOfT )
        {
            final String asJson = JsonUtil.serialize( srcObject );
            return JsonUtil.deserialize( asJson, classOfT );
        }


        /**
         * Gson Serializer for {@link java.security.cert.X509Certificate}.  Necessary because sometimes X509Certs have circular references
         * and the default gson serializer will cause a {@code java.lang.StackOverflowError}.  Standard Base64 encoding of
         * the cert is used as the json format.
         */
        private static class X509CertificateAdapter implements JsonSerializer<X509Certificate>, JsonDeserializer<X509Certificate>
        {
            private X509CertificateAdapter( )
            {
            }

            @Override
            public JsonElement serialize( final X509Certificate cert, final Type type, final JsonSerializationContext jsonSerializationContext )
            {
                try
                {
                    return new JsonPrimitive( StringUtil.stripAllWhitespace( StringUtil.base64Encode( cert.getEncoded() ) ) );
                }
                catch ( final PwmInternalException | CertificateEncodingException e )
                {
                    throw new IllegalStateException( "unable to json-encode certificate: " + e.getMessage() );
                }
            }

            @Override
            public X509Certificate deserialize( final JsonElement jsonElement, final Type type, final JsonDeserializationContext jsonDeserializationContext )
                    throws JsonParseException
            {
                try
                {
                    final CertificateFactory certificateFactory = CertificateFactory.getInstance( "X.509" );
                    return ( X509Certificate ) certificateFactory.generateCertificate( new ByteArrayInputStream( StringUtil.base64Decode(
                            jsonElement.getAsString() ) ) );
                }
                catch ( final Exception e )
                {
                    throw new JsonParseException( "unable to parse x509certificate: " + e.getMessage() );
                }
            }
        }

        /**
         * GsonSerializer that stores dates in ISO 8601 format, with a deserializer that also reads local-platform format reading.
         */
        private static class DateTypeAdapter implements JsonSerializer<Date>, JsonDeserializer<Date>
        {
            private static final PwmDateFormat ISO_DATE_FORMAT = PwmDateFormat.newPwmDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss'Z'",
                    PwmConstants.DEFAULT_LOCALE,
                    TimeZone.getTimeZone( "Zulu" ) );

            private DateFormat getGsonDateFormat()
            {
                final DateFormat gsonDateFormat = DateFormat.getDateTimeInstance( DateFormat.DEFAULT, DateFormat.DEFAULT );
                gsonDateFormat.setTimeZone( TimeZone.getDefault() );
                return gsonDateFormat;
            }

            private DateTypeAdapter( )
            {
            }

            @Override
            public JsonElement serialize( final Date date, final Type type, final JsonSerializationContext jsonSerializationContext )
            {
                return new JsonPrimitive( ISO_DATE_FORMAT.format( date.toInstant() ) );
            }

            @Override
            public Date deserialize( final JsonElement jsonElement, final Type type, final JsonDeserializationContext jsonDeserializationContext )
            {
                try
                {
                    return Date.from( ISO_DATE_FORMAT.parse( jsonElement.getAsString() ) );
                }
                catch ( final ParseException e )
                {
                    /* noop */
                }

                // for backwards compatibility
                try
                {
                    return getGsonDateFormat().parse( jsonElement.getAsString() );
                }
                catch ( final ParseException e )
                {
                    LOGGER.debug( () -> "unable to parse stored json Date.class timestamp '" + jsonElement.getAsString() + "' error: " + e.getMessage() );
                    throw new JsonParseException( e );
                }
            }
        }

        /**
         * GsonSerializer that stores instants in ISO 8601 format, with a deserializer that also reads local-platform format reading.
         */
        private static class InstantTypeAdapter implements JsonSerializer<Instant>, JsonDeserializer<Instant>
        {
            private InstantTypeAdapter( )
            {
            }

            @Override
            public JsonElement serialize( final Instant instant, final Type type, final JsonSerializationContext jsonSerializationContext )
            {
                return new JsonPrimitive( JavaHelper.toIsoDate( instant ) );
            }

            @Override
            public Instant deserialize( final JsonElement jsonElement, final Type type, final JsonDeserializationContext jsonDeserializationContext )
            {
                try
                {
                    return JavaHelper.parseIsoToInstant( jsonElement.getAsString() );
                }
                catch ( final Exception e )
                {
                    LOGGER.debug( () -> "unable to parse stored json Instant.class timestamp '" + jsonElement.getAsString() + "' error: " + e.getMessage() );
                    throw new JsonParseException( e );
                }
            }
        }

        private static class PasswordDataTypeAdapter implements JsonSerializer<PasswordData>, JsonDeserializer<PasswordData>
        {
            @Override
            public PasswordData deserialize( final JsonElement json, final Type typeOfT, final JsonDeserializationContext context ) throws JsonParseException
            {
                try
                {
                    return new PasswordData( json.getAsString() );
                }
                catch ( final PwmUnrecoverableException e )
                {
                    final String errorMsg = "error while deserializing password data: " + e.getMessage();
                    LOGGER.error( () -> errorMsg );
                    throw new JsonParseException( errorMsg, e );
                }
            }

            @Override
            public JsonElement serialize( final PasswordData src, final Type typeOfSrc, final JsonSerializationContext context )
            {
                try
                {
                    return new JsonPrimitive( src.getStringValue() );
                }
                catch ( final PwmUnrecoverableException e )
                {
                    final String errorMsg = "error while serializing password data: " + e.getMessage();
                    LOGGER.error( () -> errorMsg );
                    throw new JsonParseException( errorMsg, e );
                }
            }
        }

        private static class PwmLdapVendorTypeAdaptor implements JsonSerializer<PwmLdapVendor>, JsonDeserializer<PwmLdapVendor>
        {
            @Override
            public PwmLdapVendor deserialize( final JsonElement json, final Type typeOfT, final JsonDeserializationContext context ) throws JsonParseException
            {
                return PwmLdapVendor.fromString( json.getAsString() );
            }

            @Override
            public JsonElement serialize( final PwmLdapVendor src, final Type typeOfSrc, final JsonSerializationContext context )
            {
                return new JsonPrimitive( src.name() );
            }
        }

        private static class DomainIDTypeAdaptor implements JsonSerializer<DomainID>, JsonDeserializer<DomainID>
        {
            @Override
            public DomainID deserialize( final JsonElement json, final Type typeOfT, final JsonDeserializationContext context ) throws JsonParseException
            {
                final String sValue = json.getAsString();
                if ( DomainID.systemId().toString().equals( sValue ) )
                {
                    return DomainID.systemId();
                }
                return DomainID.create( json.getAsString() );
            }

            @Override
            public JsonElement serialize( final DomainID src, final Type typeOfSrc, final JsonSerializationContext context )
            {
                return new JsonPrimitive( src.toString() );
            }
        }

        private static class LongAdderTypeAdaptor implements JsonSerializer<LongAdder>, JsonDeserializer<LongAdder>
        {
            @Override
            public LongAdder deserialize( final JsonElement json, final Type typeOfT, final JsonDeserializationContext context ) throws JsonParseException
            {
                final long longValue = json.getAsLong();
                final LongAdder longAddr = new LongAdder();
                longAddr.add( longValue );
                return longAddr;
            }

            @Override
            public JsonElement serialize( final LongAdder src, final Type typeOfSrc, final JsonSerializationContext context )
            {
                return new JsonPrimitive( src.longValue() );
            }
        }

        private static GsonBuilder registerTypeAdapters( final GsonBuilder gsonBuilder )
        {
            gsonBuilder.registerTypeAdapter( Date.class, new DateTypeAdapter() );
            gsonBuilder.registerTypeAdapter( Instant.class, new InstantTypeAdapter() );
            gsonBuilder.registerTypeAdapter( X509Certificate.class, new X509CertificateAdapter() );
            gsonBuilder.registerTypeAdapter( PasswordData.class, new PasswordDataTypeAdapter() );
            gsonBuilder.registerTypeAdapter( PwmLdapVendorTypeAdaptor.class, new PwmLdapVendorTypeAdaptor() );
            gsonBuilder.registerTypeAdapter( DomainID.class, new DomainIDTypeAdaptor() );
            gsonBuilder.registerTypeAdapter( LongAdder.class, new LongAdderTypeAdaptor() );
            return gsonBuilder;
        }
    }
}
