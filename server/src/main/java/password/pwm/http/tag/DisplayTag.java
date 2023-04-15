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

package password.pwm.http.tag;

import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.i18n.PwmLocaleBundle;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroRequest;

/**
 * @author Jason D. Rivard
 */
public class DisplayTag extends PwmAbstractTag
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( DisplayTag.class );

    private String key;
    private String value1;
    private String value2;
    private String value3;
    private boolean displayIfMissing;
    private String bundle;

    public String getKey( )
    {
        return key;
    }

    public void setKey( final String key )
    {
        this.key = key;
    }

    public String getValue1( )
    {
        return value1;
    }

    public void setValue1( final String value1 )
    {
        this.value1 = value1;
    }

    public String getValue2( )
    {
        return value2;
    }

    public void setValue2( final String value1 )
    {
        this.value2 = value1;
    }

    public String getValue3( )
    {
        return value3;
    }

    public void setValue3( final String value3 )
    {
        this.value3 = value3;
    }

    public boolean isDisplayIfMissing( )
    {
        return displayIfMissing;
    }

    public void setDisplayIfMissing( final boolean displayIfMissing )
    {
        this.displayIfMissing = displayIfMissing;
    }

    public String getBundle( )
    {
        return bundle;
    }

    public void setBundle( final String bundle )
    {
        this.bundle = bundle;
    }

    @Override
    protected PwmLogger getLogger()
    {
        return LOGGER;
    }

    @Override
    protected String generateTagBodyContents( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException
    {

        final PwmLocaleBundle pwmBundle = PwmLocaleBundle.forKey( bundle ).orElse( PwmLocaleBundle.DISPLAY );

        final String[] valueArray = new String[]
                {
                        value1,
                        value2,
                        value3,
                };

        final String rawMessage = LocaleHelper.getLocalizedMessage(
                pwmRequest.getLocale(),
                key,
                pwmRequest.getDomainConfig(),
                pwmBundle.getTheClass(),
                valueArray );

        final MacroRequest macroRequest = pwmRequest.getMacroMachine( );
        return macroRequest.expandMacros( rawMessage  );
    }
}

