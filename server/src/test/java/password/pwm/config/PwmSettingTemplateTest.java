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

import org.junit.Assert;
import org.junit.Test;
import password.pwm.util.java.JavaHelper;

import java.util.EnumSet;
import java.util.Set;

public class PwmSettingTemplateTest
{
    @Test
    public void testPwmSettingTemplateEnums() throws Exception
    {
        {
            for ( final PwmSettingTemplate.Type type : PwmSettingTemplate.Type.values() )
            {
                final Set<PwmSettingTemplate> seenTemplatesOfType = EnumSet.noneOf( PwmSettingTemplate.class );
                final PwmSetting associatedSetting = type.getPwmSetting();
                final Set<String> xmlValues = associatedSetting.getOptions().keySet();
                final Set<PwmSettingTemplate> enumValues = PwmSettingTemplate.valuesForType( type );

                for ( final String xmlValue : xmlValues )
                {
                    final PwmSettingTemplate pwmSettingTemplate = JavaHelper.readEnumFromString( PwmSettingTemplate.class, xmlValue )
                            .orElseThrow( () -> new IllegalStateException(
                                    "PwmSetting.xml has option value '" + xmlValue
                                            + "' for " + associatedSetting
                                            + " not declared as PwmSettingTemplate enum value" ) );
                    seenTemplatesOfType.add( pwmSettingTemplate );

                }

                for ( final PwmSettingTemplate enumValue : enumValues )
                {
                    if ( !seenTemplatesOfType.contains( enumValue ) )
                    {
                        Assert.fail( "PwmSettingTemplate enum value " + enumValue
                                + " is missing corresponding option value in setting " + associatedSetting );
                    }
                }
            }
        }
    }
}
