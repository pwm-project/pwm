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

package password.pwm.ws.server;

import lombok.Builder;
import lombok.Value;
import password.pwm.config.value.data.FormConfiguration;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Value
@Builder
public class PresentableFormRow implements Serializable
{
    private String name;
    private int minimumLength;
    private int maximumLength;
    private FormConfiguration.Type type;
    private boolean required;
    private String label;
    private Map<String, String> selectOptions;

    public static PresentableFormRow fromFormConfiguration( final FormConfiguration formConfiguration, final Locale locale )
    {
        final String label = formConfiguration.getLabel( locale );

        return PresentableFormRow.builder()
                .name( formConfiguration.getName() )
                .label( label )
                .minimumLength( formConfiguration.getMinimumLength() )
                .maximumLength( formConfiguration.getMaximumLength() )
                .type( formConfiguration.getType() )
                .required ( formConfiguration.isRequired() )
                .selectOptions( formConfiguration.getSelectOptions() )
                .build();
    }

    public static List<PresentableFormRow> fromFormConfigurations( final List<FormConfiguration> formConfigurations, final Locale locale )
    {
        final List<PresentableFormRow> formRows = new ArrayList<>();
        for ( final FormConfiguration formConfiguration : formConfigurations )
        {
            formRows.add( PresentableFormRow.fromFormConfiguration( formConfiguration, locale ) );
        }
        return Collections.unmodifiableList( formRows );
    }
}


