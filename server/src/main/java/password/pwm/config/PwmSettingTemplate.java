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

package password.pwm.config;

import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.XmlElement;

import java.util.EnumMap;
import java.util.Map;

public enum PwmSettingTemplate
{
    NOVL( Type.LDAP_VENDOR ),
    AD( Type.LDAP_VENDOR ),
    ORACLE_DS( Type.LDAP_VENDOR ),
    DEFAULT( Type.LDAP_VENDOR ),
    NOVL_IDM( Type.LDAP_VENDOR ),
    OPEN_LDAP( Type.LDAP_VENDOR ),

    LOCALDB( Type.STORAGE ),
    DB( Type.STORAGE ),
    LDAP( Type.STORAGE ),

    DB_ORACLE( Type.DB_VENDOR ),
    DB_OTHER( Type.DB_VENDOR ),;

    private final Type type;

    PwmSettingTemplate( final Type type )
    {
        this.type = type;
    }

    public Type getType( )
    {
        return type;
    }

    public static PwmSettingTemplate templateForString( final String input, final Type type )
    {
        final PwmSettingTemplate template = JavaHelper.readEnumFromString( PwmSettingTemplate.class, type.getDefaultValue(), input );
        return template == null || template.getType() != type ? type.getDefaultValue() : template;
    }

    public boolean isHidden( )
    {
        final XmlElement templateElement = readTemplateElement( this );
        final String requiredAttribute = templateElement.getAttributeValue( "hidden" );
        return requiredAttribute != null && "true".equalsIgnoreCase( requiredAttribute );
    }

    private static XmlElement readTemplateElement( final PwmSettingTemplate pwmSettingTemplate )
    {
        final XmlElement element = PwmSettingXml.readTemplateXml( pwmSettingTemplate );
        if ( element == null )
        {
            throw new IllegalStateException( "missing PwmSetting.xml template element for " + pwmSettingTemplate );
        }
        return element;
    }

    public enum Type
    {
        LDAP_VENDOR,
        STORAGE,
        DB_VENDOR,;

        // done using map instead of static values to avoid initialization circularity bug
        public PwmSettingTemplate getDefaultValue( )
        {
            final Map<Type, PwmSettingTemplate> defaultValueMap = new EnumMap<>( Type.class );
            defaultValueMap.put( LDAP_VENDOR, DEFAULT );
            defaultValueMap.put( STORAGE, LDAP );
            defaultValueMap.put( DB_VENDOR, DB_OTHER );

            return defaultValueMap.get( this );
        }
    }
}
