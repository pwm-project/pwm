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

package password.pwm.config;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.config.stored.StoredConfigXmlConstants;
import password.pwm.config.stored.XmlOutputProcessData;
import password.pwm.config.value.StoredValueEncoder;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.XmlDocument;
import password.pwm.util.java.XmlElement;
import password.pwm.util.java.XmlFactory;
import password.pwm.util.localdb.TestHelper;
import password.pwm.util.secure.PwmSecurityKey;

import java.io.InputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class PwmSettingTest
{
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void testDefaultValues() throws Exception
    {
        final PwmApplication pwmApplication = TestHelper.makeTestPwmApplication( temporaryFolder.newFolder() );
        final PwmSecurityKey pwmSecurityKey = new PwmSecurityKey( "abcdefghijklmnopqrstuvwxyz" );
        final XmlOutputProcessData outputSettings = XmlOutputProcessData.builder()
                .pwmSecurityKey( pwmSecurityKey )
                .storedValueEncoderMode( StoredValueEncoder.Mode.ENCODED )
                .build();
        for ( final PwmSetting pwmSetting : PwmSetting.values() )
        {
            for ( final PwmSettingTemplate template : PwmSettingTemplate.values() )
            {
                final PwmSettingTemplateSet templateSet = new PwmSettingTemplateSet( Collections.singleton( template ) );
                final StoredValue storedValue = pwmSetting.getDefaultValue( templateSet );
                storedValue.toNativeObject();
                storedValue.toDebugString( PwmConstants.DEFAULT_LOCALE );
                storedValue.toDebugJsonObject( PwmConstants.DEFAULT_LOCALE );
                storedValue.toXmlValues( StoredConfigXmlConstants.XML_ELEMENT_VALUE, outputSettings );
                storedValue.validateValue( pwmSetting );
                Assert.assertNotNull( storedValue.valueHash() );
            }
        }
    }

    @Test
    public void testSettingXmlPresence() throws PwmUnrecoverableException
    {
        final InputStream inputStream = PwmSetting.class.getClassLoader().getResourceAsStream( PwmSettingXml.SETTING_XML_FILENAME );
        final XmlDocument xmlDoc = XmlFactory.getFactory().parseXml( inputStream );

        for ( final PwmSetting pwmSetting : PwmSetting.values() )
        {
            final String expression = "/settings/setting[@key=\"" + pwmSetting.getKey() + "\"]";
            final Optional<XmlElement> xmlElement = xmlDoc.evaluateXpathToElement( expression );
            Assert.assertTrue( "missing PwmSetting.xml setting reference for key " + pwmSetting.getKey(), xmlElement.isPresent() );
        }
    }

    @Test
    public void testSettingXmlDuplication() throws PwmUnrecoverableException
    {
        final InputStream inputStream = PwmSetting.class.getClassLoader().getResourceAsStream( PwmSettingXml.SETTING_XML_FILENAME );
        final XmlDocument xmlDoc = XmlFactory.getFactory().parseXml( inputStream );

        for ( final PwmSetting pwmSetting : PwmSetting.values() )
        {
            final String expression = "/settings/setting[@key=\"" + pwmSetting.getKey() + "\"]";
            final List<XmlElement> results = xmlDoc.evaluateXpathToElements( expression );
            Assert.assertFalse( "multiple PwmSetting.xml setting reference for key " + pwmSetting.getKey(), results.size() > 1 );
        }
    }

    @Test
    public void testUnknownSettingXml() throws PwmUnrecoverableException
    {
        final InputStream inputStream = PwmSetting.class.getClassLoader().getResourceAsStream( PwmSettingXml.SETTING_XML_FILENAME );
        final XmlDocument xmlDoc = XmlFactory.getFactory().parseXml( inputStream );

        final String expression = "/settings/setting";
        final List<XmlElement> results = xmlDoc.evaluateXpathToElements( expression );
        for ( final XmlElement result : results )
        {
            final String key = result.getAttributeValue( "key" );
            Assert.assertFalse( StringUtil.isEmpty( key ) );
            final PwmSetting pwmSetting = PwmSetting.forKey( key );
            Assert.assertNotNull( "unknown PwmSetting.xml setting reference for key " + key );
        }
    }

    @Test
    public void testLabels() throws PwmUnrecoverableException, PwmOperationalException
    {
        for ( final PwmSetting pwmSetting : PwmSetting.values() )
        {
            try
            {
                pwmSetting.getLabel( PwmConstants.DEFAULT_LOCALE );
            }
            catch ( final Throwable t )
            {
                throw new IllegalStateException( "unable to read label for setting '" + pwmSetting.toString() + "', error: " + t.getMessage(), t );
            }
        }
    }

    @Test
    public void testFlags() throws PwmUnrecoverableException, PwmOperationalException
    {
        for ( final PwmSetting pwmSetting : PwmSetting.values() )
        {
            pwmSetting.getFlags();
        }
    }

    @Test
    public void testProperties() throws PwmUnrecoverableException, PwmOperationalException
    {
        for ( final PwmSetting pwmSetting : PwmSetting.values() )
        {
            try
            {
                pwmSetting.getProperties();
            }
            catch ( final Throwable t )
            {
                throw new IllegalStateException( "unable to read properties for setting '" + pwmSetting.toString() + "', error: " + t.getMessage(), t );
            }
        }
    }

    @Test
    public void testOptions() throws PwmUnrecoverableException, PwmOperationalException
    {
        for ( final PwmSetting pwmSetting : PwmSetting.values() )
        {
            try
            {
                pwmSetting.getOptions();
            }
            catch ( final Throwable t )
            {
                throw new IllegalStateException( "unable to read options for setting '" + pwmSetting.toString() + "', error: " + t.getMessage(), t );
            }

        }
    }

    @Test
    public void testRegExPatterns() throws PwmUnrecoverableException, PwmOperationalException
    {
        for ( final PwmSetting pwmSetting : PwmSetting.values() )
        {
            pwmSetting.getRegExPattern();
        }
    }

    @Test
    public void testKeyUniqueness()
    {
        final Set<String> seenKeys = new HashSet<>();
        for ( final PwmSetting pwmSetting : PwmSetting.values() )
        {
            // duplicate key foud
            Assert.assertTrue( !seenKeys.contains( pwmSetting.getKey() ) );
            seenKeys.add( pwmSetting.getKey() );
        }
    }

    @Test
    public void testMinMaxValueRanges()
    {
        for ( final PwmSetting pwmSetting : PwmSetting.values() )
        {
            final long minValue = Long.parseLong( pwmSetting.getProperties().getOrDefault( PwmSettingProperty.Minimum, "0" ) );
            final long maxValue = Long.parseLong( pwmSetting.getProperties().getOrDefault( PwmSettingProperty.Maximum, "0" ) );
            if ( maxValue != 0 )
            {
                Assert.assertTrue( maxValue > minValue );
            }
        }

    }
}
