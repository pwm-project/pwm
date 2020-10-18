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

package password.pwm.config;

import password.pwm.i18n.Config;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.macro.MacroRequest;

import java.io.Serializable;
import java.util.Locale;

public class LDAPPermissionInfo implements Serializable
{
    private final Access access;
    private final Actor actor;

    public LDAPPermissionInfo( final Access type, final Actor actor )
    {
        this.access = type;
        this.actor = actor;
    }

    public Access getAccess( )
    {
        return access;
    }

    public Actor getActor( )
    {
        return actor;
    }

    public enum Access
    {
        read,
        write,
    }

    public enum Actor
    {
        proxy,
        self,
        self_other,
        helpdesk,
        guestManager,;

        public String getLabel( final Locale locale, final Configuration config )
        {
            return LocaleHelper.getLocalizedMessage( locale, "Actor_Label_" + this.toString(), config, Config.class );
        }

        public String getDescription( final Locale locale, final Configuration config )
        {
            final MacroRequest macroRequest = MacroRequest.forStatic();
            return macroRequest.expandMacros( LocaleHelper.getLocalizedMessage( locale, "Actor_Description_" + this.toString(), config, Config.class ) );
        }
    }
}
