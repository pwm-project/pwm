/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2016 The PWM Project
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

import password.pwm.i18n.Config;
import password.pwm.util.LocaleHelper;
import password.pwm.util.macro.MacroMachine;

import java.io.Serializable;
import java.util.Locale;

public class LDAPPermissionInfo implements Serializable {
    private final Access access;
    private final Actor actor;

    public LDAPPermissionInfo(Access type, Actor actor) {
        this.access = type;
        this.actor = actor;
    }

    public Access getAccess() {
        return access;
    }

    public Actor getActor() {
        return actor;
    }

    public enum Access {
        read,
        write,
    }

    public enum Actor {
        proxy,
        self,
        self_other,
        helpdesk,
        guestManager,

        ;

        public String getLabel(final Locale locale, final Configuration config) {
            return LocaleHelper.getLocalizedMessage(locale, "Actor_Label_" + this.toString(), config, Config.class);
        }

        public String getDescription(final Locale locale, final Configuration config) {
            final MacroMachine macroMachine = MacroMachine.forStatic();
            return macroMachine.expandMacros(LocaleHelper.getLocalizedMessage(locale, "Actor_Description_" + this.toString(), config, Config.class));
        }
    }
}
