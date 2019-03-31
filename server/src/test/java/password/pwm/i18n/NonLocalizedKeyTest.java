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

package password.pwm.i18n;

import org.junit.Assert;
import org.junit.Test;
import password.pwm.PwmConstants;
import password.pwm.config.Configuration;
import password.pwm.config.stored.StoredConfigurationImpl;
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
            final Configuration configuration = new Configuration( StoredConfigurationImpl.newStoredConfiguration() );
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

