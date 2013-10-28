/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2013 The PWM Project
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

package password.pwm;

import java.util.ResourceBundle;

public enum AppProperty {
    COOKIE_NAME_THEME("cookie.theme.name"),
    COOKIE_NAME_LOCALE("cookie.locale.name"),
    CLIENT_AJAX_TYPING_TIMEOUT("ajax.client.typingTimeout"),
    CLIENT_AJAX_TYPING_WAIT("ajax.client.typingWait"),
    CLIENT_ACTIVITY_MAX_EPS_RATE("ajax.client.activityMaxEpsRate"),
    LOCALDB_COMPRESSION_ENABLED("localdb.compression.enabled"),
    LOCALDB_DECOMPRESSION_ENABLED("localdb.decompression.enabled"),
    LOCALDB_COMPRESSION_MINSIZE("localdb.compression.minSize"),
    INTRUDER_RETENTION_TIME_MS("intruder.retentionTimeMS"),
    INTRUDER_CLEANUP_FREQUENCY_MS("intruder.cleanupFrequencyMS"),
    INTRUDER_MIN_DELAY_PENALTY_MS("intruder.minimumDelayPenaltyMS"),
    INTRUDER_MAX_DELAY_PENALTY_MS("intruder.maximumDelayPenaltyMS"),
    INTRUDER_DELAY_PER_COUNT_MS("intruder.delayPerCountMS"),
    INTRUDER_DELAY_MAX_JITTER_MS("intruder.delayMaxJitterMS"),
    LOGGING_PATTERN("logging.pattern"),
    LOGGING_FILE_MAX_SIZE("logging.file.maxSize"),
    LOGGING_FILE_MAX_ROLLOVER("logging.file.maxRollover"),
    NMAS_THREADS_MAX_COUNT("nmas.threads.maxCount"),
    NMAS_THREADS_MIN_SECONDS("nmas.threads.minSeconds"),
    NMAS_THREADS_MAX_SECONDS("nmas.threads.maxSeconds"),
    NMAS_THREADS_WATCHDOG_FREQUENCY("nmas.threads.watchdogFrequencyMs"),

    ;

    private final String key;

    private AppProperty(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    public String getDefaultValue() {
        return readAppPropertiesBundle(this.getKey());
    }

    private static String readAppPropertiesBundle(final String key) {
        return  ResourceBundle.getBundle(AppProperty.class.getName()).getString(key);
    }
}
