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

package password.pwm.http.servlet.peoplesearch;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import org.apache.commons.csv.CSVPrinter;
import password.pwm.bean.UserIdentity;
import password.pwm.config.profile.PeopleSearchProfile;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpContentType;
import password.pwm.http.HttpHeader;
import password.pwm.http.HttpMethod;
import password.pwm.http.JspUrl;
import password.pwm.http.ProcessStatus;
import password.pwm.http.PwmHttpRequestWrapper;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmRequestFlag;
import password.pwm.http.servlet.ControlledPwmServlet;
import password.pwm.http.servlet.peoplesearch.bean.OrgChartDataBean;
import password.pwm.http.servlet.peoplesearch.bean.PeopleSearchClientConfigBean;
import password.pwm.http.servlet.peoplesearch.bean.SearchResultBean;
import password.pwm.http.servlet.peoplesearch.bean.UserDetailBean;
import password.pwm.ldap.PhotoDataBean;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsManager;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.ws.server.RestResultBean;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

public abstract class PeopleSearchServlet extends ControlledPwmServlet
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( PeopleSearchServlet.class );

    private static final String PARAM_USERKEY = "userKey";
    private static final String PARAM_DEPTH = "depth";

    public enum PeopleSearchActions implements ProcessAction
    {
        search( HttpMethod.POST ),
        detail( HttpMethod.GET ),
        photo( HttpMethod.GET ),
        clientData( HttpMethod.GET ),
        orgChartData( HttpMethod.GET ),
        exportOrgChart ( HttpMethod.GET ),
        mailtoLinks ( HttpMethod.GET ),;

        private final HttpMethod method;

        PeopleSearchActions( final HttpMethod method )
        {
            this.method = method;
        }

        public Collection<HttpMethod> permittedMethods( )
        {
            return Collections.singletonList( method );
        }
    }

    @Override
    public Class<? extends ProcessAction> getProcessActionsClass( )
    {
        return PeopleSearchActions.class;
    }

    @Override
    protected void nextStep( final PwmRequest pwmRequest ) throws PwmUnrecoverableException, IOException, ChaiUnavailableException, ServletException
    {
        if ( pwmRequest.getURL().isPublicUrl() )
        {
            pwmRequest.setFlag( PwmRequestFlag.HIDE_IDLE, true );
            pwmRequest.setFlag( PwmRequestFlag.NO_IDLE_TIMEOUT, true );
        }
        pwmRequest.forwardToJsp( JspUrl.PEOPLE_SEARCH );
    }

    @Override
    public abstract ProcessStatus preProcessCheck( PwmRequest pwmRequest )
            throws PwmUnrecoverableException, IOException, ServletException;

    @ActionHandler( action = "clientData" )
    private ProcessStatus restLoadClientData(
            final PwmRequest pwmRequest

    )
            throws PwmUnrecoverableException, IOException
    {
        final PeopleSearchConfiguration peopleSearchConfiguration = new PeopleSearchConfiguration( pwmRequest.getConfig(), peopleSearchProfile( pwmRequest ) );

        final PeopleSearchClientConfigBean peopleSearchClientConfigBean = PeopleSearchClientConfigBean.fromConfig(
                pwmRequest,
                peopleSearchConfiguration,
                pwmRequest.getUserInfoIfLoggedIn()
        );

        final RestResultBean restResultBean = RestResultBean.withData( peopleSearchClientConfigBean );
        LOGGER.trace( pwmRequest, () -> "returning clientData: " + JsonUtil.serialize( restResultBean ) );
        pwmRequest.outputJsonResult( restResultBean );
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "search" )
    private ProcessStatus restSearchRequest(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException, IOException
    {
        final SearchRequestBean searchRequest = JsonUtil.deserialize( pwmRequest.readRequestBodyAsString(), SearchRequestBean.class );

        final PeopleSearchProfile peopleSearchProfile = peopleSearchProfile( pwmRequest );
        final PeopleSearchDataReader peopleSearchDataReader = new PeopleSearchDataReader( pwmRequest, peopleSearchProfile );

        final SearchResultBean searchResultBean = peopleSearchDataReader.makeSearchResultBean( searchRequest );
        final RestResultBean restResultBean = RestResultBean.withData( searchResultBean );

        addExpiresHeadersToResponse( pwmRequest );
        pwmRequest.outputJsonResult( restResultBean );

        LOGGER.trace( pwmRequest, () -> "returning " + searchResultBean.getSearchResults().size() + " results for search request " + JsonUtil.serialize( searchRequest ) );
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "orgChartData" )
    private ProcessStatus restOrgChartData(
            final PwmRequest pwmRequest
    )
            throws IOException, PwmUnrecoverableException, ServletException
    {
        final PeopleSearchProfile peopleSearchProfile = peopleSearchProfile( pwmRequest );
        final PeopleSearchConfiguration peopleSearchConfiguration = new PeopleSearchConfiguration( pwmRequest.getConfig(), peopleSearchProfile );

        final UserIdentity userIdentity;
        {
            final String userKey = pwmRequest.readParameterAsString( PARAM_USERKEY, PwmHttpRequestWrapper.Flag.BypassValidation );
            if ( StringUtil.isEmpty( userKey ) )
            {
                userIdentity = pwmRequest.getUserInfoIfLoggedIn();
                if ( userIdentity == null )
                {
                    return ProcessStatus.Halt;
                }
            }
            else
            {
                userIdentity = UserIdentity.fromObfuscatedKey( userKey, pwmRequest.getPwmApplication() );
            }
        }

        if ( !peopleSearchConfiguration.isOrgChartEnabled( ) )
        {
            throw new PwmUnrecoverableException( PwmError.ERROR_SERVICE_NOT_AVAILABLE );
        }

        final boolean noChildren = pwmRequest.readParameterAsBoolean( "noChildren" );

        try
        {
            final PeopleSearchDataReader peopleSearchDataReader = new PeopleSearchDataReader( pwmRequest, peopleSearchProfile );
            final OrgChartDataBean orgChartData = peopleSearchDataReader.makeOrgChartData( userIdentity, noChildren );

            addExpiresHeadersToResponse( pwmRequest );
            pwmRequest.outputJsonResult( RestResultBean.withData( orgChartData ) );
            StatisticsManager.incrementStat( pwmRequest, Statistic.PEOPLESEARCH_ORGCHART );
        }
        catch ( final PwmException e )
        {
            LOGGER.error( pwmRequest, () -> "error generating user detail object: " + e.getMessage() );
            pwmRequest.respondWithError( e.getErrorInformation() );
        }

        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "detail" )
    private ProcessStatus restUserDetailRequest(
            final PwmRequest pwmRequest
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, IOException
    {
        final String userKey = pwmRequest.readParameterAsString( PARAM_USERKEY, PwmHttpRequestWrapper.Flag.BypassValidation );
        final UserIdentity userIdentity = readUserIdentityFromKey( pwmRequest, userKey );

        final PeopleSearchProfile peopleSearchProfile = peopleSearchProfile( pwmRequest );
        final PeopleSearchDataReader peopleSearchDataReader = new PeopleSearchDataReader( pwmRequest, peopleSearchProfile );
        final UserDetailBean detailData = peopleSearchDataReader.makeUserDetailRequest( userIdentity );

        addExpiresHeadersToResponse( pwmRequest );
        pwmRequest.outputJsonResult( RestResultBean.withData( detailData ) );
        pwmRequest.getPwmApplication().getStatisticsManager().incrementValue( Statistic.PEOPLESEARCH_DETAILS );

        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "photo" )
    private ProcessStatus processUserPhotoImageRequest( final PwmRequest pwmRequest )
            throws ChaiUnavailableException, PwmUnrecoverableException, IOException, ServletException
    {
        final String userKey = pwmRequest.readParameterAsString( PARAM_USERKEY, PwmHttpRequestWrapper.Flag.BypassValidation );
        if ( userKey.length() < 1 )
        {
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_MISSING_PARAMETER, PARAM_USERKEY + " parameter is missing" );
            LOGGER.error( pwmRequest, errorInformation );
            pwmRequest.respondWithError( errorInformation, false );
            return ProcessStatus.Halt;
        }

        final PeopleSearchProfile peopleSearchProfile = peopleSearchProfile( pwmRequest );
        final PeopleSearchDataReader peopleSearchDataReader = new PeopleSearchDataReader( pwmRequest, peopleSearchProfile );
        final UserIdentity userIdentity = readUserIdentityFromKey( pwmRequest, userKey );

        LOGGER.debug( pwmRequest, () -> "received user photo request to view user " + userIdentity.toString() );

        final Callable<Optional<PhotoDataBean>> callablePhotoReader = () -> peopleSearchDataReader.readPhotoData( userIdentity );

        PhotoDataReader.servletRespondWithPhoto( pwmRequest, callablePhotoReader );
        return ProcessStatus.Halt;
    }

    private void addExpiresHeadersToResponse( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException
    {
        final PeopleSearchConfiguration peopleSearchConfiguration = new PeopleSearchConfiguration( pwmRequest.getConfig(), peopleSearchProfile( pwmRequest ) );
        final TimeDuration maxCacheTime = peopleSearchConfiguration.getMaxCacheTime();
        pwmRequest.getPwmResponse().getHttpServletResponse().setDateHeader( HttpHeader.Expires.getHttpName(), System.currentTimeMillis() + ( maxCacheTime.asMillis() ) );
        pwmRequest.getPwmResponse().setHeader( HttpHeader.CacheControl,  "private, max-age=" + maxCacheTime.as( TimeDuration.Unit.SECONDS ) );
    }

    @ActionHandler( action = "exportOrgChart" )
    private ProcessStatus processExportOrgChartRequest( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException, IOException
    {
        final String userKey = pwmRequest.readParameterAsString( PARAM_USERKEY, PwmHttpRequestWrapper.Flag.BypassValidation );
        final int requestedDepth = pwmRequest.readParameterAsInt( PARAM_DEPTH, 1 );
        final UserIdentity userIdentity = readUserIdentityFromKey( pwmRequest, userKey );
        final PeopleSearchProfile peopleSearchProfile = peopleSearchProfile( pwmRequest );
        final PeopleSearchDataReader peopleSearchDataReader = new PeopleSearchDataReader( pwmRequest, peopleSearchProfile );

        final PeopleSearchConfiguration peopleSearchConfiguration = new PeopleSearchConfiguration( pwmRequest.getConfig(), peopleSearchProfile( pwmRequest ) );
        final PeopleSearchClientConfigBean peopleSearchClientConfigBean = PeopleSearchClientConfigBean.fromConfig( pwmRequest, peopleSearchConfiguration, userIdentity );

        if ( !peopleSearchClientConfigBean.isEnableExport() )
        {
            final String msg = "export service is not enabled";
            throw PwmUnrecoverableException.newException( PwmError.ERROR_SERVICE_NOT_AVAILABLE, msg );
        }

        final int effectiveDepth = Math.max( peopleSearchClientConfigBean.getExportMaxDepth(), requestedDepth );

        pwmRequest.getPwmResponse().getHttpServletResponse().setBufferSize( 0 );
        pwmRequest.getPwmResponse().markAsDownload( HttpContentType.csv, "userData.csv" );

        try ( CSVPrinter csvPrinter = JavaHelper.makeCsvPrinter( pwmRequest.getPwmResponse().getOutputStream() ) )
        {
            peopleSearchDataReader.writeUserOrgChartDetailToCsv( csvPrinter, userIdentity, effectiveDepth );
        }

        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "mailtoLinks" )
    private ProcessStatus processMailtoLinksRequest( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException, IOException
    {
        final String userKey = pwmRequest.readParameterAsString( PARAM_USERKEY, PwmHttpRequestWrapper.Flag.BypassValidation );
        final int requestedDepth = pwmRequest.readParameterAsInt( PARAM_DEPTH, 1 );
        final UserIdentity userIdentity = readUserIdentityFromKey( pwmRequest, userKey );

        final PeopleSearchProfile peopleSearchProfile = peopleSearchProfile( pwmRequest );
        final PeopleSearchDataReader peopleSearchDataReader = new PeopleSearchDataReader( pwmRequest, peopleSearchProfile );
        final PeopleSearchConfiguration peopleSearchConfiguration = new PeopleSearchConfiguration( pwmRequest.getConfig(), peopleSearchProfile );

        if ( !peopleSearchConfiguration.isEnableMailtoLinks() )
        {
            final String msg = "mailto links is not enabled.";
            throw PwmUnrecoverableException.newException( PwmError.ERROR_SERVICE_NOT_AVAILABLE, msg );
        }

        final int effectiveDepth = Math.max( peopleSearchConfiguration.getMailtoLinksMaxDepth(), requestedDepth );
        final List<String> mailtoLinks = peopleSearchDataReader.getMailToLink( userIdentity, effectiveDepth );

        pwmRequest.outputJsonResult( RestResultBean.withData( new ArrayList<>( mailtoLinks ) ) );

        return ProcessStatus.Halt;
    }

    static PeopleSearchProfile peopleSearchProfile( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException
    {
        if ( pwmRequest.getURL().isPublicUrl() )
        {
            final Optional<PeopleSearchProfile> profile = pwmRequest.getConfig().getPublicPeopleSearchProfile();
            if ( !profile.isPresent() )
            {
                throw PwmUnrecoverableException.newException( PwmError.ERROR_NO_PROFILE_ASSIGNED, "public peoplesearch profile not assigned" );
            }
            return profile.get();
        }

        if ( pwmRequest.isAuthenticated() )
        {
            return pwmRequest.getPwmSession().getSessionManager().getPeopleSearchProfile();
        }

        throw PwmUnrecoverableException.newException( PwmError.ERROR_NO_PROFILE_ASSIGNED, "unable to load peoplesearch profile for authenticated user" );
    }


    static UserIdentity readUserIdentityFromKey( final PwmRequest pwmRequest, final String userKey )
            throws PwmUnrecoverableException
    {
        if ( StringUtil.isEmpty( userKey ) )
        {
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_MISSING_PARAMETER, PARAM_USERKEY + " parameter is missing" );
            LOGGER.error( pwmRequest, errorInformation );
            throw new PwmUnrecoverableException( errorInformation );
        }

        final PeopleSearchProfile peopleSearchProfile = peopleSearchProfile( pwmRequest );
        final PeopleSearchDataReader peopleSearchDataReader = new PeopleSearchDataReader( pwmRequest, peopleSearchProfile );
        final UserIdentity userIdentity = UserIdentity.fromKey( userKey, pwmRequest.getPwmApplication() );
        peopleSearchDataReader.checkIfUserIdentityViewable( userIdentity );
        return userIdentity;
    }
}
