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

package password.pwm;

import org.junit.Assert;
import org.junit.Test;

import java.util.Enumeration;
import java.util.HashSet;
import java.util.ResourceBundle;
import java.util.Set;

public class AppPropertyTest
{
    @Test
    public void testValues()
            throws Exception
    {
        for ( final AppProperty appProperty : AppProperty.values() )
        {
            final String value = appProperty.getDefaultValue();
            Assert.assertNotNull( "AppProperty " + appProperty + " does not have a value", value );
        }
    }

    @Test
    public void testKeys()
    {
        for ( final AppProperty appProperty : AppProperty.values() )
        {
            final String key = appProperty.getKey();
            Assert.assertNotNull( "AppProperty " + appProperty + " does not have a key", key );
        }
    }

    @Test
    public void testKeyValues()
    {
        final ResourceBundle resourceBundle = ResourceBundle.getBundle( AppProperty.class.getName() );
        final Set<String> allResourceBundleKeys = new HashSet<>();
        final Set<String> allEnumKeys = new HashSet<>();

        for ( final Enumeration enumeration = resourceBundle.getKeys(); enumeration.hasMoreElements(); )
        {
            allResourceBundleKeys.add( ( String ) enumeration.nextElement() );
        }

        for ( final AppProperty appProperty : AppProperty.values() )
        {
            allEnumKeys.add( appProperty.getKey() );
        }

        final Set<String> bundleKeysMissingEnum = new HashSet<>( allResourceBundleKeys );
        bundleKeysMissingEnum.removeAll( allEnumKeys );
        if ( !bundleKeysMissingEnum.isEmpty() )
        {
            Assert.fail( "AppProperty resource bundle contains key " + bundleKeysMissingEnum.iterator().next()
                    + " does not have a corresponding Enum value" );
        }

        final Set<String> enumKeysMissingResource = new HashSet<>( allEnumKeys );
        enumKeysMissingResource.removeAll( allResourceBundleKeys );
        if ( !enumKeysMissingResource.isEmpty() )
        {
            Assert.fail( "AppProperty enum contains key " + bundleKeysMissingEnum.iterator().next()
                    + " does not have a corresponding resource bundle value" );
        }
    }
}
