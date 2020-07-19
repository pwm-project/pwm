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

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.XmlDocument;
import password.pwm.util.java.XmlElement;
import password.pwm.util.java.XmlFactory;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;

public class PwmSettingXmlTest
{
    private static XmlDocument xmlDocument;

    @BeforeClass
    public static void setUp() throws Exception
    {
        try ( InputStream inputStream = PwmSetting.class.getClassLoader().getResourceAsStream( PwmSettingXml.SETTING_XML_FILENAME ) )
        {
            xmlDocument = XmlFactory.getFactory().parseXml( inputStream );
        }
    }

    @AfterClass
    public static void tearDown()
    {
        xmlDocument = null;
    }

    @Test
    public void testSettingElementIsInXml()
    {
        for ( final PwmSetting pwmSetting : PwmSetting.values() )
        {
            final XmlElement element = PwmSettingXml.readSettingXml( pwmSetting );
            Assert.assertNotNull( "no XML settings node in PwmSetting.xml for setting " + pwmSetting.getKey(), element );
        }
    }

    @Test
    public void testXmlElementIsInSettings()
    {
        final List<XmlElement> settingElements = xmlDocument.evaluateXpathToElements( "/settings/setting" );
        Assert.assertFalse( settingElements.isEmpty() );
        for ( final XmlElement element : settingElements )
        {
            final String key = element.getAttributeValue( "key" );

            final String errorMsg = "PwmSetting.xml contains setting key of '"
                    + key + "' which does not exist in PwmSetting.java";
            Assert.assertNotNull( errorMsg, PwmSetting.forKey( key ) );
        }
    }

    @Test
    public void testCategoryElementIsInXml()
    {
        for ( final PwmSettingCategory pwmSettingCategory : PwmSettingCategory.values() )
        {
            final XmlElement element = PwmSettingXml.readCategoryXml( pwmSettingCategory );
            Assert.assertNotNull( "no XML category node in PwmSetting.xml for setting " + pwmSettingCategory.getKey(), element );
        }
    }

    @Test
    public void testXmlElementIsInCategory()
    {
        final List<XmlElement> categoryElements = xmlDocument.evaluateXpathToElements( "/settings/category" );
        Assert.assertFalse( categoryElements.isEmpty() );
        for ( final XmlElement element : categoryElements )
        {
            final String key = element.getAttributeValue( "key" );
            final PwmSettingCategory category = JavaHelper.readEnumFromString( PwmSettingCategory.class, null, key );

            final String errorMsg = "PwmSetting.xml contains category key of '"
                    + key + "' which does not exist in PwmSettingCategory.java";
            Assert.assertNotNull( errorMsg, category );
        }
    }

    @Test
    public void testXmlCategoryProfileElementIsValidSetting()
    {
        final List<XmlElement> profileElements = xmlDocument.evaluateXpathToElements( "/settings/category/profile" );
        Assert.assertFalse( profileElements.isEmpty() );
        for ( final XmlElement element : profileElements )
        {
            final String settingKey = element.getAttributeValue( "setting" );
            final Optional<PwmSetting> setting = PwmSetting.forKey( settingKey );

            final String errorMsg = "PwmSetting.xml contains category/profile@setting key of '"
                    + settingKey + "' which does not exist in PwmSetting.java";
            Assert.assertTrue( errorMsg, setting.isPresent() );
        }
    }
}


