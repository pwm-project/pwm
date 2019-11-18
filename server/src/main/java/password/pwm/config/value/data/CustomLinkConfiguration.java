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

import lombok.Value;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.java.JsonUtil;

import java.io.Serializable;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;

/**
 * @author Richard A. Keil
 */
@Value
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
