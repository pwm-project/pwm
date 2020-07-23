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
        catch ( final PwmUnrecoverableException e )
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
            catch ( final PwmUnrecoverableException e )
            {
                errorInformation = e.getErrorInformation();
            }
        }

        JsonResponse jsonResponse = null;
        try
        {
            jsonResponse = JsonResponse.makeResponse( beanBeanCryptoMachine, stateMachine );
        }
        catch ( final PwmUnrecoverableException e )
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

