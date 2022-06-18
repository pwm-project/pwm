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

import lombok.Value;
import password.pwm.util.java.CollectionUtil;

import java.io.Serializable;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Value
public class PwmSettingTemplateSet implements Serializable
{
    private final Set<PwmSettingTemplate> templates;

    public PwmSettingTemplateSet( final Set<PwmSettingTemplate> templates )
    {
        final Set<PwmSettingTemplate> workingSet = CollectionUtil.copyToEnumSet( templates, PwmSettingTemplate.class );

        final Set<PwmSettingTemplate.Type> seenTypes = workingSet.stream()
                .map( PwmSettingTemplate::getType )
                .collect( Collectors.toSet() );

        workingSet.addAll( EnumSet.allOf( PwmSettingTemplate.Type.class ).stream()
                .filter( type -> !seenTypes.contains( type ) )
                .map( PwmSettingTemplate.Type::getDefaultValue )
                .collect( Collectors.toUnmodifiableSet( ) ) );

        this.templates = Set.copyOf( workingSet );
    }

    public Set<PwmSettingTemplate> getTemplates( )
    {
        return templates;
    }

    public static PwmSettingTemplateSet getDefault( )
    {
        return new PwmSettingTemplateSet( null );
    }

    public boolean contains( final PwmSettingTemplate template )
    {
        return templates.contains( template );
    }

    /**
     * Get all possible templateSets, useful for testing.
     * @return A list of all possible template sets.
     */
    public static List<PwmSettingTemplateSet> allValues()
    {
        return EnumSet.allOf( PwmSettingTemplate.class ).stream()
                .map( pwmSettingTemplate -> new PwmSettingTemplateSet( Set.of( pwmSettingTemplate ) ) )
                .collect( Collectors.toUnmodifiableList() );
    }
}
