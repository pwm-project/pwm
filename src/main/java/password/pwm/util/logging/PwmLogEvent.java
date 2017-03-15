/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2017 The PWM Project
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

package password.pwm.util.logging;

import com.google.gson.annotations.SerializedName;
import password.pwm.bean.SessionLabel;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.StringUtil;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.time.Instant;

public class PwmLogEvent implements Serializable, Comparable {
// -------------------------- ENUMERATIONS --------------------------

    private static final String VERSION = "1";

    private static final String KEY_VERSION = "v";
    private static final String KEY_LEVEL = "l";
    private static final String KEY_TOPIC = "t";
    private static final String KEY_MESSAGE = "m";
    private static final String KEY_SOURCE = "s";
    private static final String KEY_ACTOR = "a";
    private static final String KEY_LABEL = "b";
    private static final String KEY_THROWABLE = "e";
    private static final String KEY_DATE = "d";

    // ------------------------------ FIELDS ------------------------------

    private final PwmLogLevel level;

    @SerializedName("t")
    private final String topic;

    @SerializedName("m")
    private final String message;

    @SerializedName("s")
    private final String source; //aka network address/host

    @SerializedName("a")
    private final String actor; //aka principal

    @SerializedName("b")
    private final String label; //aka session id

    @SerializedName("e")
    private final Throwable throwable;

    @SerializedName("d")
    private final Instant date;

// -------------------------- STATIC METHODS --------------------------

    public static PwmLogEvent fromEncodedString(final String encodedString)
            throws ClassNotFoundException, IOException
    {
        return JsonUtil.deserialize(encodedString, PwmLogEvent.class);
    }

// --------------------------- CONSTRUCTORS ---------------------------

    private PwmLogEvent(
            final Instant date,
            final String topic,
            final String message,
            final String source,
            final String actor,
            final String label,
            final Throwable throwable,
            final PwmLogLevel level
    )
    {
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
        this.label = label;
        this.throwable = throwable;
        this.level = level;
    }

    private static String makeSrcString(final SessionLabel sessionLabel)
    {
        try {
            final StringBuilder from = new StringBuilder();
            {
                final String srcAddress = sessionLabel.getSrcAddress();
                final String srcHostname = sessionLabel.getSrcHostname();

                if (srcAddress != null) {
                    from.append(srcAddress);
                    if (!srcAddress.equals(srcHostname)) {
                        from.append("/");
                        from.append(srcHostname);
                    }
                }
            }
            return from.toString();
        } catch (NullPointerException e) {
            return "";
        }
    }

    private static String makeActorString(final SessionLabel sessionLabel)
    {
        final StringBuilder sb = new StringBuilder();
        if (sessionLabel != null) {
            if (sessionLabel.getUsername() != null) {
                sb.append(sessionLabel.getUsername());
            } else if (sessionLabel.getUserIdentity() != null) {
                sb.append(sessionLabel.getUserIdentity().toDelimitedKey());
            }
        }
        return sb.toString();
    }

    public static PwmLogEvent createPwmLogEvent(
            final Instant date,
            final String topic,
            final String message,
            final String source,
            final String actor,
            final String label,
            final Throwable throwable,
            final PwmLogLevel level
    )
    {
        return new PwmLogEvent(date, topic, message, source, actor, label, throwable, level);
    }

    public static PwmLogEvent createPwmLogEvent(
            final Instant date,
            final String topic,
            final String message,
            final SessionLabel sessionLabel,
            final Throwable throwable,
            final PwmLogLevel level
    )
    {
        final String source = makeSrcString(sessionLabel);
        final String actor = makeActorString(sessionLabel);
        final String label = sessionLabel != null ? sessionLabel.getSessionID() : null;
        return new PwmLogEvent(date, topic, message, source, actor, label, throwable, level);
    }


// --------------------- GETTER / SETTER METHODS ---------------------

    public String getActor()
    {
        return actor;
    }

    public Instant getDate()
    {
        return date;
    }

    public PwmLogLevel getLevel()
    {
        return level;
    }

    public String getMessage()
    {
        return message;
    }

    public String getSource()
    {
        return source;
    }

