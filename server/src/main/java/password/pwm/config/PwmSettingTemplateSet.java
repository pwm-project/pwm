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

import java.io.Serializable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class PwmSettingTemplateSet implements Serializable
{
    private final Set<PwmSettingTemplate> templates;

    public PwmSettingTemplateSet( final Set<PwmSettingTemplate> templates )
    {
        final Set<PwmSettingTemplate> workingSet = new HashSet<>();

        if ( templates != null )
        {
            for ( final PwmSettingTemplate template : templates )
            {
                if ( template != null )
                {
                    workingSet.add( template );
                }
            }
        }

        final Set<PwmSettingTemplate.Type> seenTypes = new HashSet<>();
        for ( final PwmSettingTemplate template : workingSet )
        {
            seenTypes.add( template.getType() );
        }

        for ( final PwmSettingTemplate.Type type : PwmSettingTemplate.Type.values() )
        {
            if ( !seenTypes.contains( type ) )
            {
                workingSet.add( type.getDefaultValue() );
            }
        }

        this.templates = Collections.unmodifiableSet( workingSet );
    }

    public Set<PwmSettingTemplate> getTemplates( )
    {
        return templates;
    }

    public static PwmSettingTemplateSet getDefault( )
    {
        return new PwmSettingTemplateSet( null );
    }
}
