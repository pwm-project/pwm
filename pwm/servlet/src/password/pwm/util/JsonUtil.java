/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2014 The PWM Project
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

package password.pwm.util;

import com.google.gson.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class JsonUtil {
    private static final PwmLogger LOGGER = PwmLogger.getLogger(JsonUtil.class);

    private static Gson GSON_SINGLETON = new GsonBuilder()
            .registerTypeAdapter(Date.class, new DateTypeAdapter())
            .registerTypeAdapter(X509Certificate.class, new X509CertificateAdapter())
            .registerTypeAdapter(byte[].class, new ByteArrayToBase64TypeAdapter())
            .create();

    public static Gson getGson(GsonBuilder gsonBuilder) {
        if (gsonBuilder == null) {
            gsonBuilder = new GsonBuilder();
            gsonBuilder.disableHtmlEscaping();
        }

        return gsonBuilder
                .registerTypeAdapter(Date.class, new DateTypeAdapter())
                .registerTypeAdapter(X509Certificate.class, new X509CertificateAdapter())
                .registerTypeAdapter(byte[].class, new ByteArrayToBase64TypeAdapter())
                .create();
    }

    public static Gson getGson() {
        return GSON_SINGLETON;
    }

    /**
     * Gson Serializer for {@link java.security.cert.X509Certificate}.  Neccessary because sometimes X509Certs have circular refecences
     * and the default gson serializer will cause a {@code java.lang.StackOverflowError}.  Standard Base64 encoding of
     * the cert is used as the json format.
     */
    private static class X509CertificateAdapter implements JsonSerializer<X509Certificate>, JsonDeserializer<X509Certificate> {
        private X509CertificateAdapter() {
        }

        public synchronized JsonElement serialize(X509Certificate cert, Type type, JsonSerializationContext jsonSerializationContext) {
            try {
                return new JsonPrimitive(Base64Util.encodeBytes(cert.getEncoded()));
            } catch (CertificateEncodingException e) {
                throw new IllegalStateException("unable to json-encode certificate: " + e.getMessage());
            }
        }

        public X509Certificate deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext)
                throws JsonParseException
        {
            try {
                final CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
                return (X509Certificate)certificateFactory.generateCertificate(new ByteArrayInputStream(Base64Util.decode(jsonElement.getAsString())));
            } catch (Exception e) {
                throw new JsonParseException("unable to parse x509certificate: " + e.getMessage());
            }
        }
    }

    /**
     * GsonSerializer that stores dates in ISO 8601 format, with a deserialier that also reads local-platform format reading.
     */
    private static class DateTypeAdapter implements JsonSerializer<Date>, JsonDeserializer<Date> {
        private static final DateFormat isoDateFormat;
        private static final DateFormat gsonDateFormat;

        static {
            isoDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
            isoDateFormat.setTimeZone(TimeZone.getTimeZone("Zulu"));

            gsonDateFormat = DateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.DEFAULT);
            gsonDateFormat.setTimeZone(TimeZone.getDefault());
        }

        private DateTypeAdapter() {
        }

        public synchronized JsonElement serialize(Date date, Type type, JsonSerializationContext jsonSerializationContext) {
            return new JsonPrimitive(isoDateFormat.format(date));
        }

        public synchronized Date deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) {
            try {
                return isoDateFormat.parse(jsonElement.getAsString());
            } catch (ParseException e) { /* noop */ }

            // for backwards compatibility
            try {
                return gsonDateFormat.parse(jsonElement.getAsString());
            } catch (ParseException e) {
                LOGGER.error("unable to parse stored json timestamp '" + jsonElement.getAsString() + "' error: " + e.getMessage());
                throw new JsonParseException(e);
            }
        }
    }

    private static class ByteArrayToBase64TypeAdapter implements JsonSerializer<byte[]>, JsonDeserializer<byte[]> {
        public byte[] deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            try {
                return Base64Util.decode(json.getAsString());
            } catch (IOException e) {
                final String errorMsg = "io stream error while deserializing byte array: " + e.getMessage();
                LOGGER.error(errorMsg);
                throw new JsonParseException(errorMsg,e);
            }
        }

        public JsonElement serialize(byte[] src, Type typeOfSrc, JsonSerializationContext context) {
            try {
                return new JsonPrimitive(Base64Util.encodeBytes(src, Base64Util.GZIP));
            } catch (IOException e) {
                final String errorMsg = "io stream error while serializing byte array: " + e.getMessage();
                LOGGER.error(errorMsg);
                throw new JsonParseException(errorMsg,e);
            }
        }
    }
}
