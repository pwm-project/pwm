/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2019 The PWM Project
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

import password.pwm.config.stored.XmlOutputProcessData;
import password.pwm.error.PwmException;
import password.pwm.util.java.XmlElement;
import password.pwm.util.secure.PwmSecurityKey;

import java.io.Serializable;
import java.util.List;
import java.util.Locale;

public interface StoredValue extends Serializable
{
    List<XmlElement> toXmlValues( String valueElementName, XmlOutputProcessData xmlOutputProcessData );

    Object toNativeObject( );

    List<String> validateValue( PwmSetting pwm );

    Serializable toDebugJsonObject( Locale locale );

    String toDebugString( Locale locale );

    int currentSyntaxVersion( );

    interface StoredValueFactory
    {
        StoredValue fromJson( String input );

        StoredValue fromXmlElement( PwmSetting pwmSetting, XmlElement settingElement, PwmSecurityKey key )
                throws PwmException;
    }

    String valueHash();
}
