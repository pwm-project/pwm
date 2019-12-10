/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2019 The PWM Project
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

package password.pwm.http.filter;

import password.pwm.AppProperty;
import password.pwm.Permission;
import password.pwm.PwmApplicationMode;
import password.pwm.config.stored.ConfigurationReader;
import password.pwm.config.stored.StoredConfiguration;
import password.pwm.config.stored.StoredConfigurationUtil;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.ContextManager;
import password.pwm.http.ProcessStatus;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmURL;
import password.pwm.http.bean.ConfigManagerBean;
import password.pwm.http.servlet.PwmServletDefinition;
import password.pwm.svc.sessiontrack.UserAgentUtils;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.ServletException;
import java.io.IOException;

public class ConfigAccessFilter extends AbstractPwmFilter
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( ConfigAccessFilter.class );

    @Override
    void processFilter( final PwmApplicationMode mode, final PwmRequest pwmRequest, final PwmFilterChain filterChain )
            throws PwmException, IOException, ServletException
    {
        final PwmApplicationMode appMode = pwmRequest.getPwmApplication().getApplicationMode();
        if ( appMode == PwmApplicationMode.NEW )
        {
            filterChain.doFilter();
            return;
        }

        final boolean blockOldIE = Boolean.parseBoolean( pwmRequest.getPwmApplication().getConfig().readAppProperty( AppProperty.CONFIG_EDITOR_BLOCK_OLD_IE ) );
        if ( blockOldIE )
        {
            try
            {
                UserAgentUtils.checkIfPreIE11( pwmRequest );
            }
            catch ( final PwmException e )
            {
                pwmRequest.respondWithError( e.getErrorInformation() );
                return;
            }
        }

        try
        {
            final ConfigManagerBean configManagerBean = pwmRequest.getPwmApplication().getSessionStateService().getBean( pwmRequest, ConfigManagerBean.class );
            if ( checkAuthentication( pwmRequest, configManagerBean ) == ProcessStatus.Continue )
            {
                filterChain.doFilter();
            }
        }
        catch ( final PwmUnrecoverableException e )
        {
            pwmRequest.respondWithError( e.getErrorInformation() );
        }
    }

    @Override
    boolean isInterested( final PwmApplicationMode mode, final PwmURL pwmURL )
    {
        return true;
    }

    public static ProcessStatus checkAuthentication(
            final PwmRequest pwmRequest,
            final ConfigManagerBean configManagerBean
    )
            throws IOException, PwmUnrecoverableException, ServletException
    {
        final ConfigurationReader runningConfigReader = ContextManager.getContextManager( pwmRequest.getHttpServletRequest().getSession() ).getConfigReader();
        final StoredConfiguration storedConfig = runningConfigReader.getStoredConfiguration();

        checkPreconditions( pwmRequest, storedConfig );

        if ( configManagerBean.isPasswordVerified() )
        {
            return ProcessStatus.Continue;
        }

        if ( !pwmRequest.getURL().isPwmServletURL( PwmServletDefinition.ConfigManager_Login ) )
        {
            configManagerBean.setPrePasswordEntryUrl( pwmRequest.getHttpServletRequest().getRequestURL().toString() );
            pwmRequest.sendRedirect( PwmServletDefinition.ConfigManager_Login );
            return ProcessStatus.Halt;
        }
        return ProcessStatus.Continue;
    }

    private static void checkPreconditions(
            final PwmRequest pwmRequest,
            final StoredConfiguration storedConfig
    )
            throws PwmUnrecoverableException
    {

        if ( !StoredConfigurationUtil.hasPassword( storedConfig ) )
        {
            final String errorMsg = "config file does not have a configuration password";
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.CONFIG_FORMAT_ERROR, errorMsg, new String[]
                    {
                            errorMsg,
                    }
            );
            throw new PwmUnrecoverableException( errorInformation );
        }

        if ( PwmApplicationMode.RUNNING == pwmRequest.getPwmApplication().getApplicationMode() )
        {
            if ( !pwmRequest.isAuthenticated() )
            {
                throw new PwmUnrecoverableException( PwmError.ERROR_AUTHENTICATION_REQUIRED );
            }

            if ( !pwmRequest.getPwmSession().getSessionManager().checkPermission( pwmRequest.getPwmApplication(), Permission.PWMADMIN ) )
            {
                throw new PwmUnrecoverableException( PwmError.ERROR_UNAUTHORIZED );
            }
        }
    }
}
