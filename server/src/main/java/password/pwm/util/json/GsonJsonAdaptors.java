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

import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import password.pwm.PwmConstants;
import password.pwm.bean.DomainID;
import password.pwm.bean.ProfileID;
import password.pwm.error.PwmInternalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.ldap.PwmLdapVendor;
import password.pwm.util.PasswordData;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.PwmDateFormat;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Type;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.text.DateFormat;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.atomic.LongAccumulator;
import java.util.concurrent.atomic.LongAdder;

class GsonJsonAdaptors
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( GsonJsonAdaptors.class );

    static GsonBuilder registerTypeAdapters( final GsonBuilder gsonBuilder )
    {
        gsonBuilder.registerTypeAdapter( Date.class, new DateTypeAdapter() );
        gsonBuilder.registerTypeAdapter( Instant.class, new InstantTypeAdapter() );
        gsonBuilder.registerTypeHierarchyAdapter( X509Certificate.class, new X509CertificateAdapter() );
        gsonBuilder.registerTypeAdapter( PasswordData.class, new PasswordDataTypeAdapter() );
        gsonBuilder.registerTypeAdapter( PwmLdapVendorTypeAdaptor.class, new PwmLdapVendorTypeAdaptor() );
        gsonBuilder.registerTypeAdapter( DomainID.class, new DomainIDTypeAdaptor() );
        gsonBuilder.registerTypeAdapter( ProfileID.class, new ProfileIDTypeAdaptor() );
        gsonBuilder.registerTypeAdapter( LongAdder.class, new LongAdderTypeAdaptor() );
        gsonBuilder.registerTypeAdapter( LongAccumulator.class, new LongAccumulatorTypeAdaptor() );
        gsonBuilder.registerTypeAdapter( TimeDuration.class, new TimeDurationAdaptor() );
        gsonBuilder.registerTypeAdapter( Duration.class, new DurationAdaptor() );
        return gsonBuilder;
    }

    /**
     * Gson Serializer for {@link java.security.cert.X509Certificate}.  Necessary because sometimes X509Certs have circular references
     * and the default gson serializer will cause a {@code java.lang.StackOverflowError}.  Standard Base64 encoding of
     * the cert is used as the json format.
     */
    private static class X509CertificateAdapter implements JsonSerializer<X509Certificate>, JsonDeserializer<X509Certificate>
    {
        private X509CertificateAdapter()
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

        private DateTypeAdapter()
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
        private InstantTypeAdapter()
        {
        }

        @Override
        public JsonElement serialize( final Instant instant, final Type type, final JsonSerializationContext jsonSerializationContext )
        {
            return new JsonPrimitive( StringUtil.toIsoDate( instant ) );
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
            return DomainID.create( sValue );
        }

        @Override
        public JsonElement serialize( final DomainID src, final Type typeOfSrc, final JsonSerializationContext context )
        {
            return new JsonPrimitive( src.toString() );
        }
    }

    private static class ProfileIDTypeAdaptor implements JsonSerializer<ProfileID>, JsonDeserializer<ProfileID>
    {
        @Override
        public ProfileID deserialize( final JsonElement json, final Type typeOfT, final JsonDeserializationContext context ) throws JsonParseException
        {
            final String sValue = json.getAsString();
            return ProfileID.create( sValue );
        }

        @Override
        public JsonElement serialize( final ProfileID src, final Type typeOfSrc, final JsonSerializationContext context )
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
            final LongAdder longAdder = new LongAdder();
            longAdder.add( longValue );
            return longAdder;
        }

        @Override
        public JsonElement serialize( final LongAdder src, final Type typeOfSrc, final JsonSerializationContext context )
        {
            return new JsonPrimitive( src.longValue() );
        }
    }

    private static class LongAccumulatorTypeAdaptor implements JsonSerializer<LongAccumulator>, JsonDeserializer<LongAccumulator>
    {
        @Override
        public LongAccumulator deserialize( final JsonElement json, final Type typeOfT, final JsonDeserializationContext context ) throws JsonParseException
        {
            final long longValue = json.getAsLong();
            final LongAccumulator longAccumulator = JavaHelper.newAbsLongAccumulator();
            longAccumulator.accumulate( longValue );
            return longAccumulator;
        }

        @Override
        public JsonElement serialize( final LongAccumulator src, final Type typeOfSrc, final JsonSerializationContext context )
        {
            return new JsonPrimitive( src.longValue() );
        }
    }

    private static class TimeDurationAdaptor implements JsonSerializer<TimeDuration>, JsonDeserializer<TimeDuration>
    {
        @Override
        public TimeDuration deserialize( final JsonElement json, final Type typeOfT, final JsonDeserializationContext context ) throws JsonParseException
        {
            final String stringValue = json.getAsString();
            return TimeDuration.fromDuration( Duration.parse( stringValue ) );
        }

        @Override
        public JsonElement serialize( final TimeDuration src, final Type typeOfSrc, final JsonSerializationContext context )
        {
            return new JsonPrimitive( src.asDuration().toString() );
        }
    }

    private static class DurationAdaptor implements JsonSerializer<Duration>, JsonDeserializer<Duration>
    {
        @Override
        public Duration deserialize( final JsonElement json, final Type typeOfT, final JsonDeserializationContext context ) throws JsonParseException
        {
            final String stringValue = json.getAsString();
            return Duration.parse( stringValue );
        }

        @Override
        public JsonElement serialize( final Duration src, final Type typeOfSrc, final JsonSerializationContext context )
        {
            return new JsonPrimitive( src.toString() );
        }
    }
}
