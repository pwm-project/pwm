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

package password.pwm.receiver;

import password.pwm.bean.pub.PublishVersionBean;
import password.pwm.ws.server.RestResultBean;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;

@WebServlet(
        urlPatterns = {
                "/version",
        }
)
public class PublishVersionServlet extends HttpServlet
{
    @Override
    protected void doGet( final HttpServletRequest req, final HttpServletResponse resp )
            throws IOException
    {

        final ContextManager contextManager = ContextManager.getContextManager( req.getServletContext() );
        final PwmReceiverApp app = contextManager.getApp();

        app.getStatisticCounterBundle().increment( PwmReceiverApp.CounterStatsKey.VersionCheckRequests );
        app.getStatisticEpsBundle().markEvent( PwmReceiverApp.EpsStatKey.VersionCheckRequests );

        final PublishVersionBean publishVersionBean = new PublishVersionBean(
                Collections.singletonMap( PublishVersionBean.VersionKey.current, app.getSettings().getCurrentVersionInfo() ) );

        final RestResultBean<PublishVersionBean> restResultBean = RestResultBean.withData( publishVersionBean, PublishVersionBean.class );

        ReceiverUtil.outputJsonResponse( req, resp, restResultBean );
    }
}
