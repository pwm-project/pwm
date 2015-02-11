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

package password.pwm.ldap.schema;

import password.pwm.util.JsonUtil;

import java.io.Serializable;
import java.util.*;

class SchemaDefinition implements Serializable {
    private SchemaType schemaType;
    private String name;
    private String definition;

    public SchemaDefinition(SchemaType schemaType, String name, String definition) {
        this.schemaType = schemaType;
        this.name = name;
        this.definition = definition;
    }

    public static List<SchemaDefinition> getPwmSchemaDefinitions() {
        final ResourceBundle resourceBundle = ResourceBundle.getBundle(SchemaDefinition.class.getName());
        final TreeMap<String,SchemaDefinition> returnObj = new TreeMap<>();
        for (final String key : Collections.list(resourceBundle.getKeys())) {
            final String value = resourceBundle.getString(key);
            SchemaDefinition schemaDefinition = JsonUtil.deserialize(value, SchemaDefinition.class);
            returnObj.put(key, schemaDefinition);
        }
        return new ArrayList<>(returnObj.values());
    }

    public SchemaType getSchemaType() {
        return schemaType;
    }

    public String getName() {
        return name;
    }

    public String getDefinition() {
        return definition;
    }

    enum SchemaType {
        attribute,
        objectclass,
    }

    enum State {
        missing,
        incorrect,
        correct,
    }
}
