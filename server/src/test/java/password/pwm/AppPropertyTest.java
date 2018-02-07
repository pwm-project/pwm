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

package password.pwm;

import junit.framework.TestCase;
import org.junit.Assert;
import org.junit.Test;

import java.util.Enumeration;
import java.util.HashSet;
import java.util.ResourceBundle;
import java.util.Set;

public class AppPropertyTest extends TestCase {
    @Test
    public void testValues()
            throws Exception
    {
        for (final AppProperty appProperty : AppProperty.values()) {
            final String value = appProperty.getDefaultValue();
            Assert.assertNotNull("AppProperty " + appProperty + " does not have a value", value);
        }
    }

    @Test
    public void testKeys() {
        for (final AppProperty appProperty : AppProperty.values()) {
            final String key = appProperty.getKey();
            Assert.assertNotNull("AppProperty " + appProperty + " does not have a key", key);
        }
    }

    @Test
    public void testKeyValues() {
        final ResourceBundle resourceBundle = ResourceBundle.getBundle(AppProperty.class.getName());
        final Set<String> allResourceBundleKeys = new HashSet<>();
        final Set<String> allEnumKeys = new HashSet<>();

        for (final Enumeration enumeration = resourceBundle.getKeys(); enumeration.hasMoreElements(); ) {
            allResourceBundleKeys.add((String)enumeration.nextElement());
        }

        for (final AppProperty appProperty : AppProperty.values()) {
            allEnumKeys.add(appProperty.getKey());
        }

        final Set<String> bundleKeysMissingEnum = new HashSet<>(allResourceBundleKeys);
        bundleKeysMissingEnum.removeAll(allEnumKeys);
        if (!bundleKeysMissingEnum.isEmpty()) {
            Assert.fail("AppProperty resource bundle contains key " + bundleKeysMissingEnum.iterator().next()
                    + " does not have a corresponding Enum value");
        }

        final Set<String> enumKeysMissingResource = new HashSet<>(allEnumKeys);
        enumKeysMissingResource.removeAll(allResourceBundleKeys);
        if (!enumKeysMissingResource.isEmpty()) {
            Assert.fail("AppProperty enum contains key " + bundleKeysMissingEnum.iterator().next()
                    + " does not have a corresponding resource bundle value");
        }
    }
}
