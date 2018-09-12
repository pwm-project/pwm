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

package password.pwm.health;

import org.junit.Assert;
import org.junit.Test;
import password.pwm.PwmConstants;
import password.pwm.config.Configuration;
import password.pwm.config.stored.StoredConfigurationImpl;
import password.pwm.error.PwmUnrecoverableException;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class HealthMessageTest {

    @Test
    public void testHealthMessageUniqueKeys() {
        final Set<String> seenKeys = new HashSet<>();
        for (final HealthMessage healthMessage : HealthMessage.values()) {
            Assert.assertTrue(!seenKeys.contains(healthMessage.getKey())); // duplicate key foud
            seenKeys.add(healthMessage.getKey());
        }
    }

    @Test
    public void testHealthMessageDescription() throws PwmUnrecoverableException {
        final Configuration configuration = new Configuration(StoredConfigurationImpl.newStoredConfiguration());
        final Locale locale = PwmConstants.DEFAULT_LOCALE;
        for (final HealthMessage healthMessage : HealthMessage.values()) {
            healthMessage.getDescription(locale, configuration, new String[]{"field1", "field2"});
        }
    }
}
