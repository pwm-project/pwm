/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2017 The PWM Project
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

package password.pwm.config.profile;

import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import password.pwm.config.profile.PwmPasswordPolicy.RuleHelper;
import password.pwm.util.macro.MacroMachine;

import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static password.pwm.config.profile.PwmPasswordRule.RegExMatch;

public class RuleHelperTest {
    private static final String[][] MACRO_MAP = new String[][] {
        { "@User:ID@",        "fflintstone"         },
        { "@User:Email@",     "fred@flintstones.tv" },
        { "@LDAP:givenName@", "Fred"                },
        { "@LDAP:sn@",        "Flintstone"          }
    };

    private MacroMachine macroMachine = mock(MacroMachine.class);
    private RuleHelper ruleHelper = mock(RuleHelper.class);

    @Before
    public void setUp() throws Exception {
        // Mock out things that don't need to be real
        when(macroMachine.expandMacros(anyString())).thenAnswer(replaceAllMacrosInMap(MACRO_MAP));
        when(ruleHelper.readBooleanValue(PwmPasswordRule.AllowMacroInRegExSetting)).thenReturn(Boolean.TRUE);
        when(ruleHelper.readRegExSetting(any(PwmPasswordRule.class), any(MacroMachine.class), anyString())).thenCallRealMethod();
    }

    @Test
    public void testReadRegExSetting_noRegex() throws Exception {
        final String input = "@User:ID@, First Name: @LDAP:givenName@, Last Name: @LDAP:sn@, Email: @User:Email@";

        final List<Pattern> patterns = ruleHelper.readRegExSetting(RegExMatch, macroMachine, input);

        assertThat(patterns.size()).isEqualTo(1);
        assertThat(patterns.get(0).pattern()).isEqualTo("fflintstone, First Name: Fred, Last Name: Flintstone, Email: fred@flintstones.tv");
    }

    @Test
    public void testReadRegExSetting() throws Exception {
        final String input = "^@User:ID@[0-9]+$;;;^password$";

        final List<Pattern> patterns = ruleHelper.readRegExSetting(RegExMatch, macroMachine, input);

        assertThat(patterns.size()).isEqualTo(2);
        assertThat(patterns.get(0).pattern()).isEqualTo("^fflintstone[0-9]+$");
        assertThat(patterns.get(1).pattern()).isEqualTo("^password$");
    }

    private Answer<String> replaceAllMacrosInMap(final String[][] macroMap) {
        return new Answer<String>() {
            @Override
            public String answer(InvocationOnMock invocation) throws Throwable {
                final String[] macroNames = new String[macroMap.length];
                final String[] macroValues = new String[macroMap.length];

                for (int i=0; i<macroMap.length; i++) {
                    macroNames[i] = macroMap[i][0];
                    macroValues[i] = macroMap[i][1];
                }

                final String stringWithMacros = invocation.getArgument(0);
                return StringUtils.replaceEach(stringWithMacros, macroNames, macroValues);
            }
        };
    }
}
