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

import org.junit.Assert;
import org.junit.Test;
import password.pwm.config.PwmSetting;
import password.pwm.config.stored.XmlOutputProcessData;
import password.pwm.config.value.data.ActionConfiguration;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.XmlDocument;
import password.pwm.util.java.XmlElement;
import password.pwm.util.java.XmlFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

public class ActionValueTest
{
    @Test
    public void fromXmlTestFull()
            throws Exception
    {
        final String settingValueValue = xmlTestValue( "jsonDataFull" );
        final InputStream inputStream = new ByteArrayInputStream( settingValueValue.getBytes() );
        final XmlDocument xmlDocument = XmlFactory.getFactory().parseXml( inputStream );
        final XmlElement settingsElement = xmlDocument.getRootElement();
        final XmlElement settingElement = settingsElement.getChild( "setting" ).orElseThrow();
        final ActionValue actionValue = ( ActionValue ) ActionValue.factory().fromXmlElement( PwmSetting.CHANGE_PASSWORD_WRITE_ATTRIBUTES, settingElement, null );

        final List<ActionConfiguration> actionConfigurations = actionValue.toNativeObject();
        Assert.assertEquals( 1, actionConfigurations.size() );

        final ActionConfiguration action1 = actionConfigurations.get( 0 );
        Assert.assertEquals( "action1", action1.getName() );
        Assert.assertEquals( "description", action1.getDescription() );
        Assert.assertEquals( 1, action1.getWebActions().size() );
        Assert.assertEquals( 1, action1.getLdapActions().size() );

        final ActionConfiguration.WebAction webAction = action1.getWebActions().get( 0 );
        Assert.assertEquals( ActionConfiguration.WebMethod.post, webAction.getMethod() );
        Assert.assertEquals( 2, webAction.getHeaders().size() );
        Assert.assertEquals( "v1", webAction.getHeaders().get( "h1" ) );
        Assert.assertEquals( "v2", webAction.getHeaders().get( "h2" ) );
        Assert.assertEquals( "username", webAction.getUsername() );
        Assert.assertEquals( "password", webAction.getPassword() );
        Assert.assertEquals( "https://www.example.com", webAction.getUrl() );
        Assert.assertEquals( "BODY", webAction.getBody() );
        Assert.assertEquals( getTestCerts(), webAction.getCertificates() );
        Assert.assertEquals( 2, webAction.getSuccessStatus().size() );
        Assert.assertTrue( webAction.getSuccessStatus().contains( 200 ) );
        Assert.assertTrue( webAction.getSuccessStatus().contains( 201 ) );

        final ActionConfiguration.LdapAction ldapAction = action1.getLdapActions().get( 0 );
        Assert.assertEquals( "ldapAttribute1", ldapAction.getAttributeName() );
        Assert.assertEquals( "value1", ldapAction.getAttributeValue() );
        Assert.assertEquals( ActionConfiguration.LdapMethod.remove, ldapAction.getLdapMethod() );
    }

    @Test
    public void toXmlTestFull()
            throws Exception
    {
        final ActionConfiguration.WebAction webAction = ActionConfiguration.WebAction.builder()
                .method( ActionConfiguration.WebMethod.post )
                .headers( makeHeaderTestMap() )
                .username( "username" )
                .password( "password" )
                .url( "https://www.example.com" )
                .body( "BODY" )
                .certificates( getTestCerts() )
                .successStatus( List.of( 200, 201 ) )
                .build();

        final ActionConfiguration.LdapAction ldapAction = ActionConfiguration.LdapAction.builder()
                .attributeName( "ldapAttribute1" )
                .attributeValue( "value1" )
                .ldapMethod( ActionConfiguration.LdapMethod.remove )
                .build();

        final ActionConfiguration action1 = ActionConfiguration.builder()
                .name( "action1" )
                .description( "description" )
                .webActions( List.of( webAction ) )
                .ldapActions( List.of( ldapAction ) )
                .build();

        final ActionValue actionValue = new ActionValue( List.of( action1 ) );
        final XmlOutputProcessData xmlOutputProcessData = XmlOutputProcessData.builder()
                .storedValueEncoderMode( StoredValueEncoder.Mode.PLAIN )
                .build();
        final List<XmlElement> valueElements = actionValue.toXmlValues( "value", xmlOutputProcessData );
        final XmlElement settingElement = XmlFactory.getFactory().newElement( "setting" );
        settingElement.setAttribute( "syntaxVersion", "2" );
        settingElement.addContent( valueElements );

        final XmlDocument xmlDocument = XmlFactory.getFactory().newDocument( "settings" );
        xmlDocument.getRootElement().addContent( settingElement );

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        XmlFactory.getFactory().outputDocument( xmlDocument, baos, XmlFactory.OutputFlag.Compact );
        final String xmlStringOutput = baos.toString();

        Assert.assertEquals( xmlTestValue( "jsonDataFull" ), xmlStringOutput );
    }

