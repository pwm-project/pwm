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

package password.pwm.ldap.search;

import com.novell.ldapchai.provider.ChaiProvider;
import lombok.Builder;
import lombok.Getter;
import password.pwm.PwmConstants;
import password.pwm.config.value.data.FormConfiguration;
import password.pwm.config.value.data.UserPermission;
import password.pwm.util.java.StringUtil;

import java.io.Serializable;
import java.util.Collections;
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
            throw new IllegalArgumentException( "username OR formRows cannot both be supplied" );
        }
    }

    public static SearchConfiguration fromPermission( final UserPermission permission )
    {
        final SearchConfiguration.SearchConfigurationBuilder builder = SearchConfiguration.builder();
        if ( !StringUtil.isEmpty( permission.getLdapQuery() ) )
        {
            builder.filter( permission.getLdapQuery() );
        }

        if ( !StringUtil.isEmpty( permission.getLdapBase() ) )
        {
            builder.contexts( Collections.singletonList( permission.getLdapBase() ) );
        }

        {
            final String ldapProfileID = permission.getLdapProfileID();
            if ( !StringUtil.isEmpty( ldapProfileID ) )
            {
                if ( !PwmConstants.PROFILE_ID_ALL.equalsIgnoreCase( ldapProfileID ) )
                {
                    builder.ldapProfile( ldapProfileID );
                }
            }
        }

        return builder.build();
    }
}
