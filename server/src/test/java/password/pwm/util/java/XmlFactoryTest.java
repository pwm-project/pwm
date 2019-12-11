/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2019 The PWM Project
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

import org.junit.Assert;
import org.junit.Test;
import password.pwm.error.PwmUnrecoverableException;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;

public class XmlFactoryTest
{
    @Test
    public void testLoadXml()
            throws PwmUnrecoverableException
    {
        final InputStream xmlFactoryTestXmlFile = this.getClass().getResourceAsStream( "XmlFactoryTest.xml" );
        final XmlDocument xmlDocument = XmlFactory.getFactory().parseXml( xmlFactoryTestXmlFile );
        Assert.assertEquals( "PwmConfiguration", xmlDocument.getRootElement().getName() );
        final Optional<XmlElement> configIsEditable = xmlDocument.evaluateXpathToElement( "//property[@key='configIsEditable']" );
        Assert.assertTrue( configIsEditable.isPresent() );
        Assert.assertEquals( "false", configIsEditable.get().getText() );
        final List<XmlElement> allSettings = xmlDocument.evaluateXpathToElements( "//setting" );
        Assert.assertEquals( 279, allSettings.size() );
    }
}
