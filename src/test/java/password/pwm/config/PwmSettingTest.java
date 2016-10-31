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

import org.junit.Assert;
import org.junit.Test;
import password.pwm.PwmConstants;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class PwmSettingTest {

    @Test
    public void testDefaultValues() throws PwmUnrecoverableException, PwmOperationalException {
        for (PwmSetting pwmSetting : PwmSetting.values()) {
            for (final PwmSettingTemplate template : PwmSettingTemplate.values()) {
                PwmSettingTemplateSet templateSet = new PwmSettingTemplateSet(Collections.singleton(template));
                pwmSetting.getDefaultValue(templateSet);
            }
        }
    }

    @Test
    public void testDescriptions() throws PwmUnrecoverableException, PwmOperationalException {
        for (PwmSetting pwmSetting : PwmSetting.values()) {
            pwmSetting.getDescription(PwmConstants.DEFAULT_LOCALE);
        }
    }

    @Test
    public void testLabels() throws PwmUnrecoverableException, PwmOperationalException {
        for (PwmSetting pwmSetting : PwmSetting.values()) {
            pwmSetting.getLabel(PwmConstants.DEFAULT_LOCALE);
        }
    }

    @Test
    public void testFlags() throws PwmUnrecoverableException, PwmOperationalException {
        for (PwmSetting pwmSetting : PwmSetting.values()) {
            pwmSetting.getFlags();
        }
    }

    @Test
    public void testProperties() throws PwmUnrecoverableException, PwmOperationalException {
        for (PwmSetting pwmSetting : PwmSetting.values()) {
            pwmSetting.getProperties();
        }
    }

    @Test
    public void testOptions() throws PwmUnrecoverableException, PwmOperationalException {
        for (PwmSetting pwmSetting : PwmSetting.values()) {
            pwmSetting.getOptions();
        }
    }

    @Test
    public void testRegExPatterns() throws PwmUnrecoverableException, PwmOperationalException {
        for (PwmSetting pwmSetting : PwmSetting.values()) {
            pwmSetting.getRegExPattern();
        }
    }

    @Test
    public void testKeyUniqueness() {
        final Set<String> seenKeys = new HashSet<>();
        for (PwmSetting pwmSetting : PwmSetting.values()) {
            Assert.assertTrue(!seenKeys.contains(pwmSetting.getKey())); // duplicate key foud
            seenKeys.add(pwmSetting.getKey());
        }
    }
}
