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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class PwmSettingTemplateSet implements Serializable
{
    private final Set<PwmSettingTemplate> templates;

    public PwmSettingTemplateSet( final Set<PwmSettingTemplate> templates )
    {
        final Set<PwmSettingTemplate> workingSet = EnumSet.noneOf( PwmSettingTemplate.class );

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

        final Set<PwmSettingTemplate.Type> seenTypes = EnumSet.noneOf( PwmSettingTemplate.Type.class );
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

    /**
     * Get all possible templateSets, useful for testing.
     * @return A list of all possible template sets.
     */
    public static List<PwmSettingTemplateSet> allValues()
    {
        final List<PwmSettingTemplateSet> templateSets = new ArrayList<>();

        for ( final PwmSettingTemplate template : PwmSettingTemplate.values() )
        {
            final PwmSettingTemplateSet templateSet = new PwmSettingTemplateSet( Collections.singleton( template ) );
            templateSets.add( templateSet );
        }

        templateSets.add( getDefault() );
        return Collections.unmodifiableList( templateSets );
    }
}
