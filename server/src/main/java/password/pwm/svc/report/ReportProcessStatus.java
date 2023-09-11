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

package password.pwm.svc.report;

import password.pwm.http.bean.DisplayElement;
import password.pwm.util.java.CollectionUtil;

import java.util.List;

public record ReportProcessStatus(
        List<DisplayElement> presentable,
        boolean reportInProgress
)
{
    private static final ReportProcessStatus IDLE = new ReportProcessStatus(
            List.of( DisplayElement.create( "status", DisplayElement.Type.string, "Status", "Idle" ) ),
            false );

    public ReportProcessStatus( final List<DisplayElement> presentable, final boolean reportInProgress )
    {
        this.presentable = CollectionUtil.stripNulls( presentable );
        this.reportInProgress = reportInProgress;
    }

    public static ReportProcessStatus getIdle()
    {
        return IDLE;
    }
}
