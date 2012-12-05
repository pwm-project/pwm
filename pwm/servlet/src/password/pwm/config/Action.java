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

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;

public class Action implements Serializable {

    public enum Type { webservice, ldap }
    public enum WebMethod { get, post }

    private String name;
    private Map<String,String> labels = Collections.singletonMap("", "");
    private Map<String,String> description = Collections.singletonMap("","");

    private Type type = Type.webservice;

    private WebMethod webMethod = WebMethod.get;
    private String url;
    private String body;

    private String attributeName;
    private String attributeValue;
}
