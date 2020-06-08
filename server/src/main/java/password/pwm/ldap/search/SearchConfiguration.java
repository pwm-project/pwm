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
import lombok.Value;
import password.pwm.PwmConstants;
import password.pwm.config.value.data.FormConfiguration;
import password.pwm.config.value.data.UserPermission;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Value
@Builder( toBuilder = true )
public class SearchConfiguration implements Serializable
{
    private static final long serialVersionUID = 1L;

    private String filter;
    private String ldapProfile;
    private String username;
    private String groupDN;
    private List<String> contexts;
    private Map<FormConfiguration, String> formValues;
    private transient ChaiProvider chaiProvider;
    private long searchTimeout;

    @Builder.Default
    private boolean ignoreOperationalErrors = false;

    @Builder.Default
    private boolean enableValueEscaping = true;

    @Builder.Default
    private boolean enableContextValidation = true;

    @Builder.Default
    private boolean enableSplitWhitespace = false;

    @Builder.Default
    private SearchScope searchScope = SearchScope.subtree;

    public enum SearchScope
    {
        base( com.novell.ldapchai.provider.SearchScope.BASE ),
        subtree( com.novell.ldapchai.provider.SearchScope.SUBTREE ),;

        private final com.novell.ldapchai.provider.SearchScope chaiSearchScope;

        SearchScope( final com.novell.ldapchai.provider.SearchScope chaiSearchScope )
        {
            this.chaiSearchScope = chaiSearchScope;
        }

        public com.novell.ldapchai.provider.SearchScope getChaiSearchScope()
        {
            return chaiSearchScope;
        }
    }

    void validate( )
    {
        if ( this.username != null && this.formValues != null )
        {
            throw new IllegalArgumentException( "username OR formRows cannot both be supplied" );
        }
    }

    public static SearchConfiguration fromPermission( final UserPermission userPermission ) throws PwmUnrecoverableException
    {
        final SearchConfiguration.SearchConfigurationBuilder builder = SearchConfiguration.builder();

        switch ( userPermission.getType() )
        {
            case ldapQuery:
            {
                builder.filter( userPermission.getLdapQuery() );
                if ( userPermission.getLdapBase() != null && !userPermission.getLdapBase().isEmpty() )
                {
                    builder.enableContextValidation( false );
                    builder.contexts( Collections.singletonList( userPermission.getLdapBase() ) );
                }
            }
            break;

            case ldapGroup:
            {
                builder.groupDN( userPermission.getLdapBase() );
            }
            break;

            default:
                throw new PwmUnrecoverableException( new ErrorInformation(
                        PwmError.ERROR_INTERNAL,
                        "unknown permission type: " + userPermission.getType() )
                );
        }

        if ( userPermission.getLdapProfileID() != null
                && !userPermission.getLdapProfileID().isEmpty()
                && !userPermission.getLdapProfileID().equals( PwmConstants.PROFILE_ID_ALL ) )
        {
            builder.ldapProfile( userPermission.getLdapProfileID() );
        }

        return builder.build();
    }
}
