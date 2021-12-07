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

package password.pwm.util.json;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import password.pwm.bean.DomainID;
import password.pwm.bean.SessionLabel;
import password.pwm.config.stored.StoredConfigurationFactory;
import password.pwm.config.value.data.ActionConfiguration;
import password.pwm.config.value.data.UserPermission;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.servlet.configeditor.data.NavTreeDataMaker;
import password.pwm.http.servlet.configeditor.data.NavTreeItem;
import password.pwm.http.servlet.configeditor.data.NavTreeSettings;
import password.pwm.ldap.PwmLdapVendor;
import password.pwm.ldap.permission.UserPermissionType;
import password.pwm.util.PasswordData;
import password.pwm.util.logging.PwmLogEvent;
import password.pwm.util.logging.PwmLogLevel;
import password.pwm.util.secure.X509Utils;
import password.pwm.ws.server.RestResultBean;

import java.io.IOException;
import java.io.Serializable;
import java.math.BigDecimal;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.LongAdder;

@RunWith( value = Parameterized.class )
public class JsonProviderTest
{
    private final JsonProvider instance;

    public JsonProviderTest( final JsonProvider instance )
    {
        this.instance = instance;
    }

    @Parameterized.Parameters
    public static Collection<JsonProvider> data()
    {
        return List.of(
                JsonFactory.get( JsonFactory.JsonImpl.moshi ),
                JsonFactory.get( JsonFactory.JsonImpl.gson ) );
    }

    @Test
    public void deserializeStringListTest()
    {
        final String jsonValue = "[\"value1\",\"value2\",\"value3\"]";
        final List<String> list = instance.deserializeStringList( jsonValue );

        Assert.assertNotNull( list );
        Assert.assertEquals( 3, list.size() );
        Assert.assertEquals( "value1", list.get( 0 ) );
        Assert.assertEquals( "value2", list.get( 1 ) );
        Assert.assertEquals( "value3", list.get( 2 ) );

        // verify returned collection is immutable
        Assert.assertThrows( UnsupportedOperationException.class, () -> list.add( "new value" ) );
    }

    @Test
    public void deserializeStringMapTest()
    {
        final String jsonValue = "{\"key1\":\"value1\",\"key2\":\"value2\",\"key3\":\"value3\"}";
        final Map<String, String> map = instance.deserializeStringMap( jsonValue );

        Assert.assertNotNull( map );
        Assert.assertEquals( 3, map.size() );
        Assert.assertEquals( "value1", map.get( "key1" ) );
        Assert.assertEquals( "value2", map.get( "key2" ) );
        Assert.assertEquals( "value3", map.get( "key3" ) );

        // verify returned collection is immutable
        Assert.assertThrows( UnsupportedOperationException.class, () -> map.put( "new key", "new value" ) );
    }

    @Test
    public void deserializeMapTest()
    {
        final String jsonValue = "{\"key1\":\"value1\",\"key2\":\"value2\",\"key3\":\"value3\"}";
        final Map<String, Object> map = instance.deserializeMap( jsonValue, String.class, Object.class );

        Assert.assertNotNull( map );
        Assert.assertEquals( 3, map.size() );
        Assert.assertEquals( "value1", map.get( "key1" ) );
        Assert.assertEquals( "value2", map.get( "key2" ) );
        Assert.assertEquals( "value3", map.get( "key3" ) );

        // verify returned collection is immutable
        Assert.assertThrows( UnsupportedOperationException.class, () -> map.put( "new key", "new value" ) );
    }

    @Test
    public void deserializeObjectTest()
    {
        final String jsonValue = TestObject1.makeExpectedJsonValue( instance );
        final TestObject1 testObject1 = instance.deserialize( jsonValue, TestObject1.class );

        Assert.assertNotNull( testObject1 );
        Assert.assertEquals( TestObject1.VALUE_STRING1, testObject1.getString1() );
        Assert.assertEquals( TestObject1.VALUE_INSTANT1.toString(), testObject1.getInstant1().toString() );
        Assert.assertEquals( TestObject1.VALUE_DATE1.toInstant().toString(), testObject1.getDate1().toInstant().toString() );
        Assert.assertEquals( TestObject1.VALUE_LONG_ADDER1.longValue(), testObject1.getLongAdder1().longValue() );
        Assert.assertEquals( TestObject1.VALUE_DOMAINID1, testObject1.getDomainId1() );
        Assert.assertEquals( TestObject1.VALUE_PASSWORD_DATA1, testObject1.getPasswordData1() );
        Assert.assertEquals( TestObject1.VALUE_X509_CERT1, testObject1.getCertificate1() );

    }

