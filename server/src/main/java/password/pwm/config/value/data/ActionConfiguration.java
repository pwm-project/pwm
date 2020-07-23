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

        @Builder.Default
        private List<Integer> successStatus = Collections.singletonList( 200 );
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

    @Value
    @Builder( toBuilder = true )
    public static class ActionConfigurationOldVersion1 implements Serializable
    {
        public enum Type
        {
            webservice,
            ldap,;
        }

        public enum WebMethod
        {
            delete( ActionConfiguration.WebMethod.delete ),
            get( ActionConfiguration.WebMethod.get ),
            post( ActionConfiguration.WebMethod.post ),
            put( ActionConfiguration.WebMethod.put ),
            patch( ActionConfiguration.WebMethod.patch ),;

            private final ActionConfiguration.WebMethod newMethod;

            WebMethod( final ActionConfiguration.WebMethod newMethod )
            {
                this.newMethod = newMethod;
            }

            public ActionConfiguration.WebMethod getNewMethod( )
            {
                return newMethod;
            }
        }

        public enum LdapMethod
        {
            replace( ActionConfiguration.LdapMethod.replace ),
            add( ActionConfiguration.LdapMethod.add ),
            remove( ActionConfiguration.LdapMethod.remove ),;

            private final ActionConfiguration.LdapMethod newMethod;

            LdapMethod( final ActionConfiguration.LdapMethod newType )
            {
                this.newMethod = newType;
            }

            public ActionConfiguration.LdapMethod getNewMethod( )
            {
                return newMethod;
            }
        }

        private String name;
        private String description;

        @Builder.Default
        private Type type = Type.webservice;

        @Builder.Default
        private WebMethod method = WebMethod.get;

        private Map<String, String> headers;
        private String url;
        private String body;
        private String username;
        private String password;
        private List<X509Certificate> certificates;

        private LdapMethod ldapMethod = LdapMethod.replace;
        private String attributeName;
        private String attributeValue;

        public static ActionConfigurationOldVersion1 parseOldConfigString( final String value )
        {
            final String[] splitString = value.split( "=" );
            final String attributeName = splitString[ 0 ];
            final String attributeValue = splitString[ 1 ];
            return ActionConfigurationOldVersion1.builder()
                    .name( attributeName )
                    .description( attributeName )
                    .type( Type.ldap )
                    .attributeName( attributeName )
                    .attributeValue( attributeValue )
                    .build();
        }

        public void validate( ) throws PwmOperationalException
        {
            if ( this.getName() == null || this.getName().length() < 1 )
            {
                throw new PwmOperationalException( new ErrorInformation( PwmError.CONFIG_FORMAT_ERROR, null, new String[]
                        {
                                " form field name is required",
                        }
                ) );
            }

            if ( this.getType() == null )
            {
                throw new PwmOperationalException( new ErrorInformation( PwmError.CONFIG_FORMAT_ERROR, null, new String[]
                        {
                                " type is required for field " + this.getName(),
                        }
                ) );
            }

            if ( this.getType() == Type.webservice )
            {
                if ( this.getMethod() == null )
                {
                    throw new PwmOperationalException( new ErrorInformation( PwmError.CONFIG_FORMAT_ERROR, null, new String[]
                            {
                                    " method for webservice action " + this.getName() + " is required",
                            }
                    ) );
                }
                if ( this.getUrl() == null || this.getUrl().length() < 1 )
                {
                    throw new PwmOperationalException( new ErrorInformation( PwmError.CONFIG_FORMAT_ERROR, null, new String[]
                            {
                                    " url for webservice action " + this.getName() + " is required",
                            } ) );
                }
            }
            else if ( this.getType() == Type.ldap )
            {
                if ( this.getAttributeName() == null || this.getAttributeName().length() < 1 )
                {
                    throw new PwmOperationalException( new ErrorInformation( PwmError.CONFIG_FORMAT_ERROR, null, new String[]
                            {
                                    " attribute name for ldap action " + this.getName() + " is required",
                            }
                    ) );
                }
                if ( this.getAttributeValue() == null || this.getAttributeValue().length() < 1 )
                {
                    throw new PwmOperationalException( new ErrorInformation( PwmError.CONFIG_FORMAT_ERROR, null, new String[]
                            {
                                    " attribute value for ldap action " + this.getName() + " is required",
                            }
                    ) );
                }
            }
        }
    }
}
