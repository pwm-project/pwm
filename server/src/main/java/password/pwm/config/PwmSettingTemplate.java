/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2021 The PWM Project
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

import org.jrivard.xmlchai.XmlElement;
import password.pwm.util.java.EnumUtil;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public enum PwmSettingTemplate
{
    NOVL( Type.LDAP_VENDOR ),
    AD( Type.LDAP_VENDOR ),
    ORACLE_DS( Type.LDAP_VENDOR ),
    DEFAULT( Type.LDAP_VENDOR ),
    NOVL_IDM( Type.LDAP_VENDOR ),
    DIRECTORY_SERVER_389( Type.LDAP_VENDOR ),
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
        final PwmSettingTemplate template = EnumUtil.readEnumFromString( PwmSettingTemplate.class, input )
                .orElse( type.getDefaultValue() );

        return template.getType() != type
                ? type.getDefaultValue()
                : template;
    }

    public boolean isHidden( )
    {
        final XmlElement templateElement = readTemplateElement( this );
        final Optional<String> requiredAttribute = templateElement.getAttribute( PwmSettingXml.XML_ELEMENT_HIDDEN );
        return requiredAttribute.isPresent() && "true".equalsIgnoreCase( requiredAttribute.get() );
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

    public static Set<PwmSettingTemplate> valuesForType( final Type type )
    {
        return EnumUtil.readEnumsFromPredicate( PwmSettingTemplate.class, t -> t.getType() == type );
    }

    public enum Type
    {
        LDAP_VENDOR( PwmSetting.TEMPLATE_LDAP ),
        STORAGE( PwmSetting.TEMPLATE_STORAGE ),
        DB_VENDOR( PwmSetting.DB_VENDOR_TEMPLATE ),;

        private final PwmSetting pwmSetting;

        Type( final PwmSetting pwmSetting )
        {
            this.pwmSetting = pwmSetting;
        }

        public PwmSetting getPwmSetting()
        {
            return pwmSetting;
        }

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
