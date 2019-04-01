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

import lombok.Data;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.util.java.JsonUtil;

import java.io.Serializable;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;

@Data
public class ActionConfigurationOldVersion1 implements Serializable
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

    private ActionConfigurationOldVersion1.Type type = ActionConfigurationOldVersion1.Type.webservice;

    private ActionConfigurationOldVersion1.WebMethod method = ActionConfigurationOldVersion1.WebMethod.get;
    private Map<String, String> headers;
    private String url;
    private String body;
    private String username;
    private String password;
    private List<X509Certificate> certificates;


    private ActionConfigurationOldVersion1.LdapMethod ldapMethod = ActionConfigurationOldVersion1.LdapMethod.replace;
    private String attributeName;
    private String attributeValue;

    public static ActionConfigurationOldVersion1 parseOldConfigString( final String value )
    {
        final String[] splitString = value.split( "=" );
        final String attributeName = splitString[ 0 ];
        final String attributeValue = splitString[ 1 ];
        final ActionConfigurationOldVersion1 actionConfiguration = new ActionConfigurationOldVersion1();
        actionConfiguration.name = attributeName;
        actionConfiguration.description = attributeName;
        actionConfiguration.type = ActionConfigurationOldVersion1.Type.ldap;
        actionConfiguration.attributeName = attributeName;
        actionConfiguration.attributeValue = attributeValue;
        return actionConfiguration;
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

        if ( this.getType() == ActionConfigurationOldVersion1.Type.webservice )
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
        else if ( this.getType() == ActionConfigurationOldVersion1.Type.ldap )
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

    public ActionConfigurationOldVersion1 copyWithNewCertificate( final List<X509Certificate> certificates )
    {
        final ActionConfigurationOldVersion1 clone = JsonUtil.cloneUsingJson( this, ActionConfigurationOldVersion1.class );
        clone.certificates = certificates;
        return clone;
    }
}
