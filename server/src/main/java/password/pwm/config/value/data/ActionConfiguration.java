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

package password.pwm.config.value.data;

import lombok.Builder;
import lombok.Value;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.util.java.StringUtil;

import java.io.Serializable;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Value
@Builder( toBuilder = true )
public class ActionConfiguration implements Serializable
{

    public enum WebMethod
    {
        delete, get, post, put, patch
    }

    public enum LdapMethod
    {
        replace, add, remove
    }

    @Value
    @Builder( toBuilder = true )
    public static class WebAction implements Serializable
    {
        @Builder.Default
        private ActionConfiguration.WebMethod method = ActionConfiguration.WebMethod.get;

        @Builder.Default
        private Map<String, String> headers = Collections.emptyMap();

        @Builder.Default
        private String url = "";

        @Builder.Default
        private String body = "";

        @Builder.Default
        private String username = "";

        @Builder.Default
        private String password = "";

        @Builder.Default
        private List<X509Certificate> certificates = Collections.emptyList();
    }

    @Value
    @Builder
    public static class LdapAction implements Serializable
    {
        @Builder.Default
        private ActionConfiguration.LdapMethod ldapMethod = ActionConfiguration.LdapMethod.replace;

        @Builder.Default
        private String attributeName = "";

        @Builder.Default
        private String attributeValue = "";
    }

    private String name;
    private String description;

    @Builder.Default
    private List<ActionConfiguration.WebAction> webActions = Collections.emptyList();

    @Builder.Default
    private List<ActionConfiguration.LdapAction> ldapActions = Collections.emptyList();

    public void validate( ) throws PwmOperationalException
    {
        if ( StringUtil.isEmpty( this.getName() ) )
        {
            throw new PwmOperationalException( new ErrorInformation( PwmError.CONFIG_FORMAT_ERROR, null, new String[]
                    {
                            " form field name is required",
                    }
            ) );
        }

        for ( final ActionConfiguration.WebAction webAction : webActions )
        {
            if ( webAction.getMethod() == null )
            {
                throw new PwmOperationalException( new ErrorInformation( PwmError.CONFIG_FORMAT_ERROR, null, new String[]
                        {
                                " method for webservice action " + this.getName() + " is required",
                        }
                ) );
            }
        }
    }
}
