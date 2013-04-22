/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2012 The PWM Project
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

package password.pwm.config;

import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

public class ActionConfiguration implements Serializable {

    public enum Type { webservice, ldap }
    public enum WebMethod { delete, get, post, put }

    private String name;
    private String description;

    private Type type = Type.webservice;

    private WebMethod method = WebMethod.get;
    //private boolean clientSide;
    private Map<String,String> headers;
    private String url;
    private String body;

    private String attributeName;
    private String attributeValue;

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Type getType() {
        return type;
    }

    public WebMethod getMethod() {
        return method;
    }

    public String getUrl() {
        return url;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public String getBody() {
        return body;
    }

    public String getAttributeName() {
        return attributeName;
    }

    public String getAttributeValue() {
        return attributeValue;
    }

    public static ActionConfiguration parseOldConfigString(final String value) {
        final String[] splitString = value.split("=");
        final String attributeName = splitString[0];
        final String attributeValue = splitString[1];
        ActionConfiguration actionConfiguration = new ActionConfiguration();
        actionConfiguration.name = attributeName;
        actionConfiguration.description = attributeName;
        actionConfiguration.type = Type.ldap;
        actionConfiguration.attributeName = attributeName;
        actionConfiguration.attributeValue = attributeValue;
        return actionConfiguration;
    }

    public void validate() throws PwmOperationalException {
        if (this.getName() == null || this.getName().length() < 1) {
            throw new PwmOperationalException(PwmError.CONFIG_FORMAT_ERROR," form field name is required");
        }

        if (this.getType() == null) {
            throw new PwmOperationalException(PwmError.CONFIG_FORMAT_ERROR," type is required for field " + this.getName());
        }

        if (this.getType() == Type.webservice) {
            if (this.getMethod() == null) {
                throw new PwmOperationalException(PwmError.CONFIG_FORMAT_ERROR," method for webservice action " + this.getName() + " is required");
            }
            if (this.getUrl() == null || this.getUrl().length() < 1) {
                throw new PwmOperationalException(PwmError.CONFIG_FORMAT_ERROR," url for webservice action " + this.getName() + " is required");
            }
        } else if (this.getType() == Type.ldap) {
            if (this.getAttributeName() == null || this.getAttributeName().length() < 1) {
                throw new PwmOperationalException(PwmError.CONFIG_FORMAT_ERROR," attribute name for ldap action " + this.getName() + " is required");
            }
            if (this.getAttributeValue() == null || this.getAttributeValue().length() < 1) {
                throw new PwmOperationalException(PwmError.CONFIG_FORMAT_ERROR," attribute value for ldap action " + this.getName() + " is required");
            }
        }
    }
}
