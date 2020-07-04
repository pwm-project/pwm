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

package password.pwm.svc.intruder;

import password.pwm.util.java.TimeDuration;

import java.io.Serializable;

public class IntruderSettings implements Serializable
{
    private TimeDuration checkDuration;
    private int checkCount;
    private TimeDuration resetDuration;

    public TimeDuration getCheckDuration( )
    {
        return checkDuration;
    }

    public void setCheckDuration( final TimeDuration checkDuration )
    {
        this.checkDuration = checkDuration;
    }

    public int getCheckCount( )
    {
        return checkCount;
    }

    public void setCheckCount( final int checkCount )
    {
        this.checkCount = checkCount;
    }

    public TimeDuration getResetDuration( )
    {
        return resetDuration;
    }

    public void setResetDuration( final TimeDuration resetDuration )
    {
        this.resetDuration = resetDuration;
    }
}
