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
            final PwmSetting setting = PwmSetting.forKey( settingKey );

            final String errorMsg = "PwmSetting.xml contains category/profile@setting key of '"
                    + settingKey + "' which does not exist in PwmSetting.java";
            Assert.assertNotNull( errorMsg, setting );
        }
    }
}


