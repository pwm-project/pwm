/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
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
