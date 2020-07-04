/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2020 The PWM Project
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

package password.pwm.ldap.schema;

import password.pwm.util.java.JsonUtil;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ResourceBundle;
import java.util.TreeMap;

public class SchemaDefinition implements Serializable
{
    private SchemaType schemaType;
    private String name;
    private String definition;

    public SchemaDefinition( final SchemaType schemaType, final String name, final String definition )
    {
        this.schemaType = schemaType;
        this.name = name;
        this.definition = definition;
    }

    public static List<SchemaDefinition> getPwmSchemaDefinitions( )
    {
        final ResourceBundle resourceBundle = ResourceBundle.getBundle( SchemaDefinition.class.getName() );
        final TreeMap<String, SchemaDefinition> returnObj = new TreeMap<>();
        for ( final String key : Collections.list( resourceBundle.getKeys() ) )
        {
            final String value = resourceBundle.getString( key );
            final SchemaDefinition schemaDefinition = JsonUtil.deserialize( value, SchemaDefinition.class );
            returnObj.put( key, schemaDefinition );
        }
        return new ArrayList<>( returnObj.values() );
    }

    public SchemaType getSchemaType( )
    {
        return schemaType;
    }

    public String getName( )
    {
        return name;
    }

    public String getDefinition( )
    {
        return definition;
    }

    enum SchemaType
    {
        attribute,
        objectclass,
    }

    enum State
    {
        missing,
        incorrect,
        correct,
    }
}
