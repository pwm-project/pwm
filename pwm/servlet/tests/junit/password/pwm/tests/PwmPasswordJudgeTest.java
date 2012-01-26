/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2012 The PWM Project
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
import password.pwm.PwmPasswordJudge;

import java.util.ArrayList;
import java.util.List;

public class PwmPasswordJudgeTest extends TestCase {
    public void testJudgePassword() throws Exception {
        final PwmPasswordJudge judge = new PwmPasswordJudge();
        Assert.assertEquals(0, judge.judgePassword(null,""));
        Assert.assertEquals(100, judge.judgePassword(null, "V.{a$f.*B697e+%J9pOPn~E0CyqN~9XmR?yjOGFC(k+la?n6&^I3bwZq[miF(`0"));

        final List<Integer> judgeValues = new ArrayList<Integer>();
        judgeValues.add(judge.judgePassword(null, ""));
        judgeValues.add(judge.judgePassword(null, "3"));
        judgeValues.add(judge.judgePassword(null, "3sadasd"));
        judgeValues.add(judge.judgePassword(null, "3sadasdA"));
        judgeValues.add(judge.judgePassword(null, "3sadasdAASDSADSAD"));
        judgeValues.add(judge.judgePassword(null, "3sadasdAASDSADSAD#"));
        judgeValues.add(judge.judgePassword(null, "3sadasdAASDSADSAD##@!#!^%&^$*"));
        judgeValues.add(judge.judgePassword(null, "3sadasdAASDSADSAD##@!#!^%&^$*aa"));
        judgeValues.add(judge.judgePassword(null, "3sadasdAASDSADSAD##@!#!^%&^$*aaaaaaaaaaaa"));
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
            Assert.assertTrue(v1 >= v2);

            v1 = judgeValues.get(i);
            v2 = judgeValues.get(i + 1);
            Assert.assertTrue(v1 <= v2);
        }
    }
}
