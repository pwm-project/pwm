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

package password.pwm.i18n;

import org.junit.Assert;
import org.junit.Test;
import password.pwm.PwmConstants;
import password.pwm.config.Configuration;
import password.pwm.config.stored.StoredConfigurationFactory;
import password.pwm.util.java.StringUtil;

import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.ResourceBundle;

public class NonLocalizedKeyTest
{
    private static final String[] NON_LOCALIZED_KEYS = {
            "Title_Application",
            "Title_Application_Abbrev",
            "Title_TitleBarAuthenticated",
            "Title_TitleBar",
    };

    @Test
    public void testNonLocalizedKeyTest() throws Exception
    {
        // check default locales have value
        {
            final ResourceBundle resourceBundle = ResourceBundle.getBundle( Display.class.getName(), PwmConstants.DEFAULT_LOCALE );
            for ( final String key : NON_LOCALIZED_KEYS )
            {
                final String value = resourceBundle.getString( key );
                Assert.assertTrue( !StringUtil.isEmpty( value ) );
            }
        }

        // check non-default locales do NOT have value
        {
            final Configuration configuration = new Configuration( StoredConfigurationFactory.newConfig() );
            final List<Locale> locales = configuration.getKnownLocales();
            for ( final Locale locale : locales )
            {
                if ( !PwmConstants.DEFAULT_LOCALE.toLanguageTag().equals( locale.toLanguageTag() ) )
                {
                    final String resourceFileName = Display.class.getName().replace( ".", "/" )
                            + "_" + locale.toLanguageTag().replace( "-", "_" )
                            + ".properties";
                    final InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream( resourceFileName );
                    if ( inputStream != null )
                    {
                        final Properties props = new Properties();
                        props.load( inputStream );
                        for ( final String key : NON_LOCALIZED_KEYS )
                        {
                            final String value = props.getProperty( key );
                            final String msg = "Display bundle for locale '" + locale.toLanguageTag() + "' has key '"
                                    + key + "'.  Only the default locale should have this key";
                            Assert.assertTrue( msg, StringUtil.isEmpty( value ) );
                        }
                    }
                }
            }
        }
    }
}

