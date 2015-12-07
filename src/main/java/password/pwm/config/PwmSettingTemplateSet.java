/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2015 The PWM Project
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
 *
 */

package password.pwm.config;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class PwmSettingTemplateSet implements Serializable {
    private final PwmSettingLdapTemplate ldapTemplate;
    private final PwmSettingStorageTemplate storageTemplate;

    public PwmSettingTemplateSet(final PwmSettingLdapTemplate ldapTemplate, final PwmSettingStorageTemplate storageTemplate) {
        this.ldapTemplate = ldapTemplate == null ? PwmSettingLdapTemplate.DEFAULT : ldapTemplate;
        this.storageTemplate = storageTemplate == null ? PwmSettingStorageTemplate.LDAP : storageTemplate;

    }

    public PwmSettingLdapTemplate getLdapTemplate() {
        return ldapTemplate;
    }

    public PwmSettingStorageTemplate getStorageTemplate() {
        return storageTemplate;
    }

    public Set<PwmSettingTemplate> getTemplates() {
        final HashSet<PwmSettingTemplate> returnObj = new HashSet<>();
        returnObj.add(ldapTemplate);
        returnObj.add(storageTemplate);
        return Collections.unmodifiableSet(returnObj);
    }
}
