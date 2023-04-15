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

package password.pwm.http.tag;

import password.pwm.PwmDomain;
import password.pwm.config.DomainConfig;
import password.pwm.config.profile.NewUserProfile;
import password.pwm.config.profile.PwmPasswordPolicy;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmSession;
import password.pwm.http.servlet.PwmServletDefinition;
import password.pwm.http.servlet.newuser.NewUserServlet;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroRequest;
import password.pwm.util.password.PasswordRequirementViewableRuleGenerator;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * @author Jason D. Rivard
 */
public class PasswordRequirementsTag extends PwmAbstractTag
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( PasswordRequirementsTag.class );
    private String separator;
    private String prepend;
    private String form;

    public String getSeparator( )
    {
        return separator;
    }

    public void setSeparator( final String separator )
    {
        this.separator = separator;
    }

    public String getPrepend( )
    {
        return prepend;
    }

    public void setPrepend( final String prepend )
    {
        this.prepend = prepend;
    }

    public String getForm( )
    {
        return form;
    }

    public void setForm( final String form )
    {
        this.form = form;
    }

    @Override
    protected PwmLogger getLogger()
    {
        return LOGGER;
    }

    @Override
    protected String generateTagBodyContents( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException
    {
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final PwmDomain pwmDomain = pwmRequest.getPwmDomain();
        final DomainConfig config = pwmDomain.getConfig();
        final Locale locale = pwmSession.getSessionStateBean().getLocale();

        pwmRequest.getMacroMachine( );

        final PwmPasswordPolicy passwordPolicy = readPasswordPolicy( pwmRequest );

        final Optional<String> configuredRuleText = passwordPolicy.getRuleText( pwmRequest.getLocale() );
        if ( configuredRuleText.isPresent() )
        {
            return configuredRuleText.get();
        }
        else
        {
            final MacroRequest macroRequest = pwmRequest.getMacroMachine( );

            final String pre = prepend != null && prepend.length() > 0 ? prepend : "";
            final String sep = separator != null && separator.length() > 0 ? separator : "<br/>";
            final List<String> requirementsList = PasswordRequirementViewableRuleGenerator
                    .generate( passwordPolicy, config, locale, macroRequest );

            final StringBuilder requirementsText = new StringBuilder();
            for ( final String requirementStatement : requirementsList )
            {
                requirementsText.append( pre );
                requirementsText.append( requirementStatement );
                requirementsText.append( sep );
            }

            return requirementsText.toString();
        }
    }

    static PwmPasswordPolicy readPasswordPolicy( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException
    {
        if ( pwmRequest.isAuthenticated() )
        {
            return pwmRequest.getPwmSession().getUserInfo().getPasswordPolicy();
        }

        if ( pwmRequest.getURL().matches( PwmServletDefinition.NewUser ) )
        {
            final NewUserProfile newUserProfile = NewUserServlet.getNewUserProfile( pwmRequest );
            return newUserProfile.getNewUserPasswordPolicy( pwmRequest.getPwmRequestContext() );
        }

        throw new PwmUnrecoverableException( PwmError.ERROR_INTERNAL, "password policy unavailable for requirements text generation" );
    }
}

