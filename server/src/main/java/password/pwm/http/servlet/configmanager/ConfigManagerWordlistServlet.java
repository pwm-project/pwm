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

package password.pwm.http.servlet.configmanager;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import lombok.Builder;
import lombok.Value;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpMethod;
import password.pwm.http.JspUrl;
import password.pwm.http.PwmRequest;
import password.pwm.http.bean.DisplayElement;
import password.pwm.http.servlet.AbstractPwmServlet;
import password.pwm.i18n.Display;
import password.pwm.i18n.Message;
import password.pwm.svc.wordlist.Wordlist;
import password.pwm.svc.wordlist.WordlistConfiguration;
import password.pwm.svc.wordlist.WordlistSourceType;
import password.pwm.svc.wordlist.WordlistStatus;
import password.pwm.svc.wordlist.WordlistType;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;
import password.pwm.ws.server.RestResultBean;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

@WebServlet(
        name = "ConfigManagerWordlistServlet",
        urlPatterns = {
                PwmConstants.URL_PREFIX_PRIVATE + "/config/manager/wordlists",
        }
)
public class ConfigManagerWordlistServlet extends AbstractPwmServlet
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( ConfigManagerWordlistServlet.class );

    public enum ConfigManagerAction implements ProcessAction
    {
        clearWordlist( HttpMethod.POST ),
        uploadWordlist( HttpMethod.POST ),
        readWordlistData( HttpMethod.POST ),;

        private final HttpMethod method;

        ConfigManagerAction( final HttpMethod method )
        {
            this.method = method;
        }

        public Collection<HttpMethod> permittedMethods( )
        {
            return Collections.singletonList( method );
        }
    }

    protected ConfigManagerAction readProcessAction( final PwmRequest request )
            throws PwmUnrecoverableException
    {
        try
        {
            return ConfigManagerAction.valueOf( request.readParameterAsString( PwmConstants.PARAM_ACTION_REQUEST ) );
        }
        catch ( IllegalArgumentException e )
        {
            return null;
        }
    }

    protected void processAction( final PwmRequest pwmRequest )
            throws ServletException, IOException, ChaiUnavailableException, PwmUnrecoverableException
    {

        final ConfigManagerAction processAction = readProcessAction( pwmRequest );
        if ( processAction != null )
        {
            switch ( processAction )
            {
                case uploadWordlist:
                    restUploadWordlist( pwmRequest );
                    return;

                case clearWordlist:
                    restClearWordlist( pwmRequest );
                    return;

                case readWordlistData:
                    restReadWordlistData( pwmRequest );
                    return;

                default:
                    JavaHelper.unhandledSwitchStatement( processAction );
            }
            return;
        }

        pwmRequest.forwardToJsp( JspUrl.CONFIG_MANAGER_WORDLISTS );
    }

    void restUploadWordlist( final PwmRequest pwmRequest )
            throws IOException, ServletException, PwmUnrecoverableException

    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final HttpServletRequest req = pwmRequest.getHttpServletRequest();
        final String wordlistTypeParam = pwmRequest.readParameterAsString( "wordlist" );
        final WordlistType wordlistType = WordlistType.valueOf( wordlistTypeParam );

        if ( wordlistType == null )
        {
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INTERNAL, "unknown wordlist type: " + wordlistTypeParam );
            pwmRequest.outputJsonResult( RestResultBean.fromError( errorInformation, pwmRequest ) );
            LOGGER.error( pwmRequest, "error during import: " + errorInformation.toDebugStr() );
            return;
        }

        if ( !ServletFileUpload.isMultipartContent( req ) )
        {
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INTERNAL, "no file found in upload" );
            pwmRequest.outputJsonResult( RestResultBean.fromError( errorInformation, pwmRequest ) );
            LOGGER.error( pwmRequest, "error during import: " + errorInformation.toDebugStr() );
            return;
        }

        final InputStream inputStream = pwmRequest.readFileUploadStream( PwmConstants.PARAM_FILE_UPLOAD );

        try
        {
            wordlistType.forType( pwmApplication ).populate( inputStream );
        }
        catch ( PwmUnrecoverableException e )
        {
            final ErrorInformation errorInfo = new ErrorInformation( PwmError.ERROR_INTERNAL, e.getMessage() );
            final RestResultBean restResultBean = RestResultBean.fromError( errorInfo, pwmRequest );
            LOGGER.debug( pwmRequest, errorInfo );
            pwmRequest.outputJsonResult( restResultBean );
            return;
        }

        pwmRequest.outputJsonResult( RestResultBean.forSuccessMessage( pwmRequest, Message.Success_Unknown ) );
    }

    void restClearWordlist( final PwmRequest pwmRequest )
            throws IOException, PwmUnrecoverableException
    {
        final String wordlistTypeParam = pwmRequest.readParameterAsString( "wordlist" );
        final WordlistType wordlistType = WordlistType.valueOf( wordlistTypeParam );

        if ( wordlistType == null )
        {
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INTERNAL, "unknown wordlist type: " + wordlistTypeParam );
            pwmRequest.outputJsonResult( RestResultBean.fromError( errorInformation, pwmRequest ) );
            LOGGER.error( pwmRequest, "error during clear: " + errorInformation.toDebugStr() );
            return;
        }

        try
        {
            wordlistType.forType( pwmRequest.getPwmApplication() ).clear();
        }
        catch ( Exception e )
        {
            LOGGER.error( "error clearing wordlist " + wordlistType + ", error: " + e.getMessage() );
        }

        pwmRequest.outputJsonResult( RestResultBean.forSuccessMessage( pwmRequest, Message.Success_Unknown ) );
    }

    void restReadWordlistData( final PwmRequest pwmRequest )
            throws IOException
    {
        final LinkedHashMap<WordlistType, WordlistDataBean> outputData = new LinkedHashMap<>();

        for ( final WordlistType wordlistType : WordlistType.values() )
        {
            final Wordlist wordlist = wordlistType.forType( pwmRequest.getPwmApplication() );
            final WordlistStatus wordlistStatus = wordlist.readWordlistStatus();
            final Wordlist.Activity activity = wordlist.getActivity();
            final WordlistConfiguration wordlistConfiguration = wordlistType.forType( pwmRequest.getPwmApplication() ).getConfiguration();

            final WordlistDataBean.WordlistDataBeanBuilder builder = WordlistDataBean.builder();
            {
                final List<DisplayElement> presentableValues = new ArrayList<>();
                presentableValues.add( new DisplayElement(
                        wordlistType.name() + "_populationStatus",
                        DisplayElement.Type.string,
                        "Import Status",
                        activity.getLabel() ) );
                presentableValues.add( new DisplayElement(
                        wordlistType.name() + "_listSource",
                        DisplayElement.Type.string, "List Source",
                        wordlistStatus.getSourceType() == null
                                ? LocaleHelper.getLocalizedMessage( Display.Value_NotApplicable, pwmRequest )
                                : wordlistStatus.getSourceType().getLabel() ) );
                if ( wordlistConfiguration.getAutoImportUrl() != null )
                {
                    presentableValues.add( new DisplayElement(
                            wordlistType.name() + "_sourceURL",
                            DisplayElement.Type.string,
                            "Configured SourceType URL",
                            wordlistConfiguration.getAutoImportUrl() ) );
                }

                presentableValues.add( new DisplayElement(
                        wordlistType.name() + "_wordCount",
                        DisplayElement.Type.number,
                        "Word Count",
                        Long.toString( wordlist.size() ) ) );

                if ( wordlistStatus.isCompleted() )
                {

                    if ( WordlistSourceType.BuiltIn != wordlistStatus.getSourceType() )
                    {
                        presentableValues.add( new DisplayElement(
                                wordlistType.name() + "_populationTimestamp",
                                DisplayElement.Type.timestamp,
                                "Population Timestamp",
                                JavaHelper.toIsoDate( wordlistStatus.getStoreDate() ) ) );
                    }
                    if ( wordlistStatus.getRemoteInfo() != null && !StringUtil.isEmpty( wordlistStatus.getRemoteInfo().getChecksum() ) )
                    {
                        presentableValues.add( new DisplayElement(
                                wordlistType.name() + "_sha256Hash",
                                DisplayElement.Type.string,
                                "SHA-256 Checksum Hash",
                                StringUtil.truncate( wordlistStatus.getRemoteInfo().getChecksum(), 32 ) + "..." ) );
                    }
                }
                if ( wordlist.getAutoImportError() != null )
                {
                    presentableValues.add( new DisplayElement(
                            wordlistType.name() + "_lastImportError",
                            DisplayElement.Type.string,
                            "Error During Import",
                            wordlist.getAutoImportError().getDetailedErrorMsg() ) );
                    presentableValues.add( new DisplayElement(
                            wordlistType.name() + "_lastImportAttempt",
                            DisplayElement.Type.timestamp,
                            "Last Import Attempt",
                            JavaHelper.toIsoDate( wordlist.getAutoImportError().getDate() ) ) );
                }
                builder.presentableData( Collections.unmodifiableList( presentableValues ) );
            }

            if ( wordlistStatus.isCompleted() )
            {
                if ( wordlistConfiguration.getAutoImportUrl() == null )
                {
                    builder.allowUpload( true );
                }
                if ( wordlistConfiguration.getAutoImportUrl() != null || wordlistStatus.getSourceType() != WordlistSourceType.BuiltIn )
                {
                    builder.allowClear( true );
                }
            }
            outputData.put( wordlistType, builder.build() );
        }
        pwmRequest.outputJsonResult( RestResultBean.withData( outputData ) );
    }

    @Value
    @Builder
    public static class WordlistDataBean implements Serializable
    {
        private List<DisplayElement> presentableData;
        private boolean allowUpload;
        private boolean allowClear;
    }
}

