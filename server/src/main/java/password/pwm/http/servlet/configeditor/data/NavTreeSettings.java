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

package password.pwm.http.servlet.configeditor.data;

import lombok.Builder;
import lombok.Value;
import password.pwm.EnvironmentProperty;
import password.pwm.PwmConstants;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmHttpRequestWrapper;
import password.pwm.http.PwmRequest;
import password.pwm.http.servlet.configeditor.DomainManageMode;
import password.pwm.http.servlet.configeditor.DomainStateReader;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;

@Value
@Builder
public class NavTreeSettings
{
    private final boolean modifiedSettingsOnly;

    @Builder.Default
    private final int level = 2;

    private final String filterText;

    @Builder.Default
    private final Locale locale = PwmConstants.DEFAULT_LOCALE;

    private final DomainManageMode domainManageMode;

    private final boolean mangeHttps;

    public static NavTreeSettings forBasic()
    {
        return NavTreeSettings.builder()
                .domainManageMode( DomainManageMode.system )
                .build();
    }

    public static NavTreeSettings readFromRequest( final PwmRequest pwmRequest ) throws PwmUnrecoverableException, IOException
    {
        final Map<String, Object> inputParameters = pwmRequest.readBodyAsJsonMap( PwmHttpRequestWrapper.Flag.BypassValidation );
        final boolean modifiedSettingsOnly = ( boolean ) inputParameters.get( "modifiedSettingsOnly" );
        final int level = ( int ) ( ( double ) inputParameters.get( "level" ) );
        final String filterText = ( String ) inputParameters.get( "text" );
        final DomainStateReader domainStateReader = DomainStateReader.forRequest( pwmRequest );
        final boolean manageHttps = pwmRequest.getPwmApplication().getPwmEnvironment().readPropertyAsBoolean( EnvironmentProperty.ManageHttps );

        return NavTreeSettings.builder()
                .modifiedSettingsOnly( modifiedSettingsOnly )
                .domainManageMode( domainStateReader.getMode() )
                .mangeHttps( manageHttps )
                .level( level )
                .filterText( filterText )
                .locale( pwmRequest.getLocale() )
                .build();
    }
}
