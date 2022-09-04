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

package password.pwm.http;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import password.pwm.bean.DomainID;
import password.pwm.config.AppConfig;
import password.pwm.config.PwmSetting;
import password.pwm.config.stored.StoredConfigKey;
import password.pwm.config.stored.StoredConfigurationFactory;
import password.pwm.config.stored.StoredConfigurationModifier;
import password.pwm.config.value.StoredValue;
import password.pwm.config.value.StringArrayValue;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.servlet.PwmServletDefinition;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

public class PwmURLTest
{
    @Test
    public void testSingleDomainLoginUrls() throws PwmUnrecoverableException, URISyntaxException
    {
        final AppConfig appConfig = AppConfig.forStoredConfig( StoredConfigurationFactory.newConfig() );
        final PwmURL pwmURL = PwmURL.create( new URI( "https://wwww.example.com/pwm/private/login" ), "/pwm", appConfig );

        Assertions.assertEquals( PwmServletDefinition.Login, pwmURL.forServletDefinition().get() );
        Assertions.assertEquals( "/private/login", pwmURL.determinePwmServletPath() );
        Assertions.assertTrue( pwmURL.isPrivateUrl() );
        Assertions.assertTrue( pwmURL.matches( PwmServletDefinition.Login ) );

    }

    @Test
    public void testMultiDomainLoginUrls() throws PwmUnrecoverableException, URISyntaxException
    {
        final AppConfig appConfig;
        {
            final StoredConfigurationModifier modifier = StoredConfigurationModifier.newModifier( StoredConfigurationFactory.newConfig() );
            final List<String> domainStrList = List.of( "aaaa", "bbbb", "cccc" );
            final StoredValue storedValue = StringArrayValue.create( domainStrList );
            modifier.writeSetting( StoredConfigKey.forSetting( PwmSetting.DOMAIN_LIST, null, DomainID.systemId() ), storedValue, null );
            appConfig = AppConfig.forStoredConfig( modifier.newStoredConfiguration( ) );
        }
        final PwmURL pwmURL = PwmURL.create( new URI( "https://wwww.example.com/pwm/aaaa/private/login" ), "/pwm", appConfig );

        Assertions.assertEquals( PwmServletDefinition.Login, pwmURL.forServletDefinition().get() );
        Assertions.assertEquals( "/private/login", pwmURL.determinePwmServletPath() );
        Assertions.assertTrue( pwmURL.isPrivateUrl() );
        Assertions.assertTrue( pwmURL.matches( PwmServletDefinition.Login ) );

    }


    @Test
    public void testSingleDomainResourceUrls() throws PwmUnrecoverableException, URISyntaxException
    {
        final AppConfig appConfig = AppConfig.forStoredConfig( StoredConfigurationFactory.newConfig() );
        final PwmURL pwmURL = PwmURL.create( new URI( "http://127.0.0.1:8080/pwm/public/resources/nonce-0/webjars/dojo/dojo.js" ), "/pwm", appConfig );

        Assertions.assertTrue( pwmURL.isPublicUrl() );
        Assertions.assertTrue( pwmURL.isResourceURL() );
    }

    @Test
    public void testMultiDomainResourceUrls() throws PwmUnrecoverableException, URISyntaxException
    {
        final AppConfig appConfig;
        {
            final StoredConfigurationModifier modifier = StoredConfigurationModifier.newModifier( StoredConfigurationFactory.newConfig() );
            final List<String> domainStrList = List.of( "aaaa", "bbbb", "cccc" );
            final StoredValue storedValue = StringArrayValue.create( domainStrList );
            modifier.writeSetting( StoredConfigKey.forSetting( PwmSetting.DOMAIN_LIST, null, DomainID.systemId() ), storedValue, null );
            appConfig = AppConfig.forStoredConfig( modifier.newStoredConfiguration( ) );
        }
        final PwmURL pwmURL = PwmURL.create( new URI( "http://127.0.0.1:8080/pwm/aaaa/public/resources/nonce-0/webjars/dojo/dojo.js" ), "/pwm", appConfig );

        Assertions.assertTrue( pwmURL.isPublicUrl() );
        Assertions.assertTrue( pwmURL.isResourceURL() );

    }

}
