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

package password.pwm.config.value;

import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Test;
import password.pwm.PwmConstants;
import password.pwm.config.PwmSetting;
import password.pwm.config.stored.ConfigurationCleanerTest;
import password.pwm.config.stored.XmlOutputProcessData;
import password.pwm.http.bean.ImmutableByteArray;
import password.pwm.util.java.XmlDocument;
import password.pwm.util.java.XmlElement;
import password.pwm.util.java.XmlFactory;
import password.pwm.util.secure.PwmHashAlgorithm;
import password.pwm.util.secure.SecureEngine;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

public class FileValueTest
{
    @Test
    public void fileValueToFromXmlTest()
            throws Exception
    {
        // use a configuration file as the raw file, the contents are unimportant for this test.
        try ( InputStream xmlFile = ConfigurationCleanerTest.class.getResourceAsStream( "ConfigurationCleanerTest.xml" ) )
        {
            final byte[] inputFile = IOUtils.toByteArray( xmlFile );
            final String inputHash = SecureEngine.hash( inputFile, PwmHashAlgorithm.SHA512 );

            final String xmlSettingValue;

            // output file to xml string
            {
                final FileValue fileValue = FileValue.newFileValue( "filename", "fileType", ImmutableByteArray.of( inputFile ) );
                final List<XmlElement> valueElements = fileValue.toXmlValues( "value", XmlOutputProcessData.builder().build() );
                final XmlDocument xmlDocument = XmlFactory.getFactory().newDocument( "root" );
                final XmlElement settingElement = XmlFactory.getFactory().newElement( "setting" );
                xmlDocument.getRootElement().addContent( settingElement );
                settingElement.addContent( valueElements );
                final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                XmlFactory.getFactory().outputDocument( xmlDocument, byteArrayOutputStream );
                xmlSettingValue = byteArrayOutputStream.toString( PwmConstants.DEFAULT_CHARSET );
            }

            final FileValue.FileInformation fileInformation;
            final FileValue.FileContent fileContent;

            // read filevalue from xml string
            {
                final XmlDocument xmlDocument = XmlFactory.getFactory().parseXml( new ByteArrayInputStream( xmlSettingValue.getBytes( PwmConstants.DEFAULT_CHARSET ) ) );
                final XmlElement settingElement = xmlDocument.getRootElement().getChild( "setting" ).orElseThrow();
                final FileValue fileValue = ( FileValue ) FileValue.factory().fromXmlElement( PwmSetting.DATABASE_JDBC_DRIVER, settingElement, null );
                final Map<FileValue.FileInformation, FileValue.FileContent> map = ( Map ) fileValue.toNativeObject();
                fileInformation = map.keySet().iterator().next();
                fileContent = map.values().iterator().next();
            }

            Assert.assertEquals( "filename", fileInformation.getFilename() );
            Assert.assertEquals( "fileType", fileInformation.getFiletype() );
            Assert.assertEquals( inputFile.length, fileContent.getContents().size() );
            Assert.assertArrayEquals( inputFile, fileContent.getContents().copyOf() );
            Assert.assertEquals( inputHash, fileContent.sha512sum() );
        }
    }
}
