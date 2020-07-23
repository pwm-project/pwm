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

package password.pwm.config.profile;

import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import password.pwm.util.macro.MacroMachine;
import password.pwm.util.password.PasswordRuleReaderHelper;

import java.util.List;
import java.util.regex.Pattern;

public class PasswordRuleReaderHelperTest
{
    private static final String[][] MACRO_MAP = new String[][] {
            {"@User:ID@", "fflintstone"},
            {"@User:Email@", "fred@flintstones.tv"},
            {"@LDAP:givenName@", "Fred"},
            {"@LDAP:sn@", "Flintstone"},
    };

    private MacroMachine macroMachine = Mockito.mock( MacroMachine.class );
    private PasswordRuleReaderHelper ruleHelper = Mockito.mock( PasswordRuleReaderHelper.class );

    @Before
    public void setUp() throws Exception
    {
        // Mock out things that don't need to be real
        Mockito.when( macroMachine.expandMacros( ArgumentMatchers.anyString() ) ).thenAnswer( replaceAllMacrosInMap( MACRO_MAP ) );
        Mockito.when( ruleHelper.readBooleanValue( PwmPasswordRule.AllowMacroInRegExSetting ) ).thenReturn( Boolean.TRUE );
        Mockito.when( ruleHelper.readRegExSetting(
                ArgumentMatchers.any( PwmPasswordRule.class ),
                ArgumentMatchers.any( MacroMachine.class ),
                ArgumentMatchers.anyString() ) ).thenCallRealMethod();
    }

    @Test
    public void testReadRegExSettingNoRegex() throws Exception
    {
        final String input = "@User:ID@, First Name: @LDAP:givenName@, Last Name: @LDAP:sn@, Email: @User:Email@";

        final List<Pattern> patterns = ruleHelper.readRegExSetting( PwmPasswordRule.RegExMatch, macroMachine, input );

        Assert.assertEquals( patterns.size(), 1 );
        Assert.assertEquals( patterns.get( 0 ).pattern(), "fflintstone, First Name: Fred, Last Name: Flintstone, Email: fred@flintstones.tv" );
    }

    @Test
    public void testReadRegExSetting() throws Exception
    {
        final String input = "^@User:ID@[0-9]+$;;;^password$";

        final List<Pattern> patterns = ruleHelper.readRegExSetting( PwmPasswordRule.RegExMatch, macroMachine, input );

        Assert.assertEquals( patterns.size(), 2 );
        Assert.assertEquals( patterns.get( 0 ).pattern(), "^fflintstone[0-9]+$" );
        Assert.assertEquals( patterns.get( 1 ).pattern(), "^password$" );
    }

    private Answer<String> replaceAllMacrosInMap( final String[][] macroMap )
    {
        return new Answer<String>()
        {
            @Override
            public String answer( final InvocationOnMock invocation ) throws Throwable
            {
                final String[] macroNames = new String[macroMap.length];
                final String[] macroValues = new String[macroMap.length];

                for ( int i = 0; i < macroMap.length; i++ )
                {
                    macroNames[i] = macroMap[i][0];
                    macroValues[i] = macroMap[i][1];
                }

                final String stringWithMacros = invocation.getArgument( 0 );
                return StringUtils.replaceEach( stringWithMacros, macroNames, macroValues );
            }
        };
    }
}
