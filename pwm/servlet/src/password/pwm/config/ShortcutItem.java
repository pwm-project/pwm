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

package password.pwm.config;

import password.pwm.util.PwmLogger;

import java.io.Serializable;
import java.net.URI;

public class ShortcutItem implements Serializable {

    private static final PwmLogger LOGGER = PwmLogger.getLogger(ShortcutItem.class);

    private final String label;
    private final URI shortcutURI;
    private final String ldapQuery;
    private final String description;

    public ShortcutItem(final String label, final URI shortcutURI, final String ldapQuery, final String description) {
        this.ldapQuery = ldapQuery;
        this.shortcutURI = shortcutURI;
        this.label = label;
        this.description = description;
    }

    public String getLdapQuery() {
        return ldapQuery;
    }

    public URI getShortcutURI() {
        return shortcutURI;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }


    public String toString() {
        return "ShortcutItem{" +
                "label='" + label + '\'' +
                ", shortcutURI=" + shortcutURI +
                ", ldapQuery='" + ldapQuery + '\'' +
                ", description='" + description + '\'' +
                '}';
    }

    public static ShortcutItem parsePwmConfigInput(final String input) {
        if (input != null && input.length() > 0) {
            try {
                final String[] splitSettings = input.split("::");
                return new ShortcutItem(
                        splitSettings[0],
                        URI.create(splitSettings[1]),
                        splitSettings[2],
                        splitSettings[3]
                );
            } catch (Exception e) {
                LOGGER.warn("malformed ShortcutItem configuration value of '" + input + "', " + e.getMessage());
            }
        }
        throw new IllegalArgumentException("malformed ShortcutItem configuration value of '" + input + "'");
    }


    public static ShortcutItem parseHeaderInput(final String input) {
        if (input != null && input.length() > 0) {
            try {
                final String[] splitSettings = input.split(";;;");
                return new ShortcutItem(
                        "",
                        URI.create(splitSettings[0]),
                        splitSettings[1],
                        splitSettings[2]
                );
            } catch (Exception e) {
                LOGGER.warn("malformed ShortcutItem configuration value of '" + input + "', " + e.getMessage());
            }
        }
        throw new IllegalArgumentException("malformed ShortcutItem configuration value of '" + input + "'");
    }
}
