/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
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

import java.io.Serializable;
import java.net.URI;

public class ShortcutItem implements Serializable {
    private String ldapQuery;
    private URI shortcutURI;
    private String label;
    private String description;

    public ShortcutItem(String ldapQuery, URI shortcutURI, String label, String description) {
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
                "ldapQuery='" + ldapQuery + '\'' +
                ", shortcutURI=" + shortcutURI +
                ", label='" + label + '\'' +
                ", description='" + description + '\'' +
                '}';
    }
}
