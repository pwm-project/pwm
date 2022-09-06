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
import com.squareup.moshi.JsonReader;
import com.squareup.moshi.JsonWriter;
import com.squareup.moshi.Moshi;
import org.jetbrains.annotations.Nullable;
import password.pwm.PwmConstants;
import password.pwm.bean.DomainID;
import password.pwm.bean.ProfileID;
import password.pwm.error.PwmInternalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.PasswordData;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.PwmDateFormat;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.text.DateFormat;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.atomic.LongAdder;

class MoshiJsonAdaptors
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( MoshiJsonAdaptors.class );

    static void registerTypeAdapters( final Moshi.Builder moshiBuilder, final JsonProvider.Flag... flags )
    {
        moshiBuilder.add( Date.class, applyFlagsToAdapter( new DateTypeAdapter(), flags ) );
        moshiBuilder.add( Instant.class, applyFlagsToAdapter( new InstantTypeAdapter(), flags ) );
        moshiBuilder.add( X509Certificate.class, applyFlagsToAdapter( new X509CertificateAdapter(), flags ) );
        moshiBuilder.add( PasswordData.class, applyFlagsToAdapter( new PasswordDataAdapter(), flags ) );
        moshiBuilder.add( DomainID.class, applyFlagsToAdapter( new DomainIdAdaptor(), flags ) );
        moshiBuilder.add( ProfileID.class, applyFlagsToAdapter( new ProfileIdAdaptor(), flags ) );
        moshiBuilder.add( LongAdder.class, applyFlagsToAdapter( new LongAdderTypeAdaptor(), flags ) );
        moshiBuilder.add( BigInteger.class, applyFlagsToAdapter( new BigIntegerTypeAdaptor(), flags ) );
        moshiBuilder.add( Locale.class, applyFlagsToAdapter( new LocaleTypeAdaptor(), flags ) );
        moshiBuilder.add( TimeDuration.class, applyFlagsToAdapter( new TimeDurationAdaptor(), flags ) );
        moshiBuilder.add( Duration.class, applyFlagsToAdapter( new DurationAdaptor(), flags ) );
    }

    static <T> JsonAdapter<T> applyFlagsToAdapter( final JsonAdapter<T> adapter, final JsonProvider.Flag... flags )
    {
        JsonAdapter<T> adapterInProgress = adapter;

        if ( JavaHelper.enumArrayContainsValue( flags, JsonProvider.Flag.PrettyPrint ) )
        {
            adapterInProgress = adapter.indent( "  " );
        }

        return adapterInProgress;
    }



    /**
     * Stores instants in ISO 8601 format, with a deserializer that also reads local-platform format reading.
     */
    private static class InstantTypeAdapter extends JsonAdapter<Instant>
    {
        @Nullable
        @Override
        public Instant fromJson( final JsonReader reader ) throws IOException
        {
            final String strValue = reader.nextString();
            if ( StringUtil.isEmpty( strValue ) )
            {
                return null;
            }

            try
            {
                return JavaHelper.parseIsoToInstant( strValue );
            }
            catch ( final Exception e )
            {
                LOGGER.debug( () -> "unable to parse stored json Instant.class timestamp '" + strValue + "' error: " + e.getMessage() );
                throw new IOException( e );
            }
        }

        @Override
        public void toJson( final JsonWriter writer, @Nullable final Instant value ) throws IOException
        {
            if ( value == null )
            {
                writer.nullValue();
                return;
            }

            writer.jsonValue( StringUtil.toIsoDate( value ) );
        }
    }

    /**
     * GsonSerializer that stores dates in ISO 8601 format, with a deserializer that also reads local-platform format reading.
     */
    private static class DateTypeAdapter extends JsonAdapter<Date>
    {
        private static final PwmDateFormat ISO_DATE_FORMAT = PwmDateFormat.newPwmDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                PwmConstants.DEFAULT_LOCALE,
                TimeZone.getTimeZone( "Zulu" ) );

        private static DateFormat getLegacyDateFormat()
        {
            final DateFormat gsonDateFormat = DateFormat.getDateTimeInstance( DateFormat.DEFAULT, DateFormat.DEFAULT );
            gsonDateFormat.setTimeZone( TimeZone.getDefault() );
            return gsonDateFormat;
        }

        @Nullable
        @Override
        public Date fromJson( final JsonReader reader ) throws IOException
        {
            final String strValue = reader.nextString();

            if ( StringUtil.isEmpty( strValue ) )
            {
                return null;
            }

            try
            {
                return Date.from( ISO_DATE_FORMAT.parse( strValue ) );
            }
            catch ( final ParseException e )
            {
                /* noop */
            }

            // for backwards compatibility
            try
            {
                return getLegacyDateFormat().parse( strValue );
            }
            catch ( final ParseException e )
            {
                LOGGER.debug( () -> "unable to parse stored json Date.class timestamp '" + strValue + "' error: " + e.getMessage() );
                throw new IOException( e );
            }
        }

        @Override
        public void toJson( final JsonWriter writer, @Nullable final Date value ) throws IOException
        {
            if ( value == null )
            {
                writer.nullValue();
                return;
            }

            writer.value( ISO_DATE_FORMAT.format( value.toInstant() ) );
        }
    }

    private static class DomainIdAdaptor extends JsonAdapter<DomainID>
    {
        @Nullable
        @Override
        public DomainID fromJson( final JsonReader reader ) throws IOException
        {
            final String stringValue = reader.nextString();

            if ( StringUtil.isEmpty( stringValue ) )
            {
                return null;
            }

            return DomainID.create( stringValue );
        }

        @Override
        public void toJson( final JsonWriter writer, @Nullable final DomainID value ) throws IOException
        {
            if ( value == null )
            {
                writer.nullValue();
                return;
            }

            writer.value( value.toString() );
        }
    }

    private static class ProfileIdAdaptor extends JsonAdapter<ProfileID>
    {
        @Nullable
        @Override
        public ProfileID fromJson( final JsonReader reader ) throws IOException
        {
            final String stringValue = reader.nextString();

            if ( StringUtil.isEmpty( stringValue ) )
            {
                return null;
            }

            return ProfileID.create( stringValue );
        }

        @Override
        public void toJson( final JsonWriter writer, @Nullable final ProfileID value ) throws IOException
        {
            if ( value == null )
            {
                writer.nullValue();
                return;
            }

            writer.value( value.toString() );
        }
    }

    /**
     * Gson Serializer for {@link java.security.cert.X509Certificate}.  Necessary because sometimes X509Certs have circular references
     * and the default gson serializer will cause a {@code java.lang.StackOverflowError}.  Standard Base64 encoding of
     * the cert is used as the json format.
     */
    private static class X509CertificateAdapter extends JsonAdapter<X509Certificate>
    {
        @Nullable
        @Override
        public X509Certificate fromJson( final JsonReader reader ) throws IOException
        {
            final String strValue = reader.nextString();
            try
            {
                final CertificateFactory certificateFactory = CertificateFactory.getInstance( "X.509" );
                try ( ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream( StringUtil.base64Decode( strValue ) ) )
                {
                    return ( X509Certificate ) certificateFactory.generateCertificate( byteArrayInputStream );
                }
            }
            catch ( final Exception e )
            {
                throw new IOException( "unable to parse x509certificate: " + e.getMessage() );
            }
        }


        @Override
        public void toJson( final JsonWriter writer, @Nullable final X509Certificate value ) throws IOException
        {
            if ( value == null )
            {
                writer.nullValue();
                return;
            }

            try
            {
                final byte[] encoded = value.getEncoded();
                writer.value( StringUtil.stripAllWhitespace( StringUtil.base64Encode( encoded ) ) );
            }
            catch ( final PwmInternalException | CertificateEncodingException e )
            {
                throw new IOException( "unable to json-encode certificate: " + e.getMessage() );
            }
        }
    }


    private static class PasswordDataAdapter extends JsonAdapter<PasswordData>
    {
        @Nullable
        @Override
        public PasswordData fromJson( final JsonReader reader ) throws IOException
        {
            final String strValue = reader.nextString();
            try
            {
                return new PasswordData( strValue );
            }
            catch ( final PwmUnrecoverableException e )
            {
                final String errorMsg = "error while deserializing password data: " + e.getMessage();
                LOGGER.error( () -> errorMsg );
                throw new IOException( errorMsg, e );
            }
        }

        @Override
        public void toJson( final JsonWriter writer, @Nullable final PasswordData value ) throws IOException
        {
            if ( value == null )
            {
                writer.nullValue();
                return;
            }

            try
            {
                writer.value( value.getStringValue() );
            }
            catch ( final PwmUnrecoverableException e )
            {
                final String errorMsg = "error while serializing password data: " + e.getMessage();
                LOGGER.error( () -> errorMsg );
                throw new IOException( errorMsg, e );
            }
        }
    }

    private static class LongAdderTypeAdaptor extends JsonAdapter<LongAdder>
    {
        @Nullable
        @Override
        public LongAdder fromJson( final JsonReader reader ) throws IOException
        {
            final String strValue = reader.nextString();
            final long longValue = Long.parseLong( strValue );
            final LongAdder longAddr = new LongAdder();
            longAddr.add( longValue );
            return longAddr;
        }

        @Override
        public void toJson( final JsonWriter writer, @Nullable final LongAdder value ) throws IOException
        {
            if ( value == null )
            {
                writer.nullValue();
                return;
            }

            writer.value( value.longValue() );
        }
    }

    private static class BigIntegerTypeAdaptor extends JsonAdapter<BigInteger>
    {
        @Nullable
        @Override
        public BigInteger fromJson( final JsonReader reader ) throws IOException
        {
            final String strValue = reader.nextString();
            return new BigInteger( strValue );
        }

        @Override
        public void toJson( final JsonWriter writer, @Nullable final BigInteger value ) throws IOException
        {
            if ( value == null )
            {
                writer.nullValue();
                return;
            }

            writer.value( value.toString() );
        }
    }

    private static class LocaleTypeAdaptor extends JsonAdapter<Locale>
    {
        @Nullable
        @Override
        public Locale fromJson( final JsonReader reader ) throws IOException
        {
            final String strValue = reader.nextString();
            return new Locale( strValue );
        }

        @Override
        public void toJson( final JsonWriter writer, @Nullable final Locale value ) throws IOException
        {
            if ( value == null )
            {
                writer.nullValue();
                return;
            }

            writer.value( value.toString() );
        }
    }

    private static class TimeDurationAdaptor extends JsonAdapter<TimeDuration>
    {
        @Nullable
        @Override
        public TimeDuration fromJson( final JsonReader reader ) throws IOException
        {
            final String strValue = reader.nextString();
            if ( StringUtil.isEmpty( strValue ) )
            {
                return null;
            }
            return TimeDuration.fromDuration( Duration.parse( strValue ) );
        }

        @Override
        public void toJson( final JsonWriter writer, @Nullable final TimeDuration value ) throws IOException
        {
            if ( value == null )
            {
                writer.nullValue();
                return;
            }

            writer.value( value.asDuration().toString() );
        }
    }

    private static class DurationAdaptor extends JsonAdapter<Duration>
    {
        @Nullable
        @Override
        public Duration fromJson( final JsonReader reader ) throws IOException
        {
            final String strValue = reader.nextString();
            if ( StringUtil.isEmpty( strValue ) )
            {
                return null;
            }
            return Duration.parse( strValue );
        }

        @Override
        public void toJson( final JsonWriter writer, @Nullable final Duration value ) throws IOException
        {
            if ( value == null )
            {
                writer.nullValue();
                return;
            }

            writer.value( value.toString() );
        }
    }
}
