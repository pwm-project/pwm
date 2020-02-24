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

package password.pwm.http.servlet.updateprofile;

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import lombok.Data;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.TokenDestinationItem;
import password.pwm.config.PwmSetting;
import password.pwm.config.profile.UpdateProfileProfile;
import password.pwm.config.value.data.FormConfiguration;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmDataValidationException;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpMethod;
import password.pwm.http.JspUrl;
import password.pwm.http.ProcessStatus;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmRequestAttribute;
import password.pwm.http.PwmSession;
import password.pwm.http.bean.UpdateProfileBean;
import password.pwm.http.servlet.ControlledPwmServlet;
import password.pwm.i18n.Message;
import password.pwm.svc.event.AuditEvent;
import password.pwm.svc.event.AuditRecord;
import password.pwm.svc.event.AuditRecordFactory;
import password.pwm.svc.token.TokenService;
import password.pwm.svc.token.TokenType;
import password.pwm.svc.token.TokenUtil;
import password.pwm.util.form.FormUtility;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;
import password.pwm.ws.server.RestResultBean;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.URLConnection;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * User interaction servlet for updating user attributes.
 *
 * @author Jason D. Rivard
 */
@WebServlet(
        name = "UpdateProfileServlet",
        urlPatterns = {
                PwmConstants.URL_PREFIX_PRIVATE + "/updateprofile",
                PwmConstants.URL_PREFIX_PRIVATE + "/UpdateProfile"
        }
)
public class UpdateProfileServlet extends ControlledPwmServlet
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( UpdateProfileServlet.class );

    @Data
    public static class ValidateResponse implements Serializable
    {
        private String message;
        private boolean success;
    }

    public enum UpdateProfileAction implements ProcessAction
    {
        updateProfile( HttpMethod.POST ),
        agree( HttpMethod.POST ),
        confirm( HttpMethod.POST ),
        reset( HttpMethod.POST ),
        validate( HttpMethod.POST ),
        enterCode( HttpMethod.POST ),
        uploadPhoto( HttpMethod.POST ),
        deletePhoto( HttpMethod.POST ),
        readPhoto( HttpMethod.GET ),;

        private final HttpMethod method;

        UpdateProfileAction( final HttpMethod method )
        {
            this.method = method;
        }

        public Collection<HttpMethod> permittedMethods( )
        {
            return Collections.singletonList( method );
        }
    }

    public enum ResetAction
    {
        unConfirm,
        exitProfileUpdate,
    }


    @Override
    public Class<? extends ProcessAction> getProcessActionsClass( )
    {
        return UpdateProfileAction.class;
    }

    private static UpdateProfileProfile getProfile( final PwmRequest pwmRequest ) throws PwmUnrecoverableException
    {
        return pwmRequest.getPwmSession().getSessionManager().getUpdateAttributeProfile( );
    }

    private static UpdateProfileBean getBean( final PwmRequest pwmRequest ) throws PwmUnrecoverableException
    {
        return pwmRequest.getPwmApplication().getSessionStateService().getBean( pwmRequest, UpdateProfileBean.class );
    }

    @ActionHandler( action = "enterCode" )
    ProcessStatus handleEnterCodeRequest(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException, ServletException, IOException
    {
        setLastError( pwmRequest, null );

        final UpdateProfileBean updateProfileBean = getBean( pwmRequest );
        final UpdateProfileProfile updateProfileProfile = getProfile( pwmRequest );

        final String userEnteredCode = pwmRequest.readParameterAsString( PwmConstants.PARAM_TOKEN );

        final TokenDestinationItem tokenDestinationItem = UpdateProfileUtil.tokenDestinationItemForCurrentValidation(
                pwmRequest,
                updateProfileBean,
                updateProfileProfile
        );

        ErrorInformation errorInformation = null;
        try
        {
            TokenUtil.checkEnteredCode(
                    pwmRequest.commonValues(),
                    userEnteredCode,
                    tokenDestinationItem,
                    pwmRequest.getUserInfoIfLoggedIn(),
                    TokenType.UPDATE,
                    TokenService.TokenEntryType.authenticated
            );
        }
        catch ( final PwmUnrecoverableException e )
        {
            LOGGER.debug( pwmRequest, () -> "error while checking entered token: " );
            errorInformation = e.getErrorInformation();
        }

        if ( errorInformation != null )
        {
            setLastError( pwmRequest, errorInformation );
            UpdateProfileUtil.forwardToEnterCode( pwmRequest, updateProfileProfile, updateProfileBean );
            return ProcessStatus.Halt;
        }

        LOGGER.debug( pwmRequest, () -> "marking token as passed " + JsonUtil.serialize( tokenDestinationItem ) );
        updateProfileBean.getCompletedTokenFields().add( updateProfileBean.getCurrentTokenField() );
        updateProfileBean.setTokenSent( false );
        updateProfileBean.setCurrentTokenField( null );

        if ( pwmRequest.getConfig().readSettingAsBoolean( PwmSetting.DISPLAY_TOKEN_SUCCESS_BUTTON ) )
        {
            pwmRequest.setAttribute( PwmRequestAttribute.TokenDestItems, tokenDestinationItem );
            pwmRequest.forwardToJsp( JspUrl.UPDATE_ATTRIBUTES_TOKEN_SUCCESS );
            return ProcessStatus.Halt;
        }

        return ProcessStatus.Continue;
    }

    @ActionHandler( action = "validate" )
    ProcessStatus restValidateForm(
            final PwmRequest pwmRequest
    )
            throws IOException, ServletException, PwmUnrecoverableException, ChaiUnavailableException
    {
        final UpdateProfileBean updateProfileBean = getBean( pwmRequest );
        final UpdateProfileProfile updateProfileProfile = getProfile( pwmRequest );

        boolean success = true;
        String userMessage = Message.getLocalizedMessage( pwmRequest.getLocale(), Message.Success_UpdateForm, pwmRequest.getConfig() );

        try
        {
            // read in the responses from the request
            final Map<FormConfiguration, String> formValues = UpdateProfileUtil.readFromJsonRequest( pwmRequest, updateProfileProfile, updateProfileBean );

            // verify form meets the form requirements
            UpdateProfileUtil.verifyFormAttributes( pwmRequest.getPwmApplication(), pwmRequest.getUserInfoIfLoggedIn(), pwmRequest.getLocale(), formValues, true );

            updateProfileBean.getFormData().putAll( FormUtility.asStringMap( formValues ) );
        }
        catch ( final PwmOperationalException e )
        {
            success = false;
            userMessage = e.getErrorInformation().toUserStr( pwmRequest.getPwmSession(), pwmRequest.getPwmApplication() );
        }

        final ValidateResponse response = new ValidateResponse();
        response.setMessage( userMessage );
        response.setSuccess( success );
        pwmRequest.outputJsonResult( RestResultBean.withData( response ) );
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "reset" )
    private ProcessStatus processReset( final PwmRequest pwmRequest )
            throws IOException, PwmUnrecoverableException
    {
        final ResetAction resetType = pwmRequest.readParameterAsEnum( PwmConstants.PARAM_RESET_TYPE, ResetAction.class, ResetAction.exitProfileUpdate );

        switch ( resetType )
        {
            case unConfirm:
                final UpdateProfileBean updateProfileBean = getBean( pwmRequest );
                updateProfileBean.setFormSubmitted( false );
                updateProfileBean.setConfirmationPassed( false );
                updateProfileBean.getCompletedTokenFields().clear();
                updateProfileBean.setTokenSent( false );
                updateProfileBean.setCurrentTokenField( null );
                break;

            case exitProfileUpdate:
                pwmRequest.getPwmApplication().getSessionStateService().clearBean( pwmRequest, UpdateProfileBean.class );
                pwmRequest.sendRedirectToContinue();
                return ProcessStatus.Halt;

            default:
                JavaHelper.unhandledSwitchStatement( resetType );

        }

        return ProcessStatus.Continue;
    }


    @ActionHandler( action = "agree" )
    ProcessStatus handleAgreeRequest( final PwmRequest pwmRequest )
            throws ServletException, IOException, PwmUnrecoverableException, ChaiUnavailableException
    {
        LOGGER.debug( pwmRequest, () -> "user accepted agreement" );

        final UpdateProfileBean updateProfileBean = getBean( pwmRequest );
        if ( !updateProfileBean.isAgreementPassed() )
        {
            updateProfileBean.setAgreementPassed( true );
            final AuditRecord auditRecord = new AuditRecordFactory( pwmRequest ).createUserAuditRecord(
                    AuditEvent.AGREEMENT_PASSED,
                    pwmRequest.getUserInfoIfLoggedIn(),
                    pwmRequest.getLabel(),
                    "UpdateProfile"
            );
            pwmRequest.getPwmApplication().getAuditManager().submit( auditRecord );
        }

        return ProcessStatus.Continue;
    }

    @ActionHandler( action = "confirm" )
    ProcessStatus handleConfirmRequest( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException
    {
        final UpdateProfileBean updateProfileBean = getBean( pwmRequest );
        updateProfileBean.setConfirmationPassed( true );

        return ProcessStatus.Continue;
    }

    @ActionHandler( action = "updateProfile" )
    ProcessStatus handleUpdateProfileRequest(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException, ChaiUnavailableException
    {
        final UpdateProfileBean updateProfileBean = getBean( pwmRequest );
        final UpdateProfileProfile updateProfileProfile = getProfile( pwmRequest );

        try
        {
            readFormParametersFromRequest( pwmRequest, updateProfileProfile, updateProfileBean );
        }
        catch ( final PwmOperationalException e )
        {
            LOGGER.error( pwmRequest, () -> e.getMessage() );
            setLastError( pwmRequest, e.getErrorInformation() );
        }

        updateProfileBean.setFormSubmitted( true );

        return ProcessStatus.Continue;
    }

    protected void nextStep( final PwmRequest pwmRequest )
            throws IOException, ServletException, PwmUnrecoverableException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final UpdateProfileBean updateProfileBean = getBean( pwmRequest );
        final UpdateProfileProfile updateProfileProfile = getProfile( pwmRequest );
        final PwmSession pwmSession = pwmRequest.getPwmSession();

        {
            final String updateProfileAgreementText = updateProfileProfile.readSettingAsLocalizedString(
                    PwmSetting.UPDATE_PROFILE_AGREEMENT_MESSAGE,
                    pwmSession.getSessionStateBean().getLocale()
            );

            if ( !StringUtil.isEmpty( updateProfileAgreementText ) )
            {
                if ( !updateProfileBean.isAgreementPassed() )
                {
                    final MacroMachine macroMachine = pwmRequest.getPwmSession().getSessionManager().getMacroMachine( );
                    final String expandedText = macroMachine.expandMacros( updateProfileAgreementText );
                    pwmRequest.setAttribute( PwmRequestAttribute.AgreementText, expandedText );
                    pwmRequest.forwardToJsp( JspUrl.UPDATE_ATTRIBUTES_AGREEMENT );
                    return;
                }
            }
        }

        //make sure there is form data in the bean.
        if ( !updateProfileBean.isFormLdapLoaded() )
        {
            updateProfileBean.getFormData().clear();
            updateProfileBean.getFormData().putAll( ( UpdateProfileUtil.formDataFromLdap( pwmRequest, updateProfileProfile ) ) );
            updateProfileBean.setFormLdapLoaded( true );
            UpdateProfileUtil.forwardToForm( pwmRequest, updateProfileProfile, updateProfileBean );
            return;
        }

        if ( !updateProfileBean.isFormSubmitted() )
        {
            UpdateProfileUtil.forwardToForm( pwmRequest, updateProfileProfile, updateProfileBean );
            return;
        }


        // validate the form data.
        try
        {
            // verify form meets the form requirements
            final List<FormConfiguration> formFields = updateProfileProfile.readSettingAsForm( PwmSetting.UPDATE_PROFILE_FORM );
            final Map<FormConfiguration, String> formValues = FormUtility.readFormValuesFromMap( updateProfileBean.getFormData(), formFields, pwmRequest.getLocale() );
            UpdateProfileUtil.verifyFormAttributes( pwmRequest.getPwmApplication(), pwmRequest.getUserInfoIfLoggedIn(), pwmRequest.getLocale(), formValues, true );
        }
        catch ( final PwmException e )
        {
            LOGGER.error( pwmRequest, () -> e.getMessage() );
            setLastError( pwmRequest, e.getErrorInformation() );
            UpdateProfileUtil.forwardToForm( pwmRequest, updateProfileProfile, updateProfileBean );
            return;
        }

        {
            final boolean requireConfirmation = updateProfileProfile.readSettingAsBoolean( PwmSetting.UPDATE_PROFILE_SHOW_CONFIRMATION );
            if ( requireConfirmation && !updateProfileBean.isConfirmationPassed() )
            {
                UpdateProfileUtil.forwardToConfirmForm( pwmRequest, updateProfileProfile, updateProfileBean );
                return;
            }
        }

        if ( UpdateProfileUtil.checkForTokenVerificationProgress( pwmRequest, updateProfileBean, updateProfileProfile ) == ProcessStatus.Halt )
        {
            return;
        }

        try
        {
            // write the form values
            final ChaiUser theUser = pwmSession.getSessionManager().getActor( );
            UpdateProfileUtil.doProfileUpdate(
                    pwmRequest.getPwmApplication(),
                    pwmRequest.getLabel(),
                    pwmRequest.getLocale(),
                    pwmSession.getUserInfo(),
                    pwmSession.getSessionManager().getMacroMachine( ),
                    updateProfileProfile,
                    updateProfileBean.getFormData(),
                    theUser
            );

            // re-populate the uiBean because we have changed some values.
            pwmSession.reloadUserInfoBean( pwmRequest );

            // mark the event log
            pwmApplication.getAuditManager().submit( AuditEvent.UPDATE_PROFILE, pwmSession.getUserInfo(), pwmSession );

            // clear the bean
            pwmApplication.getSessionStateService().clearBean( pwmRequest, UpdateProfileBean.class );

            pwmRequest.getPwmResponse().forwardToSuccessPage( Message.Success_UpdateProfile );
            return;
        }
        catch ( final PwmException e )
        {
            LOGGER.error( pwmRequest, () -> e.getMessage() );
            setLastError( pwmRequest, e.getErrorInformation() );
        }
        catch ( final ChaiException e )
        {
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_UPDATE_ATTRS_FAILURE, e.toString() );
            LOGGER.error( pwmRequest, () -> errorInformation.toDebugStr() );
            setLastError( pwmRequest, errorInformation );
        }

        UpdateProfileUtil.forwardToForm( pwmRequest, updateProfileProfile, updateProfileBean );
    }

    @Override
    public ProcessStatus preProcessCheck( final PwmRequest pwmRequest ) throws PwmUnrecoverableException, IOException, ServletException
    {
        if ( !pwmRequest.getPwmApplication().getConfig().readSettingAsBoolean( PwmSetting.UPDATE_PROFILE_ENABLE ) )
        {
            pwmRequest.respondWithError( new ErrorInformation(
                    PwmError.ERROR_SERVICE_NOT_AVAILABLE,
                    "Setting " + PwmSetting.UPDATE_PROFILE_ENABLE.toMenuLocationDebug( null, null ) + " is not enabled." )
            );
            return ProcessStatus.Halt;
        }

        final UpdateProfileProfile updateProfileProfile = getProfile( pwmRequest );
        if ( updateProfileProfile == null )
        {
            pwmRequest.respondWithError( new ErrorInformation( PwmError.ERROR_NO_PROFILE_ASSIGNED ) );
            return ProcessStatus.Halt;
        }

        return ProcessStatus.Continue;
    }


    final Map<FormConfiguration, String> readFormParametersFromRequest(
            final PwmRequest pwmRequest,
            final UpdateProfileProfile updateProfileProfile,
            final UpdateProfileBean updateProfileBean
    )
            throws PwmUnrecoverableException, PwmDataValidationException, ChaiUnavailableException
    {
        final List<FormConfiguration> formFields = updateProfileProfile.readSettingAsForm( PwmSetting.UPDATE_PROFILE_FORM );

        //read the values from the request
        final Map<FormConfiguration, String> formValueMap = FormUtility.readFormValuesFromRequest( pwmRequest, formFields, pwmRequest.getLocale() );

        return UpdateProfileUtil.updateBeanFormData( formFields, formValueMap, updateProfileBean );
    }

    @ActionHandler( action = "uploadPhoto" )
    public ProcessStatus uploadPhoto( final PwmRequest pwmRequest ) throws ServletException, PwmUnrecoverableException, IOException
    {
        final HttpServletRequest req = pwmRequest.getHttpServletRequest();
        final String fieldName = pwmRequest.readParameterAsString( "field" );
        final UpdateProfileBean updateProfileBean = getBean( pwmRequest );
        final UpdateProfileProfile updateProfileProfile = getProfile( pwmRequest );
        final List<FormConfiguration> formFields = updateProfileProfile.readSettingAsForm( PwmSetting.UPDATE_PROFILE_FORM );
        final FormConfiguration formConfiguration = FormUtility.asFormNameMap( formFields ).get( fieldName );
        final int maxSize = formConfiguration.getMaximumSize();

        if ( ServletFileUpload.isMultipartContent( req ) )
        {
            final InputStream uploadedFile = pwmRequest.readFileUploadStream( PwmConstants.PARAM_FILE_UPLOAD );
            if ( uploadedFile != null )
            {
                final byte[] bytes;
                {
                    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    JavaHelper.copy( uploadedFile, baos );
                    baos.flush();
                    bytes = baos.toByteArray();
                }

                if ( bytes.length > maxSize )
                {
                    final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_FILE_TOO_LARGE,
                            "file size (" + bytes.length + ") exceeds maximum file size (" + maxSize + ")",
                            new String[]
                                    {
                                            String.valueOf( maxSize ),
                                    }
                    );
                    pwmRequest.outputJsonResult( RestResultBean.fromError( errorInformation, pwmRequest ) );
                    return ProcessStatus.Halt;
                }

                final String b64String = StringUtil.base64Encode( bytes );

                if ( !JavaHelper.isEmpty( formConfiguration.getMimeTypes() ) )
                {
                    final String mimeType = URLConnection.guessContentTypeFromStream( new ByteArrayInputStream( bytes ) );
                    if ( !formConfiguration.getMimeTypes().contains( mimeType ) )
                    {
                        final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_FILE_TYPE_INCORRECT, "incorrect file type of " + mimeType, new String[]
                                {
                                        mimeType,
                                }
                        );
                        pwmRequest.outputJsonResult( RestResultBean.fromError( errorInformation, pwmRequest ) );
                        return ProcessStatus.Halt;
                    }
                }
                updateProfileBean.getFormData().put( fieldName, b64String );
            }
        }

        final RestResultBean restResultBean = RestResultBean.forSuccessMessage( pwmRequest, Message.Success_Unknown );
        pwmRequest.getPwmResponse().outputJsonResult( restResultBean );

        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "deletePhoto" )
    public ProcessStatus deletePhotoHandler( final PwmRequest pwmRequest ) throws ServletException, PwmUnrecoverableException, IOException
    {
        final String fieldName = pwmRequest.readParameterAsString( "field" );
        final UpdateProfileBean updateProfileBean = getBean( pwmRequest );

        updateProfileBean.getFormData().put( fieldName, "" );

        return ProcessStatus.Continue;
    }

    @ActionHandler( action = "readPhoto" )
    public ProcessStatus readPhotoHandler( final PwmRequest pwmRequest ) throws ServletException, PwmUnrecoverableException, IOException
    {
        final String fieldName = pwmRequest.readParameterAsString( "field" );
        final UpdateProfileBean updateProfileBean = getBean( pwmRequest );

        final String b64value = updateProfileBean.getFormData().get( fieldName );
        if ( !StringUtil.isEmpty( b64value ) )
        {
            final byte[] bytes = StringUtil.base64Decode( b64value );

            try ( OutputStream outputStream = pwmRequest.getPwmResponse().getOutputStream() )
            {
                final HttpServletResponse resp = pwmRequest.getPwmResponse().getHttpServletResponse();
                final String mimeType = URLConnection.guessContentTypeFromStream( new ByteArrayInputStream( bytes ) );
                resp.setContentType( mimeType );
                outputStream.write( bytes );
                outputStream.flush();
            }
        }

        return ProcessStatus.Halt;
    }
}

