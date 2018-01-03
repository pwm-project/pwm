/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2017 The PWM Project
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

package password.pwm.ldap.search;

import com.novell.ldapchai.provider.ChaiProvider;
import lombok.Builder;
import lombok.Getter;
import password.pwm.config.value.data.FormConfiguration;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Builder
@Getter
public class SearchConfiguration implements Serializable
{

    private String filter;
    private String ldapProfile;
    private String username;
    private String groupDN;
    private List<String> contexts;
    private Map<FormConfiguration, String> formValues;
    private transient ChaiProvider chaiProvider;
    private long searchTimeout;

    @Builder.Default
    private boolean enableValueEscaping = true;

    @Builder.Default
    private boolean enableContextValidation = true;

    @Builder.Default
    private boolean enableSplitWhitespace = false;

    void validate( )
    {
        if ( this.username != null && this.formValues != null )
        {
            throw new IllegalArgumentException( "username OR formValues cannot both be supplied" );
        }
    }

}
