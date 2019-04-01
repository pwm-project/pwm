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

package password.pwm.config;

import lombok.Getter;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.java.JsonUtil;

import java.io.Serializable;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;

/**
 * @author Richard A. Keil
 */
@Getter
public class CustomLinkConfiguration implements Serializable
{

    public enum Type
    {
        text, url, select, checkbox, customLink
    }

    private String name;
    private Type type = Type.customLink;
    private Map<String, String> labels = Collections.singletonMap( "", "" );
    private Map<String, String> description = Collections.singletonMap( "", "" );
    private String customLinkUrl = "";
    private boolean customLinkNewWindow;
    private Map<String, String> selectOptions = Collections.emptyMap();

    public String getLabel( final Locale locale )
    {
        return LocaleHelper.resolveStringKeyLocaleMap( locale, labels );
    }

    public String getDescription( final Locale locale )
    {
        return LocaleHelper.resolveStringKeyLocaleMap( locale, description );
    }

    public String toString( )
    {
        final StringBuilder sb = new StringBuilder();

        sb.append( "CustomLink: " );
        sb.append( JsonUtil.serialize( this ) );

        return sb.toString();
    }
}
