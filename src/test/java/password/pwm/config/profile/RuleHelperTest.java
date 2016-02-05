package password.pwm.config.profile;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;
import static password.pwm.config.profile.PwmPasswordRule.*;

import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import password.pwm.config.profile.PwmPasswordPolicy.RuleHelper;
import password.pwm.util.macro.MacroMachine;

public class RuleHelperTest {
    private static final String[][] MACRO_MAP = new String[][] {
        { "@User:ID@",        "fflintstone"         },
        { "@User:Email@",     "fred@flintstones.tv" },
        { "@LDAP:givenName@", "Fred"                },
        { "@LDAP:sn@",        "Flintstone"          }
    };

    private MacroMachine macroMachine = mock(MacroMachine.class);

    @Test
    public void testReadRegExSetting_sanityCheck() throws Exception {
        when(macroMachine.expandMacros(anyString())).thenAnswer(replaceAllMacrosInMap(MACRO_MAP));

        final String expandedText = macroMachine.expandMacros("@User:ID@, First Name: @LDAP:givenName@, Last Name: @LDAP:sn@, Email: @User:Email@");
        assertThat(expandedText).isEqualTo("fflintstone, First Name: Fred, Last Name: Flintstone, Email: fred@flintstones.tv");
    }

    @Test
    public void testReadRegExSetting_noRegex() throws Exception {
        when(macroMachine.expandMacros(anyString())).thenAnswer(replaceAllMacrosInMap(MACRO_MAP));

        final String expandedText = macroMachine.expandMacros("@User:ID@, First Name: @LDAP:givenName@, Last Name: @LDAP:sn@, Email: @User:Email@");
        assertThat(expandedText).isEqualTo("fflintstone, First Name: Fred, Last Name: Flintstone, Email: fred@flintstones.tv");

        List<Pattern> setting = RuleHelper.readRegExSetting(RegExMatch, macroMachine, "");
        setting = RuleHelper.readRegExSetting(RegExNoMatch, macroMachine, "");
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

                final String stringWithMacros = invocation.getArgumentAt(0, String.class);
                return StringUtils.replaceEach(stringWithMacros, macroNames, macroValues);
            }
        };
    }
}
