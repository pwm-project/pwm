/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2015 The PWM Project
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
import com.google.gson.reflect.TypeToken;
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
import java.text.SimpleDateFormat;
import java.util.*;

public class JsonUtil {
    private static final PwmLogger LOGGER = PwmLogger.forClass(JsonUtil.class);

    public enum Flag {
        PrettyPrint,
        HtmlEscape,
    }

    private static final Gson GENERIC_GSON = registerTypeAdapters(new GsonBuilder())
            .disableHtmlEscaping()
            .create();

    private static Gson getGson(final Flag... flags) {
        if (flags == null) {
            return getGson(Collections.<Flag>emptySet());
        } else {
            return getGson(new HashSet(Arrays.asList(flags)));
        }
    }

    private static Gson getGson(final Set<Flag> flags) {
        if (flags == null || flags.isEmpty()) {
            return GENERIC_GSON;
        }

        final GsonBuilder gsonBuilder = registerTypeAdapters(new GsonBuilder());

        if (!flags.contains(Flag.HtmlEscape)) {
            gsonBuilder.disableHtmlEscaping();
        }

        if (flags.contains(Flag.PrettyPrint)) {
            gsonBuilder.setPrettyPrinting();
        }

        return gsonBuilder.create();
    }

    public static <T> T deserialize(final String jsonString, final TypeToken typeToken) {
        return JsonUtil.getGson().fromJson(jsonString, typeToken.getType());
    }

    public static List<String> deserializeStringList(final String jsonString) {
        return JsonUtil.getGson().fromJson(jsonString, new TypeToken<List<Object>>() {
        }.getType());
    }

    public static Map<String, String> deserializeStringMap(final String jsonString) {
        return JsonUtil.getGson().fromJson(jsonString, new TypeToken<Map<String, String>>() {
        }.getType());
    }

    public static Map<String, Object> deserializeMap(final String jsonString) {
        return JsonUtil.getGson().fromJson(jsonString, new TypeToken<Map<String, Object>>() {
        }.getType());
    }

    public static <T> T deserialize(final String json, final Class<T> classOfT) {
        return JsonUtil.getGson().fromJson(json, classOfT);
    }

    public static String serialize(final Serializable object, final Flag... flags) {
        return JsonUtil.getGson(flags).toJson(object);
    }

    public static String serializeMap(final Map object, final Flag... flags) {
        return JsonUtil.getGson(flags).toJson(object);
    }

    public static String serializeCollection(final Collection object, final Flag... flags) {
        return JsonUtil.getGson(flags).toJson(object);
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
                return new JsonPrimitive(StringUtil.base64Encode(cert.getEncoded()));
            } catch (CertificateEncodingException e) {
                throw new IllegalStateException("unable to json-encode certificate: " + e.getMessage());
            }
        }

        public X509Certificate deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext)
                throws JsonParseException {
            try {
                final CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
                return (X509Certificate) certificateFactory.generateCertificate(new ByteArrayInputStream(StringUtil.base64Decode(
                        jsonElement.getAsString())));
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
                return StringUtil.base64Decode(json.getAsString());
            } catch (IOException e) {
                final String errorMsg = "io stream error while deserializing byte array: " + e.getMessage();
                LOGGER.error(errorMsg);
                throw new JsonParseException(errorMsg, e);
            }
        }

        public JsonElement serialize(byte[] src, Type typeOfSrc, JsonSerializationContext context) {
            try {
                return new JsonPrimitive(StringUtil.base64Encode(src, StringUtil.Base64Options.GZIP));
            } catch (IOException e) {
                final String errorMsg = "io stream error while serializing byte array: " + e.getMessage();
                LOGGER.error(errorMsg);
                throw new JsonParseException(errorMsg, e);
            }
        }
    }

    private static GsonBuilder registerTypeAdapters(final GsonBuilder gsonBuilder) {
        gsonBuilder.registerTypeAdapter(Date.class, new DateTypeAdapter());
        gsonBuilder.registerTypeAdapter(X509Certificate.class, new X509CertificateAdapter());
        gsonBuilder.registerTypeAdapter(byte[].class, new ByteArrayToBase64TypeAdapter());
        return gsonBuilder;
    }

    public static <T> T cloneUsingJson(final Serializable srcObject, final Class<T> classOfT) {
        final String asJson = JsonUtil.serialize(srcObject);
        return JsonUtil.deserialize(asJson, classOfT);
    }
}