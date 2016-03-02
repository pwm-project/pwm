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

import java.io.Serializable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class PwmSettingTemplateSet implements Serializable {
    private final Set<PwmSettingTemplate> templates;

    public PwmSettingTemplateSet(final Set<PwmSettingTemplate> templates) {
        final Set<PwmSettingTemplate> workingSet = new HashSet<>();

        if (templates != null) {
            for (final PwmSettingTemplate template : templates) {
                if (template != null) {
                    workingSet.add(template);
                }
            }
        }

        final Set<PwmSettingTemplate.Type> seenTypes = new HashSet<>();
        for (final PwmSettingTemplate template : workingSet) {
            seenTypes.add(template.getType());
        }

        for (final PwmSettingTemplate.Type type : PwmSettingTemplate.Type.values()) {
            if (!seenTypes.contains(type)) {
                workingSet.add(type.getDefaultValue());
            }
        }

        this.templates = Collections.unmodifiableSet(workingSet);
    }

    public Set<PwmSettingTemplate> getTemplates() {
        return templates;
    }

    public static PwmSettingTemplateSet getDefault() {
        return new PwmSettingTemplateSet(null);
    }
}
