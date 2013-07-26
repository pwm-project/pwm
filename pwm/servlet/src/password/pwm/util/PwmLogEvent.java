/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2012 The PWM Project
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

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class PwmLogEvent implements Serializable, Comparable {
// -------------------------- ENUMERATIONS --------------------------

    private static final String VERSION = "1";

    private static final String KEY_VERSION = "v";
    private static final String KEY_LEVEL = "l";
    private static final String KEY_TOPIC = "t";
    private static final String KEY_MESSAGE = "m";
    private static final String KEY_SOURCE = "s";
    private static final String KEY_ACTOR = "a";
    private static final String KEY_THROWABLE = "e";
    private static final String KEY_DATE = "d";

    // ------------------------------ FIELDS ------------------------------

    private final PwmLogLevel level;
    private final String topic;
    private final String message;
    private final String source;
    private final String actor;
    private final Throwable throwable;
    private final Date date;

// -------------------------- STATIC METHODS --------------------------

    public static PwmLogEvent fromEncodedString(final String encodedString)
            throws ClassNotFoundException, IOException {
        final Gson gson = new Gson();
        final Map<String, String> srcMap = gson.fromJson(encodedString, new TypeToken<Map<String, String>>() {
        }.getType());

        if (srcMap == null) {
            return null;
        }

        final String topic = srcMap.get(KEY_TOPIC);
        final String message = srcMap.get(KEY_MESSAGE);
        final String source = srcMap.get(KEY_SOURCE);
        final String actor = srcMap.get(KEY_ACTOR);

        Date date = null;
        if (srcMap.containsKey(KEY_DATE)) {
            date = new Date(Long.valueOf(srcMap.get(KEY_DATE)));
        }

        Throwable throwable = null;
        if (srcMap.containsKey(KEY_THROWABLE)) {
            throwable = (Throwable) Base64Util.decodeToObject((String) srcMap.get(KEY_THROWABLE));
        }

        PwmLogLevel level = null;
        if (srcMap.containsKey(KEY_LEVEL)) {
            level = PwmLogLevel.valueOf(srcMap.get(KEY_LEVEL));
        }

        return new PwmLogEvent(date, topic, message, source, actor, throwable, level);
    }

// --------------------------- CONSTRUCTORS ---------------------------

    public PwmLogEvent(
            final Date date,
            final String topic,
            final String message,
            final String source,
            final String actor,
            final Throwable throwable,
            final PwmLogLevel level
    ) {
        if (date == null) {
            throw new IllegalArgumentException("date may not be null");
        }

        if (level == null) {
            throw new IllegalArgumentException("level may not be null");
        }

        this.date = date;
        this.topic = topic;
        this.message = message;
        this.source = source;
        this.actor = actor;
        this.throwable = throwable;
        this.level = level;
    }

// --------------------- GETTER / SETTER METHODS ---------------------

    public String getActor() {
        return actor;
    }

    public Date getDate() {
        return date;
    }

    public PwmLogLevel getLevel() {
        return level;
    }

    public String getMessage() {
        return message;
    }

    public String getHtmlSafeMessage() {
        return message.replaceAll("<", "&lt;").replaceAll(">", "&gt;");
    }

    public String getSource() {
        return source;
    }

    public Throwable getThrowable() {
        return throwable;
    }

    public String getTopic() {
        return topic;
    }

    public String getTopTopic() {
        if (topic == null) {
            return null;
        }

        final int lastDot = topic.lastIndexOf(".");
        return lastDot != -1 ? topic.substring(lastDot + 1, topic.length()) : topic;
    }

// ------------------------ INTERFACE METHODS ------------------------


// --------------------- Interface Comparable ---------------------

    public int compareTo(final Object o) {
        if (!(o instanceof PwmLogEvent)) {
            throw new IllegalArgumentException("cannot compare with non PwmLogEvent");
        }
        return this.getDate().compareTo(((PwmLogEvent) o).getDate());
    }

// -------------------------- OTHER METHODS --------------------------

    public String toEncodedString()
            throws IOException {
        final Map<String, String> tempMap = new HashMap<String, String>();
        tempMap.put(KEY_VERSION, VERSION);
        tempMap.put(KEY_TOPIC, topic);
        tempMap.put(KEY_MESSAGE, message);
        tempMap.put(KEY_SOURCE, source);
        tempMap.put(KEY_ACTOR, actor);

        if (level != null) {
            tempMap.put(KEY_LEVEL, level.toString());
        }
        if (throwable != null) {
            tempMap.put(KEY_THROWABLE, Base64Util.encodeObject(throwable, Base64Util.NO_OPTIONS));
        }
        if (date != null) {
            tempMap.put(KEY_DATE, String.valueOf(date.getTime()));
        }

        final Gson gson = new Gson();
        return gson.toJson(tempMap);
    }

    public String toLogString(final boolean htmlSafe) {
        final StringBuilder sb = new StringBuilder();
        sb.append(this.date);
        sb.append(", ");
        sb.append(this.level.toString().length() == 4 ? this.level + " " : this.level);
        sb.append(", ");
        sb.append(this.topic);
        sb.append(", ");
        if (this.getActor() != null && this.getActor().length() > 0) {
            sb.append("{");
            sb.append(this.getActor());
            sb.append("} ");
        }
        sb.append(htmlSafe ? this.getHtmlSafeMessage() : this.message);
        if (this.getSource() != null && this.getSource().length() > 0) {
            sb.append(" [");
            sb.append(this.getSource());
            sb.append("]");
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return "PwmLogEvent{" +
                "level=" + level +
                ", topic='" + topic + '\'' +
                ", message=" + message +
                ", source='" + source + '\'' +
                ", actor='" + actor + '\'' +
                ", throwable=" + throwable +
                ", date=" + date +
                '}';
    }
}
