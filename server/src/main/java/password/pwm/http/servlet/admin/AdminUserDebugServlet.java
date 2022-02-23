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

package password.pwm.http.servlet.admin;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.AppProperty;
import password.pwm.PwmConstants;
import password.pwm.PwmDomain;
import password.pwm.bean.UserIdentity;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpContentType;
import password.pwm.http.HttpMethod;
import password.pwm.http.JspUrl;
import password.pwm.http.ProcessStatus;
import password.pwm.http.PwmHttpRequestWrapper;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmRequestAttribute;
import password.pwm.http.bean.AdminBean;
import password.pwm.http.servlet.ControlledPwmServlet;
import password.pwm.http.servlet.PwmServletDefinition;
import password.pwm.ldap.search.UserSearchEngine;
import password.pwm.util.java.StringUtil;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.json.JsonProvider;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@WebServlet(
        name = "AdminUserDebugServlet",
        urlPatterns = {
                PwmConstants.URL_PREFIX_PRIVATE + "/admin/user-debug",
        }
)
public class AdminUserDebugServlet extends ControlledPwmServlet
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( AdminUserDebugServlet.class );

    public enum AdminUserDebugAction implements ProcessAction
    {
        searchUsername( HttpMethod.POST ),
        downloadUserDebug( HttpMethod.GET ),;

        private final Collection<HttpMethod> method;

        AdminUserDebugAction( final HttpMethod... method )
        {
            this.method = List.of( method );
        }

        @Override
        public Collection<HttpMethod> permittedMethods( )
        {
            return method;
        }
    }

    @Override
    protected PwmServletDefinition getServletDefinition()
    {
        return PwmServletDefinition.AdminUserDebug;
    }

    @Override
    public Optional<Class<? extends ProcessAction>> getProcessActionsClass()
    {
        return Optional.of( AdminUserDebugAction.class );
    }

    @Override
    protected void nextStep( final PwmRequest pwmRequest ) throws PwmUnrecoverableException, IOException, ChaiUnavailableException, ServletException
    {
        pwmRequest.forwardToJsp( JspUrl.ADMIN_DEBUG );
    }

    @Override
    public ProcessStatus preProcessCheck( final PwmRequest pwmRequest ) throws PwmUnrecoverableException, IOException, ServletException
    {
        return SystemAdminServlet.preProcessAdminCheck( pwmRequest );
    }

    @ActionHandler( action = "searchUsername" )
    public ProcessStatus processSearchUsername( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException, ServletException, IOException
    {
        final String username = pwmRequest.readParameterAsString( "username", PwmHttpRequestWrapper.Flag.BypassValidation );
        if ( !StringUtil.isEmpty( username ) )
        {

            final UserSearchEngine userSearchEngine = pwmRequest.getPwmDomain().getUserSearchEngine();
            final UserIdentity userIdentity;
            try
            {
                userIdentity = userSearchEngine.resolveUsername( username, null, null, pwmRequest.getLabel() );
                final AdminBean adminBean = pwmRequest.getPwmDomain().getSessionStateService().getBean( pwmRequest, AdminBean.class );
                adminBean.setLastUserDebug( userIdentity );
                final UserDebugDataBean userDebugData = UserDebugDataReader.readUserDebugData(
                        pwmRequest.getPwmDomain(),
                        pwmRequest.getLocale(),
                        pwmRequest.getLabel(),
                        userIdentity
                );
                pwmRequest.setAttribute( PwmRequestAttribute.UserDebugData, userDebugData );
            }
            catch ( final PwmUnrecoverableException | PwmOperationalException e )
            {
                setLastError( pwmRequest, e.getErrorInformation() );
            }
        }

        return ProcessStatus.Continue;
    }

    @ActionHandler( action = "downloadUserDebug" )
    public ProcessStatus processDownloadUserDebug( final PwmRequest pwmRequest )

            throws PwmUnrecoverableException, IOException, ServletException
    {
        final PwmDomain pwmDomain = pwmRequest.getPwmDomain();
        final AdminBean adminBean = pwmRequest.getPwmDomain().getSessionStateService().getBean( pwmRequest, AdminBean.class );
        final UserIdentity userIdentity = adminBean.getLastUserDebug();
        if ( userIdentity != null )
        {
            pwmRequest.getPwmResponse().markAsDownload(
                    HttpContentType.json,
                    pwmDomain.getConfig().readAppProperty( AppProperty.DOWNLOAD_FILENAME_USER_DEBUG_JSON )
            );
            final UserDebugDataBean userDebugData = UserDebugDataReader.readUserDebugData(
                    pwmRequest.getPwmDomain(),
                    pwmRequest.getLocale(),
                    pwmRequest.getLabel(),
                    userIdentity
            );
            final String output = JsonFactory.get().serialize( userDebugData, UserDebugDataBean.class, JsonProvider.Flag.PrettyPrint );
            pwmRequest.getPwmResponse().getOutputStream().write( output.getBytes( PwmConstants.DEFAULT_CHARSET ) );
        }
        else
        {
            pwmRequest.respondWithError( new ErrorInformation( PwmError.ERROR_INTERNAL, "no previously searched user available for download" ) );
        }

        return ProcessStatus.Halt;
    }
}