    @Test
    public void miscSerializationTests()
    {
        {
            final String json = "[{\"type\":\"ldapQuery\",\"ldapProfileID\":\"all\",\"ldapQuery\":\"(cn=asmith)\"}]";
            final List<UserPermission> userPermission = instance.deserializeList( json, UserPermission.class );

            Assert.assertEquals( UserPermissionType.ldapQuery, userPermission.get( 0 ).getType() );
        }

        {
            final Map<String, Integer> map = Map.of(
                    "J", 1,
                    "JJ", 2,
                    "JJJ", 3
            );

            final String json = instance.serializeMap( new TreeMap<>( map ), String.class, Integer.class );
            Assert.assertEquals( "{\"J\":1,\"JJ\":2,\"JJJ\":3}", json );
        }
    }

    @Test
    public void serializeNestedListMap()
    {
        {
            final List<Map<String, Object>> srcObject = new ArrayList<>();
            srcObject.add( Map.of( "key1", "value1" ) );
            final String jsonOutput = instance.serializeCollection( srcObject );
            Assert.assertEquals( "[{\"key1\":\"value1\"}]", jsonOutput );
        }
    }

    @Test
    public void deserializeNestedListMap()
    {
        {

            final String srcJson = "[{\"key1\":\"value1\"}]";
            //final List tempObj = instance.deserializeList( srcJson, List.class );

        }

    }

    @Test
    public void deserializeCollectionTest()
    {
        final String json = "[\"ListItem1\",\"ListItem2\",{\"key1\":\"value1\",\"key2\":\"value2\"}]";
        final List<Object> list = instance.deserializeList( json, Object.class );

        Assert.assertEquals( "ListItem1", list.get( 0 ) );
        Assert.assertEquals( "ListItem2", list.get( 1 ) );
        Assert.assertEquals( Map.of( "key1", "value1", "key2", "value2" ), list.get( 2 ) );
    }

    @Test
    public void serializeNavTreeTest()
            throws Exception
    {
        final List<NavTreeItem> navTreeItems = NavTreeDataMaker.makeNavTreeItems(
                DomainID.DOMAIN_ID_DEFAULT,
                StoredConfigurationFactory.newConfig(),
                NavTreeSettings.builder().build() );

        instance.serializeCollection( navTreeItems );
    }

    @Test
    public void serializeRestResultBeanTest()
    {
        {
            final String expectedJson = "{\"data\":{\"key1\":1,\"key2\":\"value2\"},\"error\":false,\"errorCode\":0}";
            final Map<String, Object> data = new HashMap<>();
            data.put( "key1", 1 );
            data.put( "key2", "value2" );
            final RestResultBean<Map> restResultBean = RestResultBean.withData( data, Map.class );
            final String json = restResultBean.toJson( false );
            Assert.assertEquals( expectedJson, json );
        }

    }

    @Test
    public void deserializeMap()
    {
        final String json = "{\"key1\":1,\"key2\":\"String2\",\"key3\":[\"ListValue1\",\"ListValue2\"]}";
        final Map<String, Object> map = instance.deserializeMap( json,
                String.class,
                Object.class );
        Assert.assertEquals( 1.0, map.get( "key1" ) );
        Assert.assertEquals( "String2", map.get( "key2" ) );
        Assert.assertEquals( List.of( "ListValue1", "ListValue2" ), map.get( "key3" ) );
    }

    @Test
    public void serializeObjectTest()
    {
        final TestObject1 testObject1 = TestObject1.newTestObject();
        final String jsonValue = instance.serialize( testObject1 );
        Assert.assertEquals( TestObject1.makeExpectedJsonValue( instance ), jsonValue );
    }

