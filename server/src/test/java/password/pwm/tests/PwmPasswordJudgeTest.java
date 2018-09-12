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

package password.pwm.tests;

import junit.framework.Assert;
import junit.framework.TestCase;
import org.mockito.Mockito;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.StrengthMeterType;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.operations.PasswordUtility;

import java.util.ArrayList;
import java.util.List;

public class PwmPasswordJudgeTest extends TestCase {
    public void testJudgePassword() throws Exception {
        final Configuration configuration = Mockito.mock(Configuration.class);
        Mockito.when(configuration.readSettingAsEnum(PwmSetting.PASSWORD_STRENGTH_METER_TYPE, StrengthMeterType.class)).thenReturn(StrengthMeterType.PWM);

        Assert.assertEquals(0, PasswordUtility.judgePasswordStrength(configuration,""));
        Assert.assertEquals(100, PasswordUtility.judgePasswordStrength(configuration,
                "V.{a$f.*B697e+%J9pOPn~E0CyqN~9XmR?yjOGFC(k+la?n6&^I3bwZq[miF(`0"));

        final List<Integer> judgeValues = new ArrayList<>();
        judgeValues.add(PasswordUtility.judgePasswordStrength(configuration,""));
        judgeValues.add(PasswordUtility.judgePasswordStrength(configuration,"3"));
        judgeValues.add(PasswordUtility.judgePasswordStrength(configuration,"3sadasd"));
        judgeValues.add(PasswordUtility.judgePasswordStrength(configuration,"3sadasdA"));
        judgeValues.add(PasswordUtility.judgePasswordStrength(configuration,"3sadasdAASDSADSAD"));
        judgeValues.add(PasswordUtility.judgePasswordStrength(configuration,"3sadasdAASDSADSAD#"));
        judgeValues.add(PasswordUtility.judgePasswordStrength(configuration,"3sadasdAASDSADSAD##@!#!^%&^$*"));
        judgeValues.add(PasswordUtility.judgePasswordStrength(configuration,"3sadasdAASDSADSAD##@!#!^%&^$*aa"));
        judgeValues.add(PasswordUtility.judgePasswordStrength(configuration,"3sadasdAASDSADSAD##@!#!^%&^$*aaaaaaaaaaaa"));
        /*
        judgeValues.add(0);
        judgeValues.add(1);
        judgeValues.add(2);
        judgeValues.add(2);
        judgeValues.add(3);
        judgeValues.add(4);
        */

        for (int i = 1; i < judgeValues.size() - 1; i++) {
            int v1, v2;

            v1 = judgeValues.get(i);
            v2 = judgeValues.get(i - 1);
            //assertTrue(v1 >= v2);

            v1 = judgeValues.get(i);
            v2 = judgeValues.get(i + 1);
            //assertTrue(v1 <= v2);
        }
    }
}
