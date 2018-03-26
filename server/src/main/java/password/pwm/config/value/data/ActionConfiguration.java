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

import lombok.Getter;
import lombok.Setter;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.util.java.JsonUtil;

import java.io.Serializable;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class ActionConfiguration implements Serializable
{

    public enum Type
    {
        webservice, ldap
    }

    public enum WebMethod
    {
        delete, get, post, put, patch
    }

    public enum LdapMethod
    {
        replace, add, remove
    }

    private String name;
    private String description;

    private Type type = Type.webservice;

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

    public static ActionConfiguration parseOldConfigString( final String value )
    {
        final String[] splitString = value.split( "=" );
        final String attributeName = splitString[ 0 ];
        final String attributeValue = splitString[ 1 ];
        final ActionConfiguration actionConfiguration = new ActionConfiguration();
        actionConfiguration.name = attributeName;
        actionConfiguration.description = attributeName;
        actionConfiguration.type = Type.ldap;
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

    public ActionConfiguration copyWithNewCertificate( final List<X509Certificate> certificates )
    {
        final ActionConfiguration clone = JsonUtil.cloneUsingJson( this, ActionConfiguration.class );
        clone.certificates = certificates;
        return clone;
    }
}