    @Test
    public void serializeTypeTest()
    {
        final List<ActionConfiguration> srcList = new ArrayList<>();
        {
            final List<ActionConfiguration.WebAction> webActions = List.of( ActionConfiguration.WebAction.builder()
                    .password( "password" )
                    .method( ActionConfiguration.WebMethod.get )
                    .url( "https://www.example.com" )
                    .body( "body" )
                    .build() );
            final ActionConfiguration actionConfiguration = ActionConfiguration.builder()
                    .name( "action1" )
                    .description( "actionDescription" )
                    .webActions( webActions )
                    .build();
            srcList.add( actionConfiguration );
        }
        final String json = instance.serializeCollection( srcList );

        final List<ActionConfiguration> deserializedList = instance.deserializeList( json, ActionConfiguration.class );

        Assert.assertEquals( srcList, deserializedList );
    }

    public static class TestObject1 implements Serializable
    {
        static final BigDecimal VALUE_BIG_DECIMAL1 = new BigDecimal( "3.0404040400404040404040404040404" );
        static final X509Certificate VALUE_X509_CERT1;
        static final Date VALUE_DATE1 = Date.from( Instant.parse( "2000-01-01T01:01:01Z" ) );
        static final DomainID VALUE_DOMAINID1 = DomainID.create( "acme1" );
        static final Instant VALUE_INSTANT1 = Instant.parse( "2000-01-01T01:01:01Z" );
        static final PwmLdapVendor VALUE_LDAP_VENDOR1 = PwmLdapVendor.DIRECTORY_SERVER_389;
        static final LongAdder VALUE_LONG_ADDER1;
        static final PasswordData VALUE_PASSWORD_DATA1;
        static final String VALUE_STRING1 = "stringValue1";
        static final PwmLogEvent VALUE_LOG_EVENT1;
        static final Locale VALUE_LOCALE1 = new Locale( "jp" );

        private static final String DATA_CERT1 = "MIIC1TCCAb2gAwIBAgIJAMIrQtIBUHNJMA0GCSqGSIb3DQEBBQUAMBoxGDAWBgNV"
                + "BAMTD3d3dy5leGFtcGxlLmNvbTAeFw0yMTA5MDUyMTQ2NDlaFw0zMTA5MDMyMTQ2"
                + "NDlaMBoxGDAWBgNVBAMTD3d3dy5leGFtcGxlLmNvbTCCASIwDQYJKoZIhvcNAQEB"
                + "BQADggEPADCCAQoCggEBANaDkcpssTnKQ0BDLbMjIhU+b1vHBKiwHgBAdLkKEx0N"
                + "e/5obMMy4TIecnvO/8y9eo7HEgi1Q9FB9PT+M/+YhfQ4glp8IgQBa8eL3e3MqklW"
                + "1upWVotn4cXlpgDXIBCflR9v27r3svK5FXUc5Ge352aYbsLDJsdBiwWMHFrMjO3x"
                + "V8OhT3vkuhgwcdCQtiVN+6GgB3Krkq/qOQtqdaRisVlqKyePhHSDyrHY1ZeQYIgR"
                + "jYuhh+Pbrr/QMnKOIxNLOkainE68h+0R3LCYR+rb8Ex3CgsxdIBdmNBixh2k9EIm"
                + "smA81D+at13bny8o7Jieeu2uY6dnGquD3YE4AfyiP0cCAwEAAaMeMBwwGgYDVR0R"
                + "BBMwEYIPd3d3LmV4YW1wbGUuY29tMA0GCSqGSIb3DQEBBQUAA4IBAQCmQw8I3N0p"
                + "KhdaEdLn9jK+Md+PyJpca8WYdbNYzlis0Nxsp+V97+Rt5SHyxjS2mTY9tMUGAwiF"
                + "3HNcTmPK2+yPx6TqELmJ4NfRG1bdZJ6OvFRZFVT5BeKWetUHZ8cf7J2+o5yU0V4o"
                + "iKW2/l8jQIUVQDYlTwwfNUufWI9B4bSGf7gliFWnTaDtZ+JxP9oNOaWBqVeRcLFu"
                + "QDcGP+kQTE0+FW4kP9/oTIjD2u2Jc4d0NcPa2hUDWyPS1OqcSPJYGngBmDo524Mv"
                + "ye7akpMj/ywK4BEnZpl/1rO5pNMD7GIK8lST4OOycWs3vErybogF45JCp7enroTH"
                + "UWSGBXG89MJR";


