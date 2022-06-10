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

import java.util.List;
import java.util.Set;

@Value
class TemplateSetReference<T>
{
    private final T reference;
    private final Set<PwmSettingTemplate> settingTemplates;

    static <T> T referenceForTempleSet(
            final List<TemplateSetReference<T>> templateSetReferences,
            final PwmSettingTemplateSet pwmSettingTemplateSet
    )
    {
        final PwmSettingTemplateSet effectiveTemplateSet = pwmSettingTemplateSet == null
                ? PwmSettingTemplateSet.getDefault()
                : pwmSettingTemplateSet;

        if ( templateSetReferences == null || templateSetReferences.isEmpty() )
        {
            throw new IllegalStateException( "templateSetReferences can not be null" );
        }

        if ( templateSetReferences.size() == 1 )
        {
            return templateSetReferences.get( 0 ).getReference();
        }

        for ( int matchCountExamSize = templateSetReferences.size(); matchCountExamSize > 0; matchCountExamSize-- )
        {
            for ( final TemplateSetReference<T> templateSetReference : templateSetReferences )
            {
                final Set<PwmSettingTemplate> temporarySet = CollectionUtil.copyToEnumSet( templateSetReference.getSettingTemplates(), PwmSettingTemplate.class );
                temporarySet.retainAll( effectiveTemplateSet.getTemplates() );
                final int matchCount = temporarySet.size();
                if ( matchCount == matchCountExamSize )
                {
                    return templateSetReference.getReference();
                }
            }
        }

        return templateSetReferences.get( 0 ).getReference();
    }
}
