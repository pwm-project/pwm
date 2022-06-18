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

package password.pwm.config;

import org.jrivard.xmlchai.AccessMode;
import org.jrivard.xmlchai.XmlChai;
import org.jrivard.xmlchai.XmlDocument;
import org.jrivard.xmlchai.XmlElement;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import password.pwm.PwmConstants;
import password.pwm.config.stored.StoredConfigXmlConstants;
import password.pwm.config.stored.XmlOutputProcessData;
import password.pwm.config.value.StoredValue;
import password.pwm.config.value.StoredValueEncoder;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.StringUtil;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.secure.PwmSecurityKey;

import java.io.InputStream;
import java.io.Serializable;
import java.util.EnumSet;
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
        final PwmSecurityKey pwmSecurityKey = new PwmSecurityKey( "abcdefghijklmnopqrstuvwxyz" );
        final XmlOutputProcessData outputSettings = XmlOutputProcessData.builder()
                .pwmSecurityKey( pwmSecurityKey )
                .storedValueEncoderMode( StoredValueEncoder.Mode.ENCODED )
                .build();

        final List<PwmSettingTemplateSet> allPwmSettingTemplates = PwmSettingTemplateSet.allValues();

        for ( final PwmSetting pwmSetting : PwmSetting.values() )
        {
            //System.out.println( pwmSetting.name() + " " + pwmSetting.getKey()  );
            for ( final PwmSettingTemplateSet templateSet : allPwmSettingTemplates )
            {
                final StoredValue storedValue = pwmSetting.getDefaultValue( templateSet );
                storedValue.toNativeObject();
                storedValue.toDebugString( PwmConstants.DEFAULT_LOCALE );
                storedValue.toDebugJsonObject( PwmConstants.DEFAULT_LOCALE );
                storedValue.toXmlValues( StoredConfigXmlConstants.XML_ELEMENT_VALUE, outputSettings );
                storedValue.validateValue( pwmSetting );
                Assert.assertNotNull( storedValue.valueHash() );
                if ( storedValue.toNativeObject() != null )
                {
                    JsonFactory.get().serialize( ( Serializable ) storedValue.toNativeObject() );
                }
            }
        }
    }

    @Test
    public void testSettingXmlPresence() throws Exception
    {
        final InputStream inputStream = PwmSetting.class.getClassLoader().getResourceAsStream( PwmSettingXml.SETTING_XML_FILENAME );
        final XmlDocument xmlDoc = XmlChai.getFactory().parse( inputStream, AccessMode.IMMUTABLE );

        for ( final PwmSetting pwmSetting : PwmSetting.values() )
        {
            final String expression = "/settings/setting[@key=\"" + pwmSetting.getKey() + "\"]";
            final Optional<XmlElement> xmlElement = xmlDoc.evaluateXpathToElement( expression );
            Assert.assertTrue( "missing PwmSetting.xml setting reference for key " + pwmSetting.getKey(), xmlElement.isPresent() );
        }
    }

    @Test
    public void testSettingXmlDuplication() throws Exception
    {
        final InputStream inputStream = PwmSetting.class.getClassLoader().getResourceAsStream( PwmSettingXml.SETTING_XML_FILENAME );
        final XmlDocument xmlDoc = XmlChai.getFactory().parse( inputStream, AccessMode.IMMUTABLE );

        for ( final PwmSetting pwmSetting : PwmSetting.values() )
        {
            final String expression = "/settings/setting[@key=\"" + pwmSetting.getKey() + "\"]";
            final List<XmlElement> results = xmlDoc.evaluateXpathToElements( expression );
            Assert.assertFalse( "multiple PwmSetting.xml setting reference for key " + pwmSetting.getKey(), results.size() > 1 );
        }
    }

    @Test
    public void testUnknownSettingXml() throws Exception
    {
        final InputStream inputStream = PwmSetting.class.getClassLoader().getResourceAsStream( PwmSettingXml.SETTING_XML_FILENAME );
        final XmlDocument xmlDoc = XmlChai.getFactory().parse( inputStream, AccessMode.IMMUTABLE );

        final String expression = "/settings/setting";
        final List<XmlElement> results = xmlDoc.evaluateXpathToElements( expression );
        for ( final XmlElement result : results )
        {
            final String key = result.getAttribute( "key" )
                    .orElseThrow( () -> new IllegalStateException( "setting element " + result.getName() + " missing key attribute" ) );

            Assert.assertFalse( StringUtil.isEmpty( key ) );
            final Optional<PwmSetting> pwmSetting = PwmSetting.forKey( key );
            Assert.assertTrue( "unknown PwmSetting.xml setting reference for key " + key, pwmSetting.isPresent() );
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
            // duplicate key found
            Assert.assertFalse( seenKeys.contains( pwmSetting.getKey() ) );
            seenKeys.add( pwmSetting.getKey() );
        }
        Assert.assertEquals( seenKeys.size(), PwmSetting.values().length );
    }

    @Test
    public void sortedByMenuLocation()
    {
        final List<PwmSetting> list = PwmSetting.sortedValues();
        Assert.assertEquals( list.size(), PwmSetting.values().length );
    }


    @Test
    public void testAllSettingMethods()
    {
        for ( final PwmSetting pwmSetting : EnumSet.allOf( PwmSetting.class ) )
        {
            pwmSetting.getProperties();
            pwmSetting.getFlags();
            pwmSetting.getOptions();
            pwmSetting.getLabel( PwmConstants.DEFAULT_LOCALE );
            pwmSetting.getDescription( PwmConstants.DEFAULT_LOCALE );
            pwmSetting.isRequired();
            pwmSetting.isHidden();
            pwmSetting.getLevel();
            pwmSetting.getRegExPattern();
            pwmSetting.getLDAPPermissionInfo();
            PwmSettingTemplateSet.allValues().forEach( pwmSetting::getDefaultValue );
            PwmSettingTemplateSet.allValues().forEach( pwmSetting::getExample );

            pwmSetting.toMenuLocationDebug( null, PwmConstants.DEFAULT_LOCALE );
        }
    }

}
