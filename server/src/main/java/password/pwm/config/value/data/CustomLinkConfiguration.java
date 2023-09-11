/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2021 The PWM Project
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

import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.java.CollectionUtil;

import java.util.Locale;
import java.util.Map;

/**
 * @author Richard A. Keil
 */
public record CustomLinkConfiguration(
        String name,
        Type type,
        Map<String, String> labels,
        Map<String, String> description,
        String customLinkUrl,
        boolean customLinkNewWindow,
        Map<String, String> selectOptions
)
{
    public CustomLinkConfiguration(
            final String name,
            final Type type,
            final Map<String, String> labels,
            final Map<String, String> description,
            final String customLinkUrl,
            final boolean customLinkNewWindow,
            final Map<String, String> selectOptions
    )
    {
        this.name = name;
        this.type = type == null ? Type.customLink : type;
        this.labels = CollectionUtil.stripNulls( labels );
        this.description = CollectionUtil.stripNulls( description );
        this.customLinkUrl = customLinkUrl == null ? "" : customLinkUrl;
        this.customLinkNewWindow = customLinkNewWindow;
        this.selectOptions = CollectionUtil.stripNulls( selectOptions );
    }

    public enum Type
    {
        text, url, select, checkbox, customLink
    }

    public String getLabel( final Locale locale )
    {
        return LocaleHelper.resolveStringKeyLocaleMap( locale, labels );
    }

    public String getDescription( final Locale locale )
    {
        return LocaleHelper.resolveStringKeyLocaleMap( locale, description );
    }
}
