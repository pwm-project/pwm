/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2014 The PWM Project
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

package password.pwm.util.macro;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class AbstractMacro implements MacroImplementation {

    static final String PATTERN_OPTIONAL_PARAMETER_MATCH = "(:(?:/@|[^@])*)*"; // match param values inside @ and include @ if preceded by /
    static final String PATTERN_PARAMETER_SPLIT = "(?<![/]):"; //split on : except if preceded by /

    public AbstractMacro() {
    }

    @Override
    public boolean isSensitive() {
        return false;
    }
    static String stripMacroDelimiters(final String input) {
        return input.replaceAll("^@|@$",""); // strip leading / trailing @
    }

    static String unescapeParamValue(final String input) {
        String result = input;
        result = result.replace("/:", ":");
        result = result.replace("/@","@");
        return result;
    }

    static List<String> splitMacroParameters(final String input, String... ignoreValues) {
        final String strippedInput = stripMacroDelimiters(input);
        final String[] splitInput = strippedInput.split(PATTERN_PARAMETER_SPLIT);
        final List<String> returnObj = new ArrayList<>();
        final List<String> ignoreValueList = Arrays.asList(ignoreValues);
        for (final String value : splitInput) {
            if (!ignoreValueList.contains(value)) {
                returnObj.add(unescapeParamValue(value));
                ignoreValueList.remove(value);
            }
        }
        return returnObj;
    }

}
