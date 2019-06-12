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