        static
        {
            {
                final LongAdder longAdder = new LongAdder();
                longAdder.add( 9223372036854775807L );
                VALUE_LONG_ADDER1 = longAdder;
            }

            try
            {
                VALUE_PASSWORD_DATA1 = PasswordData.forStringValue( "super-secret-password" );
            }
            catch ( final PwmUnrecoverableException e )
            {
                throw new RuntimeException( e );
            }

            try
            {
                VALUE_X509_CERT1 = X509Utils.certificateFromBase64( DATA_CERT1 );
            }
            catch ( final CertificateException | IOException e )
            {
                throw new RuntimeException( e );
            }

            {
                final Throwable throwable = new RuntimeException( "test runtime exception" );

                VALUE_LOG_EVENT1 = PwmLogEvent.createPwmLogEvent(
                        Instant.parse( "2000-01-01T01:01:01Z" ),
                        "topic",
                        "message",
                        SessionLabel.TEST_SESSION_LABEL,
                        throwable,
                        PwmLogLevel.TRACE
                );
            }
        }

        static String makeExpectedJsonValue( final JsonProvider jsonProvider )
        {
            return "{"
                    + "\"certificate1\":\"" + DATA_CERT1 + "\"" + ","
                    + "\"date1\":\"" + VALUE_DATE1.toInstant().toString() + "\"" + ","
                    + "\"domainId1\":\"acme1\"" + ","
                    + "\"instant1\":\"" + VALUE_INSTANT1.toString() + "\"" + ","
                    + "\"ldapVendor1\":\"" + VALUE_LDAP_VENDOR1 + "\"" + ","
                    + "\"locale1\":\"" + VALUE_LOCALE1 + "\"" + ","
                    + "\"logEvent1\":" + jsonProvider.serialize( VALUE_LOG_EVENT1, PwmLogEvent.class ) + ","
                    + "\"longAdder1\":9223372036854775807" + ","
                    + "\"passwordData1\":\"super-secret-password\"" + ","
                    + "\"string1\":\"" + VALUE_STRING1 + "\""
                    + "}";
        }


        static TestObject1 newTestObject()
        {
            return new TestObject1(
                    VALUE_X509_CERT1,
                    VALUE_DATE1,
                    VALUE_DOMAINID1,
                    VALUE_INSTANT1,
                    VALUE_LDAP_VENDOR1,
                    VALUE_LOCALE1,
                    VALUE_LONG_ADDER1,
                    VALUE_PASSWORD_DATA1,
                    VALUE_STRING1,
                    VALUE_LOG_EVENT1 );
        }

        private final X509Certificate certificate1;
        private final Date date1;
        private final DomainID domainId1;
        private final Instant instant1;
        private final PwmLdapVendor ldapVendor1;
        private final Locale locale1;
        private final PwmLogEvent logEvent1;
        private final LongAdder longAdder1;
        private final PasswordData passwordData1;
        private final String string1;

        @SuppressWarnings( "checkstyle:ParameterNumber" )
        public TestObject1(
                final X509Certificate certificate1,
                final Date date1,
                final DomainID domainId1,
                final Instant instant1,
                final PwmLdapVendor ldapVendor1,
                final Locale locale1,
                final LongAdder longAdder1,
                final PasswordData passwordData1,
                final String string1,
                final PwmLogEvent logEvent1
        )
        {
            this.certificate1 = certificate1;
            this.date1 = date1;
            this.domainId1 = domainId1;
            this.instant1 = instant1;
            this.ldapVendor1 = ldapVendor1;
            this.locale1 = locale1;
            this.longAdder1 = longAdder1;
            this.passwordData1 = passwordData1;
            this.string1 = string1;
            this.logEvent1 = logEvent1;
        }

        public X509Certificate getCertificate1()
        {
            return certificate1;
        }

        public Date getDate1()
        {
            return date1;
        }

        public DomainID getDomainId1()
        {
            return domainId1;
        }

        public Instant getInstant1()
        {
            return instant1;
        }

        public LongAdder getLongAdder1()
        {
            return longAdder1;
        }

        public PasswordData getPasswordData1()
        {
            return passwordData1;
        }

        public String getString1()
        {
            return string1;
        }

        public PwmLdapVendor getLdapVendor1()
        {
            return ldapVendor1;
        }

        public PwmLogEvent getLogEvent1()
        {
            return logEvent1;
        }

        public Locale getLocale1()
        {
            return locale1;
        }
    }

}

