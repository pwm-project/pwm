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

package password.pwm.receiver;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

@WebListener
public class ContextManager implements ServletContextListener
{
    private static final String CONTEXT_ATTR = "contextManager";
    private PwmReceiverApp app;

    @Override
    public void contextInitialized( final ServletContextEvent sce )
    {
        app = new PwmReceiverApp();
        sce.getServletContext().setAttribute( CONTEXT_ATTR, this );
    }

    @Override
    public void contextDestroyed( final ServletContextEvent sce )
    {
        app.close();
        app = null;
    }

    public PwmReceiverApp getApp( )
    {
        return app;
    }

    public static ContextManager getContextManager( final ServletContext serverContext )
    {
        return ( ContextManager ) serverContext.getAttribute( CONTEXT_ATTR );
    }
}
