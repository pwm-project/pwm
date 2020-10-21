/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2020 The PWM Project
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

package password.pwm.http.tag.url;

import password.pwm.http.servlet.resource.ResourceFileServlet;

public enum PwmThemeURL
{
    THEME_URL( ResourceFileServlet.THEME_CSS_PATH ),
    MOBILE_THEME_URL( ResourceFileServlet.THEME_CSS_MOBILE_PATH ),
    CONFIG_THEME_URL( ResourceFileServlet.THEME_CSS_CONFIG_PATH ),;

    PwmThemeURL( final String cssName )
    {
        this.cssName = cssName;
    }

    private final String cssName;

    public String token( )
    {
        return "%" + this.toString() + "%";
    }

    public String getCssName( )
    {
        return cssName;
    }
}
