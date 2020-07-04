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

package password.pwm.http.servlet.peoplesearch.bean;

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
public class SearchAttributeBean implements Serializable
{
    private String attribute;
    private String label;
    private FormConfiguration.Type type;
    private Map<String, String> options;

    public static List<SearchAttributeBean> searchAttributesFromForm(
            final Locale locale,
            final List<FormConfiguration> formConfigurations
    )
    {
        final List<SearchAttributeBean> returnList = new ArrayList<>( );
        for ( final FormConfiguration formConfiguration : formConfigurations )
        {
            final String attribute = formConfiguration.getName();
            final String label = formConfiguration.getLabel( locale );

            final SearchAttributeBean searchAttribute = SearchAttributeBean.builder()
                    .attribute( attribute )
                    .type( formConfiguration.getType() )
                    .label( label )
                    .options( formConfiguration.getSelectOptions() )
                    .build();

            returnList.add( searchAttribute );
        }

        return Collections.unmodifiableList( returnList );
    }
}
