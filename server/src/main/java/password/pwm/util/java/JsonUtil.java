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
import com.novell.ldapchai.cr.ChallengeSet;
import password.pwm.PwmConstants;
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
import java.util.TimeZone;

public class JsonUtil
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( JsonUtil.class );

    public enum Flag
    {
        PrettyPrint,
        HtmlEscape,
    }

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

        if ( !JavaHelper.enumArrayContainsValue( flags, Flag.HtmlEscape ) )
        {
            gsonBuilder.disableHtmlEscaping();
        }

        if ( JavaHelper.enumArrayContainsValue( flags, Flag.PrettyPrint ) )
        {
            gsonBuilder.setPrettyPrinting();
        }

        return gsonBuilder.create();
    }

    public static <T> T deserialize( final String jsonString, final TypeToken typeToken )
    {
        return JsonUtil.getGson().fromJson( jsonString, typeToken.getType() );
    }

    public static <T> T deserialize( final String jsonString, final Type type )
    {
        return JsonUtil.getGson().fromJson( jsonString, type );
    }

    public static List<String> deserializeStringList( final String jsonString )
    {
        return JsonUtil.getGson().fromJson( jsonString, new TypeToken<List<Object>>()
        {
        }.getType() );
    }

    public static Map<String, String> deserializeStringMap( final String jsonString )
    {
        return JsonUtil.getGson().fromJson( jsonString, new TypeToken<Map<String, String>>()
        {
        }.getType() );
    }

    public static Map<String, Object> deserializeStringObjectMap( final String jsonString )
    {
        return JsonUtil.getGson().fromJson( jsonString, new TypeToken<Map<String, Object>>()
        {
        }.getType() );
    }

    public static Map<String, Object> deserializeMap( final String jsonString )
    {
        return JsonUtil.getGson().fromJson( jsonString, new TypeToken<Map<String, Object>>()
        {
        }.getType() );
    }

    public static <T> T deserialize( final String json, final Class<T> classOfT )
    {
        return JsonUtil.getGson().fromJson( json, classOfT );
    }

    public static String serialize( final Serializable object, final Flag... flags )
    {
        return JsonUtil.getGson( flags ).toJson( object );
    }

    public static String serializeMap( final Map object, final Flag... flags )
    {
        return JsonUtil.getGson( flags ).toJson( object );
    }

    public static String serializeCollection( final Collection object, final Flag... flags )
    {
        return JsonUtil.getGson( flags ).toJson( object );
    }

    /**
     * Gson Serializer for {@link java.security.cert.X509Certificate}.  Neccessary because sometimes X509Certs have circular refecences
     * and the default gson serializer will cause a {@code java.lang.StackOverflowError}.  Standard Base64 encoding of
     * the cert is used as the json format.
     */
    private static class X509CertificateAdapter implements JsonSerializer<X509Certificate>, JsonDeserializer<X509Certificate>
    {
        private X509CertificateAdapter( )
        {
        }

        public synchronized JsonElement serialize( final X509Certificate cert, final Type type, final JsonSerializationContext jsonSerializationContext )
        {
            try
            {
                return new JsonPrimitive( StringUtil.base64Encode( cert.getEncoded() ) );
            }
            catch ( final CertificateEncodingException e )
            {
                throw new IllegalStateException( "unable to json-encode certificate: " + e.getMessage() );
            }
        }

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
     * GsonSerializer that stores dates in ISO 8601 format, with a deserialier that also reads local-platform format reading.
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

        public synchronized JsonElement serialize( final Date date, final Type type, final JsonSerializationContext jsonSerializationContext )
        {
            return new JsonPrimitive( ISO_DATE_FORMAT.format( date.toInstant() ) );
        }

        public synchronized Date deserialize( final JsonElement jsonElement, final Type type, final JsonDeserializationContext jsonDeserializationContext )
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
     * GsonSerializer that stores instants in ISO 8601 format, with a deserialier that also reads local-platform format reading.
     */
    private static class InstantTypeAdapter implements JsonSerializer<Instant>, JsonDeserializer<Instant>
    {
        private InstantTypeAdapter( )
        {
        }

        public synchronized JsonElement serialize( final Instant instant, final Type type, final JsonSerializationContext jsonSerializationContext )
        {
            return new JsonPrimitive( JavaHelper.toIsoDate( instant ) );
        }

        public synchronized Instant deserialize( final JsonElement jsonElement, final Type type, final JsonDeserializationContext jsonDeserializationContext )
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

    private static class ChallengeSetAdapter implements JsonDeserializer<ChallengeSet>
    {
        private ChallengeSetAdapter( )
        {
        }

        public synchronized ChallengeSet deserialize( final JsonElement jsonElement, final Type type, final JsonDeserializationContext jsonDeserializationContext )
        {
            try
            {
                return null;
            }
            catch ( final Exception e )
            {
                LOGGER.debug( () -> "unable to parse stored json ChallengeSet.class timestamp '" + jsonElement.getAsString() + "' error: " + e.getMessage() );
                throw new JsonParseException( e );
            }
        }
    }

    private static class ByteArrayToBase64TypeAdapter implements JsonSerializer<byte[]>, JsonDeserializer<byte[]>
    {
        public byte[] deserialize( final JsonElement json, final Type typeOfT, final JsonDeserializationContext context ) throws JsonParseException
        {
            try
            {
                return StringUtil.base64Decode( json.getAsString() );
            }
            catch ( final IOException e )
            {
                final String errorMsg = "io stream error while de-serializing byte array: " + e.getMessage();
                LOGGER.error( () -> errorMsg );
                throw new JsonParseException( errorMsg, e );
            }
        }

        public JsonElement serialize( final byte[] src, final Type typeOfSrc, final JsonSerializationContext context )
        {
            try
            {
                return new JsonPrimitive( StringUtil.base64Encode( src, StringUtil.Base64Options.GZIP ) );
            }
            catch ( final IOException e )
            {
                final String errorMsg = "io stream error while serializing byte array: " + e.getMessage();
                LOGGER.error( () -> errorMsg );
                throw new JsonParseException( errorMsg, e );
            }
        }
    }

    private static class PasswordDataTypeAdapter implements JsonSerializer<PasswordData>, JsonDeserializer<PasswordData>
    {
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
        public PwmLdapVendor deserialize( final JsonElement json, final Type typeOfT, final JsonDeserializationContext context ) throws JsonParseException
        {
            return PwmLdapVendor.fromString( json.getAsString() );
        }

        public JsonElement serialize( final PwmLdapVendor src, final Type typeOfSrc, final JsonSerializationContext context )
        {
            return new JsonPrimitive( src.name() );
        }
    }

    private static GsonBuilder registerTypeAdapters( final GsonBuilder gsonBuilder )
    {
        gsonBuilder.registerTypeAdapter( Date.class, new DateTypeAdapter() );
        gsonBuilder.registerTypeAdapter( Instant.class, new InstantTypeAdapter() );
        gsonBuilder.registerTypeAdapter( X509Certificate.class, new X509CertificateAdapter() );
        gsonBuilder.registerTypeAdapter( byte[].class, new ByteArrayToBase64TypeAdapter() );
        gsonBuilder.registerTypeAdapter( PasswordData.class, new PasswordDataTypeAdapter() );
        gsonBuilder.registerTypeAdapter( PwmLdapVendorTypeAdaptor.class, new PwmLdapVendorTypeAdaptor() );
        return gsonBuilder;
    }

    public static <T> T cloneUsingJson( final Serializable srcObject, final Class<T> classOfT )
    {
        final String asJson = JsonUtil.serialize( srcObject );
        return JsonUtil.deserialize( asJson, classOfT );
    }
}