    public Throwable getThrowable()
    {
        return throwable;
    }

    public String getTopic()
    {
        return topic;
    }

    public String getLabel()
    {
        return label;
    }

    public String getTopTopic()
    {
        if (topic == null) {
            return null;
        }

        final int lastDot = topic.lastIndexOf(".");
        return lastDot != -1 ? topic.substring(lastDot + 1, topic.length()) : topic;
    }

    public String getEnhancedMessage()
    {
        final StringBuilder output = new StringBuilder();
        output.append(getDebugLabel());
        output.append(message);

        final String srcAddrString = this.getSource();
        if (srcAddrString != null && !srcAddrString.isEmpty()) {
            final String srcStr = " [" + srcAddrString + "]";

            final int firstCR = output.indexOf("\n");
            if (firstCR == -1) {
                output.append(srcStr);
            } else {
                output.insert(firstCR, srcStr);
            }
        }

        if (this.getThrowable() != null) {
            output.append(" (stacktrace follows)\n");
            final StringWriter sw = new StringWriter();
            final PrintWriter pw = new PrintWriter(sw);
            this.getThrowable().printStackTrace(pw);
            pw.flush();
            output.append(sw.toString());
        }

        return output.toString();
    }

    public int compareTo(final Object o)
    {
        if (!(o instanceof PwmLogEvent)) {
            throw new IllegalArgumentException("cannot compare with non PwmLogEvent");
        }
        return this.getDate().compareTo(((PwmLogEvent) o).getDate());
    }

    public String toEncodedString()
            throws IOException
    {
        return JsonUtil.serialize(this);
        /*
        final Map<String, String> tempMap = new HashMap<>();
        tempMap.put(KEY_VERSION, VERSION);
        tempMap.put(KEY_TOPIC, topic);
        tempMap.put(KEY_MESSAGE, message);
        tempMap.put(KEY_SOURCE, source);
        tempMap.put(KEY_ACTOR, actor);
        tempMap.put(KEY_LEVEL, level.toString());
        tempMap.put(KEY_DATE, String.valueOf(date.getTime()));

        if (label != null) {
            tempMap.put(KEY_LABEL, label);
        }

        if (throwable != null) {
            tempMap.put(KEY_THROWABLE, Base64.encodeObject(throwable, Base64.NO_OPTIONS));
        }

        return JsonUtil.serializeMap(tempMap);
        */
    }

    private String getDebugLabel()
    {
        final StringBuilder sb = new StringBuilder();
        if ((getActor() != null && !getActor().isEmpty()) || ((getLabel() != null && !getLabel().isEmpty()))) {
            sb.append("{");
            if (getLabel() != null && !getLabel().isEmpty()) {
                sb.append(this.getLabel());
            }
            if (getActor() != null && !getActor().isEmpty()) {
                if (sb.length() > 0) {
                    sb.append(",");
                }
                sb.append(this.getActor());
            }
            sb.append("} ");
        }
        return sb.toString();
    }

    public String toLogString()
    {
        return toLogString(true);
    }

    public String toLogString(final boolean includeTimeStamp)
    {
        final StringBuilder sb = new StringBuilder();
        if (includeTimeStamp) {
            sb.append(this.getDate().toString());
            sb.append(", ");
        }
        sb.append(StringUtil.padEndToLength(getLevel().toString(),5,' '));
        sb.append(", ");
        sb.append(shortenTopic(this.topic));
        sb.append(", ");

        sb.append(this.getEnhancedMessage());

        return sb.toString();
    }

    @Override
    public String toString()
    {
        return "PwmLogEvent=" + JsonUtil.serialize(this);
    }

    private static String shortenTopic(final String input)
    {
        if (input == null || input.isEmpty()) {
            return input;
        }

        final int keepParts = 2;
        final String[] parts = input.split("\\.");
        final StringBuilder output = new StringBuilder();
        int partsAdded = 0;
        for (int i = parts.length; i > 0 && partsAdded < keepParts; i--) {
            output.insert(0, parts[i - 1]);
            partsAdded++;
            if (i > 0 && partsAdded < keepParts) {
                output.insert(0, ".");
            }
        }
        return output.toString();
    }
}
