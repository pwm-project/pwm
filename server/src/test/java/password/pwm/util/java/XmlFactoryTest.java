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

package password.pwm.util.java;

import org.jrivard.xmlchai.AccessMode;
import org.jrivard.xmlchai.XmlChai;
import org.jrivard.xmlchai.XmlDocument;
import org.jrivard.xmlchai.XmlElement;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;

public class XmlFactoryTest
{
    @Test
    public void testLoadXml()
            throws Exception
    {
        final InputStream xmlFactoryTestXmlFile = this.getClass().getResourceAsStream( "XmlFactoryTest.xml" );
        final XmlDocument xmlDocument = XmlChai.getFactory().parse( xmlFactoryTestXmlFile, AccessMode.IMMUTABLE );
        Assertions.assertEquals( "PwmConfiguration", xmlDocument.getRootElement().getName() );
        final Optional<XmlElement> configIsEditable = xmlDocument.evaluateXpathToElement( "//property[@key='configIsEditable']" );
        Assertions.assertTrue( configIsEditable.isPresent() );
        Assertions.assertEquals( "false", configIsEditable.get().getText().orElseThrow() );
        final List<XmlElement> allSettings = xmlDocument.evaluateXpathToElements( "//setting" );
        Assertions.assertEquals( 279, allSettings.size() );
    }
}
