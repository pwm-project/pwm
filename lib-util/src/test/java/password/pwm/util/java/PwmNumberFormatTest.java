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

package password.pwm.util.java;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Locale;

class PwmNumberFormatTest
{
    @Test
    void prettyBigDecimalTest()
    {
        final Locale locale = new Locale( "en_US" );

        Assertions.assertEquals(
                "0.333",
                PwmNumberFormat.prettyBigDecimal( new BigDecimal( "0.333" ), 3, locale ) );

        Assertions.assertEquals(
                "123",
                PwmNumberFormat.prettyBigDecimal( new BigDecimal( "123.333" ), 3, locale ) );

        Assertions.assertEquals(
                "123,000,000",
                PwmNumberFormat.prettyBigDecimal( new BigDecimal( "123456789.3333333333333" ), 3, locale ) );

        Assertions.assertEquals(
                "0",
                PwmNumberFormat.prettyBigDecimal( new BigDecimal( "0.000000003" ), 3, locale ) );

        Assertions.assertEquals(
                "0.778",
                PwmNumberFormat.prettyBigDecimal( new BigDecimal( "0.77777777" ), 3, locale ) );
    }
}
