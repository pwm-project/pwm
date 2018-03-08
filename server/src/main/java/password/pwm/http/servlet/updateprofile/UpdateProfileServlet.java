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

package password.pwm.http.servlet.updateprofile;

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import lombok.Data;
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
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;
import password.pwm.ws.server.RestResultBean;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import java.io.IOException;
import java.io.Serializable;
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
        unConfirm( HttpMethod.POST ),
        validate( HttpMethod.POST ),
        enterCode( HttpMethod.POST ),;

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

    @Override
    public Class<? extends ProcessAction> getProcessActionsClass( )
    {
        return UpdateProfileAction.class;
    }

    private static UpdateProfileProfile getProfile( final PwmRequest pwmRequest ) throws PwmUnrecoverableException
    {
        return pwmRequest.getPwmSession().getSessionManager().getUpdateAttributeProfile( pwmRequest.getPwmApplication() );
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
                    pwmRequest,
                    userEnteredCode,
                    tokenDestinationItem,
                    pwmRequest.getUserInfoIfLoggedIn(),
                    TokenType.UPDATE,
                    TokenService.TokenEntryType.authenticated
            );
        }
        catch ( PwmUnrecoverableException e )
        {
            LOGGER.debug( pwmRequest, "error while checking entered token: " );
            errorInformation = e.getErrorInformation();
        }

        if ( errorInformation != null )
        {
            setLastError( pwmRequest, errorInformation );
            UpdateProfileUtil.forwardToEnterCode( pwmRequest, updateProfileProfile, updateProfileBean );
            return ProcessStatus.Halt;
        }

        LOGGER.debug( pwmRequest, "marking token as passed " + JsonUtil.serialize( tokenDestinationItem ) );
        updateProfileBean.getCompletedTokenFields().add( updateProfileBean.getCurrentTokenField() );
        updateProfileBean.setTokenSent( false );
        updateProfileBean.setCurrentTokenField( null );

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
        catch ( PwmOperationalException e )
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

    @ActionHandler( action = "unConfirm" )
    ProcessStatus handleUnconfirm(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException
    {
        final UpdateProfileBean updateProfileBean = getBean( pwmRequest );

        updateProfileBean.setFormSubmitted( false );
        updateProfileBean.setConfirmationPassed( false );
        updateProfileBean.getCompletedTokenFields().clear();
        updateProfileBean.setTokenSent( false );
        updateProfileBean.setCurrentTokenField( null );

        return ProcessStatus.Continue;
    }

    @ActionHandler( action = "agree" )
    ProcessStatus handleAgreeRequest( final PwmRequest pwmRequest )
            throws ServletException, IOException, PwmUnrecoverableException, ChaiUnavailableException
    {
        LOGGER.debug( pwmRequest, "user accepted agreement" );

        final UpdateProfileBean updateProfileBean = getBean( pwmRequest );
        if ( !updateProfileBean.isAgreementPassed() )
        {
            updateProfileBean.setAgreementPassed( true );
            final AuditRecord auditRecord = new AuditRecordFactory( pwmRequest ).createUserAuditRecord(
                    AuditEvent.AGREEMENT_PASSED,
                    pwmRequest.getUserInfoIfLoggedIn(),
                    pwmRequest.getSessionLabel(),
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
        catch ( PwmOperationalException e )
        {
            LOGGER.error( pwmRequest, e.getMessage() );
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
                    final MacroMachine macroMachine = pwmRequest.getPwmSession().getSessionManager().getMacroMachine( pwmRequest.getPwmApplication() );
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
        catch ( PwmException e )
        {
            LOGGER.error( pwmSession, e.getMessage() );
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
            final ChaiUser theUser = pwmSession.getSessionManager().getActor( pwmApplication );
            UpdateProfileUtil.doProfileUpdate(
                    pwmRequest.getPwmApplication(),
                    pwmRequest.getSessionLabel(),
                    pwmRequest.getLocale(),
                    pwmSession.getUserInfo(),
                    pwmSession.getSessionManager().getMacroMachine( pwmApplication ),
                    updateProfileProfile,
                    updateProfileBean.getFormData(),
                    theUser
            );

            // re-populate the uiBean because we have changed some values.
            pwmSession.reloadUserInfoBean( pwmApplication );

            // clear cached read attributes.
            pwmRequest.getPwmSession().reloadUserInfoBean( pwmApplication );

            // mark the event log
            pwmApplication.getAuditManager().submit( AuditEvent.UPDATE_PROFILE, pwmSession.getUserInfo(), pwmSession );

            // clear the bean
            pwmApplication.getSessionStateService().clearBean( pwmRequest, UpdateProfileBean.class );

            pwmRequest.getPwmResponse().forwardToSuccessPage( Message.Success_UpdateProfile );
            return;
        }
        catch ( PwmException e )
        {
            LOGGER.error( pwmSession, e.getMessage() );
            setLastError( pwmRequest, e.getErrorInformation() );
        }
        catch ( ChaiException e )
        {
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_UPDATE_ATTRS_FAILURE, e.toString() );
            LOGGER.error( pwmSession, errorInformation.toDebugStr() );
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


}

