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

package password.pwm.http.servlet.peoplesearch;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import org.apache.commons.csv.CSVPrinter;
import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpContentType;
import password.pwm.http.HttpMethod;
import password.pwm.http.JspUrl;
import password.pwm.http.ProcessStatus;
import password.pwm.http.PwmHttpRequestWrapper;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmRequestFlag;
import password.pwm.http.servlet.ControlledPwmServlet;
import password.pwm.ldap.PhotoDataBean;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsManager;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.logging.PwmLogger;
import password.pwm.ws.server.RestResultBean;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

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
        export ( HttpMethod.GET ),;

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
    public ProcessStatus preProcessCheck( final PwmRequest pwmRequest ) throws PwmUnrecoverableException, IOException, ServletException
    {
        if ( !pwmRequest.getConfig().readSettingAsBoolean( PwmSetting.PEOPLE_SEARCH_ENABLE ) )
        {
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_SERVICE_NOT_AVAILABLE ) );
        }

        return ProcessStatus.Continue;
    }

    @ActionHandler( action = "clientData" )
    private ProcessStatus restLoadClientData(
            final PwmRequest pwmRequest

    )
            throws ChaiUnavailableException, PwmUnrecoverableException, IOException, ServletException
    {
        final PeopleSearchConfiguration peopleSearchConfiguration = PeopleSearchConfiguration.forRequest( pwmRequest );

        final PeopleSearchClientConfigBean peopleSearchClientConfigBean = PeopleSearchClientConfigBean.fromConfig(
                pwmRequest,
                peopleSearchConfiguration,
                pwmRequest.getUserInfoIfLoggedIn()
        );

        final RestResultBean restResultBean = RestResultBean.withData( peopleSearchClientConfigBean );
        LOGGER.trace( pwmRequest, "returning clientData: " + JsonUtil.serialize( restResultBean ) );
        pwmRequest.outputJsonResult( restResultBean );
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "search" )
    private ProcessStatus restSearchRequest(
            final PwmRequest pwmRequest
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, IOException, ServletException
    {
        final Map<String, Object> jsonBodyMap = pwmRequest.readBodyAsJsonMap( PwmHttpRequestWrapper.Flag.BypassValidation );
        final String username = jsonBodyMap.get( "username" ) == null
                ? null
                : jsonBodyMap.get( "username" ).toString();

        final boolean includeDisplayName = pwmRequest.readParameterAsBoolean( "includeDisplayName" );

        final PeopleSearchDataReader peopleSearchDataReader = new PeopleSearchDataReader( pwmRequest );

        final SearchResultBean searchResultBean = peopleSearchDataReader.makeSearchResultBean( username, includeDisplayName );
        final RestResultBean restResultBean = RestResultBean.withData( searchResultBean );

        addExpiresHeadersToResponse( pwmRequest );
        pwmRequest.outputJsonResult( restResultBean );

        LOGGER.trace( pwmRequest, "returning " + searchResultBean.getSearchResults().size() + " results for search request '" + username + "'" );
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "orgChartData" )
    private ProcessStatus restOrgChartData(
            final PwmRequest pwmRequest
    )
            throws IOException, PwmUnrecoverableException, ServletException
    {
        final PeopleSearchConfiguration peopleSearchConfiguration = PeopleSearchConfiguration.forRequest( pwmRequest );

        final UserIdentity userIdentity;
        {
            final String userKey = pwmRequest.readParameterAsString( PARAM_USERKEY, PwmHttpRequestWrapper.Flag.BypassValidation );
            if ( userKey == null || userKey.isEmpty() )
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
            final PeopleSearchDataReader peopleSearchDataReader = new PeopleSearchDataReader( pwmRequest );
            final OrgChartDataBean orgChartData = peopleSearchDataReader.makeOrgChartData( userIdentity, noChildren );

            addExpiresHeadersToResponse( pwmRequest );
            pwmRequest.outputJsonResult( RestResultBean.withData( orgChartData ) );
            StatisticsManager.incrementStat( pwmRequest, Statistic.PEOPLESEARCH_ORGCHART );
        }
        catch ( PwmException e )
        {
            LOGGER.error( pwmRequest, "error generating user detail object: " + e.getMessage() );
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

        final PeopleSearchDataReader peopleSearchDataReader = new PeopleSearchDataReader( pwmRequest );
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


        final PeopleSearchDataReader peopleSearchDataReader = new PeopleSearchDataReader( pwmRequest );
        final UserIdentity userIdentity = readUserIdentityFromKey( pwmRequest, userKey );

        LOGGER.debug( pwmRequest, "received user photo request to view user " + userIdentity.toString() );

        final PhotoDataBean photoData;
        try
        {
            photoData = peopleSearchDataReader.readPhotoDataFromLdap( userIdentity );
        }
        catch ( PwmOperationalException e )
        {
            final ErrorInformation errorInformation = e.getErrorInformation();
            LOGGER.error( pwmRequest, errorInformation );
            pwmRequest.respondWithError( errorInformation, false );
            return ProcessStatus.Halt;
        }

        addExpiresHeadersToResponse( pwmRequest );

        try ( OutputStream outputStream = pwmRequest.getPwmResponse().getOutputStream() )
        {
            final HttpServletResponse resp = pwmRequest.getPwmResponse().getHttpServletResponse();
            resp.setContentType( photoData.getMimeType() );

            if ( photoData.getContents() != null )
            {
                outputStream.write( photoData.getContents() );
            }
        }
        return ProcessStatus.Halt;
    }

    private void addExpiresHeadersToResponse( final PwmRequest pwmRequest )
    {
        final long maxCacheSeconds = pwmRequest.getConfig().readSettingAsLong( PwmSetting.PEOPLE_SEARCH_MAX_CACHE_SECONDS );
        final HttpServletResponse resp = pwmRequest.getPwmResponse().getHttpServletResponse();
        resp.setDateHeader( "Expires", System.currentTimeMillis() + ( maxCacheSeconds * 1000L ) );
        resp.setHeader( "Cache-Control", "private, max-age=" + maxCacheSeconds );
    }

    @ActionHandler( action = "export" )
    private ProcessStatus processExportRequest( final PwmRequest pwmRequest )
            throws ChaiUnavailableException, PwmUnrecoverableException, IOException
    {
        final String userKey = pwmRequest.readParameterAsString( PARAM_USERKEY, PwmHttpRequestWrapper.Flag.BypassValidation );
        final int requestedDepth = pwmRequest.readParameterAsInt( PARAM_DEPTH, 1 );
        final UserIdentity userIdentity = readUserIdentityFromKey( pwmRequest, userKey );
        final PeopleSearchDataReader peopleSearchDataReader = new PeopleSearchDataReader( pwmRequest );

        final PeopleSearchConfiguration peopleSearchConfiguration = PeopleSearchConfiguration.forRequest( pwmRequest );
        final PeopleSearchClientConfigBean peopleSearchClientConfigBean = PeopleSearchClientConfigBean.fromConfig( pwmRequest, peopleSearchConfiguration, userIdentity );

        if ( !peopleSearchClientConfigBean.isEnableExport() )
        {
            final String msg = "export service is not enabled";
            throw PwmUnrecoverableException.newException( PwmError.ERROR_SERVICE_NOT_AVAILABLE, msg );
        }

        final int effectiveDepth = Math.max( peopleSearchClientConfigBean.getExportMaxDepth(), requestedDepth );

        final UserDetailBean detailData = peopleSearchDataReader.makeUserDetailRequest( userIdentity );
        pwmRequest.getPwmResponse().getHttpServletResponse().setBufferSize( 0 );
        pwmRequest.getPwmResponse().markAsDownload( HttpContentType.csv, "userData.csv" );

        try ( CSVPrinter csvPrinter = JavaHelper.makeCsvPrinter( pwmRequest.getPwmResponse().getOutputStream() ) )
        {
            peopleSearchDataReader.writeUserDetailToCsv( peopleSearchConfiguration, csvPrinter, userIdentity, detailData, effectiveDepth );
        }

        return ProcessStatus.Halt;
    }

    static UserIdentity readUserIdentityFromKey( final PwmRequest pwmRequest, final String userKey )
            throws PwmUnrecoverableException
    {
        if ( userKey.length() < 1 )
        {
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_MISSING_PARAMETER, PARAM_USERKEY + " parameter is missing" );
            LOGGER.error( pwmRequest, errorInformation );
            throw new PwmUnrecoverableException( errorInformation );
        }

        final PeopleSearchDataReader peopleSearchDataReader = new PeopleSearchDataReader( pwmRequest );
        final UserIdentity userIdentity = UserIdentity.fromKey( userKey, pwmRequest.getPwmApplication() );
        peopleSearchDataReader.checkIfUserIdentityViewable( userIdentity );
        return userIdentity;
    }
}
