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

package password.pwm.ws.server.rest;

import lombok.Builder;
import lombok.Value;
import password.pwm.PwmConstants;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.IdentityVerificationMethod;
import password.pwm.config.option.WebServiceUsage;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.CommonValues;
import password.pwm.http.HttpContentType;
import password.pwm.http.HttpMethod;
import password.pwm.http.bean.ForgottenPasswordBean;
import password.pwm.http.bean.ForgottenPasswordStage;
import password.pwm.http.servlet.forgottenpw.ForgottenPasswordStateMachine;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.BeanCryptoMachine;
import password.pwm.ws.server.PresentableForm;
import password.pwm.ws.server.RestMethodHandler;
import password.pwm.ws.server.RestRequest;
import password.pwm.ws.server.RestResultBean;
import password.pwm.ws.server.RestServlet;
import password.pwm.ws.server.RestWebServer;

import javax.servlet.annotation.WebServlet;
import java.io.IOException;
import java.io.Serializable;
import java.util.Map;
import java.util.Optional;

@WebServlet(
        urlPatterns = {
                PwmConstants.URL_PREFIX_PUBLIC + PwmConstants.URL_PREFIX_REST + "/forgottenpassword",
        }
)
@RestWebServer( webService = WebServiceUsage.ForgottenPassword )
public class RestForgottenPasswordServer extends RestServlet
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( RestForgottenPasswordServer.class );

    @Value
    @Builder
    static class JsonResponse implements Serializable
    {
        private ForgottenPasswordStage stage;
        private IdentityVerificationMethod method;
        private PresentableForm form;
        private String state;

        static JsonResponse makeResponse(
                final BeanCryptoMachine<ForgottenPasswordBean> beanBeanCryptoMachine,
                final ForgottenPasswordStateMachine stateMachine
        )
                throws PwmUnrecoverableException
        {
            final String encryptedState = beanBeanCryptoMachine.encrypt( stateMachine.getForgottenPasswordBean() );
            return JsonResponse.builder()
                    .state( encryptedState )
                    .method( stateMachine.getForgottenPasswordBean().getProgress().getInProgressVerificationMethod() )
                    .stage( stateMachine.nextStage() )
                    .form( stateMachine.nextForm() )
                    .build();
        }
    }

    @Value
    static class JsonInput implements Serializable
    {
        private String state;
        private Map<String, String> form;
    }


    @Override
    public void preCheckRequest( final RestRequest request ) throws PwmUnrecoverableException
    {

    }

    @RestMethodHandler( method = HttpMethod.POST, consumes = HttpContentType.json, produces = HttpContentType.json )
    public RestResultBean doRestForgottenPasswordService( final RestRequest restRequest )
            throws PwmUnrecoverableException, IOException
    {
        final CommonValues commonValues = restRequest.commonValues();
        final JsonInput jsonInput = restRequest.readBodyAsJsonObject( JsonInput.class );
        final BeanCryptoMachine<ForgottenPasswordBean> beanBeanCryptoMachine = new BeanCryptoMachine<>( commonValues, figureMaxIdleTimeout( commonValues ) );
        final ForgottenPasswordStateMachine stateMachine;

        final boolean newState;
        try
        {
            final Optional<ForgottenPasswordBean> readBean = beanBeanCryptoMachine.decryprt( jsonInput.getState() );
            final ForgottenPasswordBean inputBean = readBean.orElseGet( ForgottenPasswordBean::new );
            stateMachine = new ForgottenPasswordStateMachine(
                    restRequest.commonValues(),
                    inputBean );

            newState = !readBean.isPresent();

            stateMachine.nextStage();
        }
        catch ( PwmUnrecoverableException e )
        {
            return RestResultBean.fromError( e.getErrorInformation() );
        }

        ErrorInformation errorInformation = null;
        if ( !newState && !JavaHelper.isEmpty( jsonInput.getForm() ) )
        {
            try
            {
                stateMachine.applyFormValues( jsonInput.getForm() );
            }
            catch ( PwmUnrecoverableException e )
            {
                errorInformation = e.getErrorInformation();
            }
        }

        JsonResponse jsonResponse = null;
        try
        {
            jsonResponse = JsonResponse.makeResponse( beanBeanCryptoMachine, stateMachine );
        }
        catch ( PwmUnrecoverableException e )
        {
            errorInformation = e.getErrorInformation();
        }

        final RestResultBean restResultBean = RestResultBean.fromErrorWithData( restRequest, errorInformation, jsonResponse );
        LOGGER.trace( restRequest.getSessionLabel(), () -> "Sending Response State: " + JsonUtil.serialize( restResultBean ) );
        return restResultBean;
    }

    private TimeDuration figureMaxIdleTimeout( final CommonValues commonValues )
    {
        final long idleSeconds = commonValues.getConfig().readSettingAsLong( PwmSetting.IDLE_TIMEOUT_SECONDS );
        return TimeDuration.of( idleSeconds, TimeDuration.Unit.SECONDS );
    }

}

