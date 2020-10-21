/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2020 The PWM Project
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

package password.pwm.tests;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.StrengthMeterType;
import password.pwm.util.password.PasswordUtility;

import java.util.ArrayList;
import java.util.List;

public class PwmPasswordJudgeTest
{
    @Test
    public void testJudgePassword() throws Exception
    {
        final Configuration configuration = Mockito.mock( Configuration.class );
        Mockito.when( configuration.readSettingAsEnum( PwmSetting.PASSWORD_STRENGTH_METER_TYPE, StrengthMeterType.class ) ).thenReturn( StrengthMeterType.PWM );

        Assert.assertEquals( 0, PasswordUtility.judgePasswordStrength( configuration, "" ) );
        Assert.assertEquals( 100, PasswordUtility.judgePasswordStrength( configuration,
                "V.{a$f.*B697e+%J9pOPn~E0CyqN~9XmR?yjOGFC(k+la?n6&^I3bwZq[miF(`0" ) );

        final List<Integer> judgeValues = new ArrayList<>();
        judgeValues.add( PasswordUtility.judgePasswordStrength( configuration, "" ) );
        judgeValues.add( PasswordUtility.judgePasswordStrength( configuration, "3" ) );
        judgeValues.add( PasswordUtility.judgePasswordStrength( configuration, "3sadasd" ) );
        judgeValues.add( PasswordUtility.judgePasswordStrength( configuration, "3sadasdA" ) );
        judgeValues.add( PasswordUtility.judgePasswordStrength( configuration, "3sadasdAASDSADSAD" ) );
        judgeValues.add( PasswordUtility.judgePasswordStrength( configuration, "3sadasdAASDSADSAD#" ) );
        judgeValues.add( PasswordUtility.judgePasswordStrength( configuration, "3sadasdAASDSADSAD##@!#!^%&^$*" ) );
        judgeValues.add( PasswordUtility.judgePasswordStrength( configuration, "3sadasdAASDSADSAD##@!#!^%&^$*aa" ) );
        judgeValues.add( PasswordUtility.judgePasswordStrength( configuration, "3sadasdAASDSADSAD##@!#!^%&^$*aaaaaaaaaaaa" ) );
        /*
        judgeValues.add(0);
        judgeValues.add(1);
        judgeValues.add(2);
        judgeValues.add(2);
        judgeValues.add(3);
        judgeValues.add(4);
        */

        for ( int i = 1; i < judgeValues.size() - 1; i++ )
        {
            int v1;
            int v2;

            v1 = judgeValues.get( i );
            v2 = judgeValues.get( i - 1 );
            //assertTrue(v1 >= v2);

            v1 = judgeValues.get( i );
            v2 = judgeValues.get( i + 1 );
            //assertTrue(v1 <= v2);
        }
    }
}