    @Test
    public void fromXmlBasicWeb()
            throws Exception
    {
        final String settingValueValue = xmlTestValue( "jsonDataBasicWeb" );
        final InputStream inputStream = new ByteArrayInputStream( settingValueValue.getBytes() );
        final XmlDocument xmlDocument = XmlFactory.getFactory().parseXml( inputStream );
        final XmlElement settingsElement = xmlDocument.getRootElement();
        final XmlElement settingElement = settingsElement.getChild( "setting" ).orElseThrow();
        final ActionValue actionValue = ( ActionValue ) ActionValue.factory().fromXmlElement( PwmSetting.CHANGE_PASSWORD_WRITE_ATTRIBUTES, settingElement, null );

        final List<ActionConfiguration> actionConfigurations = actionValue.toNativeObject();
        Assert.assertEquals( 1, actionConfigurations.size() );

        final ActionConfiguration action1 = actionConfigurations.get( 0 );
        Assert.assertEquals( "action1", action1.getName() );
        Assert.assertNull(  action1.getDescription() );
        Assert.assertEquals( 1, action1.getWebActions().size() );
        Assert.assertEquals( 0, action1.getLdapActions().size() );

        final ActionConfiguration.WebAction webAction = action1.getWebActions().get( 0 );
        Assert.assertEquals( ActionConfiguration.WebMethod.get, webAction.getMethod() );
        Assert.assertEquals( "", webAction.getUsername() );
        Assert.assertEquals( "", webAction.getPassword() );
        Assert.assertEquals( "https://www.example.com", webAction.getUrl() );
        Assert.assertEquals( "", webAction.getBody() );
        Assert.assertEquals( 0, webAction.getCertificates().size() );
        Assert.assertEquals( 1, webAction.getSuccessStatus().size() );
        Assert.assertTrue( webAction.getSuccessStatus().contains( 200 ) );
    }


    @Test
    public void toXmlTestBasicWeb()
            throws Exception
    {
        final ActionConfiguration.WebAction webAction = ActionConfiguration.WebAction.builder()
                .method( ActionConfiguration.WebMethod.get )
                .url( "https://www.example.com" )
                .build();

        final ActionConfiguration action1 = ActionConfiguration.builder()
                .name( "action1" )
                .webActions( List.of( webAction ) )
                .build();

        final ActionValue actionValue = new ActionValue( List.of( action1 ) );
        final XmlOutputProcessData xmlOutputProcessData = XmlOutputProcessData.builder()
                .storedValueEncoderMode( StoredValueEncoder.Mode.PLAIN )
                .build();

        final List<XmlElement> valueElements = actionValue.toXmlValues( "value", xmlOutputProcessData );
        final XmlElement settingElement = XmlFactory.getFactory().newElement( "setting" );
        settingElement.setAttribute( "syntaxVersion", "2" );
        settingElement.addContent( valueElements );

        final XmlDocument xmlDocument = XmlFactory.getFactory().newDocument( "settings" );
        xmlDocument.getRootElement().addContent( settingElement );

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        XmlFactory.getFactory().outputDocument( xmlDocument, baos, XmlFactory.OutputFlag.Compact );
        final String xmlStringOutput = baos.toString();

        Assert.assertEquals( xmlTestValue( "jsonDataBasicWeb" ), xmlStringOutput );
    }

    private static Map<String, String> makeHeaderTestMap()
    {
        // ordered map is required for json string matching
        final Map<String, String> map = new LinkedHashMap<>();
        map.put( "h1", "v1" );
        map.put( "h2", "v2" );
        return Collections.unmodifiableMap( map );
    }

    private static List<X509Certificate> getTestCerts()
    {
        final String stringData = readResourceString( "certData1" );
        final X509Certificate[] certArray = JsonUtil.deserialize( stringData, X509Certificate[].class );
        return Arrays.asList( certArray );
    }

    private static String xmlTestValue(  final String key )
    {
        final String jsonValue = readResourceString( key );
        final String xmlValueValue = "<value>" + jsonValue + "</value>";
        final String settingValueValue = "<setting syntaxVersion=\"2\">" + xmlValueValue + "</setting>";
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><settings>" + settingValueValue + "</settings>";
    }

    private static String readResourceString( final String key )
    {
        final ResourceBundle bundle = ResourceBundle.getBundle( ActionValueTest.class.getName() );
        return bundle.getString( key );
    }
}
