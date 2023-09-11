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

import password.pwm.EnvironmentProperty;
import password.pwm.PwmConstants;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.http.servlet.configeditor.DomainManageMode;
import password.pwm.http.servlet.configeditor.DomainStateReader;

import java.io.IOException;
import java.util.Locale;

public record NavTreeSettings(
        EditorFilterState filterState,
        Locale locale,
        DomainManageMode domainManageMode,
        boolean mangeHttps
)
{
    public NavTreeSettings(
            final EditorFilterState filterState,
            final Locale locale,
            final DomainManageMode domainManageMode, final boolean mangeHttps
    )
    {
        this.filterState = filterState == null ? EditorFilterState.DEFAULT : filterState;
        this.locale = locale == null ? PwmConstants.DEFAULT_LOCALE : locale;
        this.domainManageMode = domainManageMode == null ? DomainManageMode.system : domainManageMode;
        this.mangeHttps = mangeHttps;
    }

    private static final NavTreeSettings BASIC = new NavTreeSettings(
            EditorFilterState.DEFAULT,
            PwmConstants.DEFAULT_LOCALE,
            DomainManageMode.system,
            false );

    public static NavTreeSettings forBasic()
    {
        return BASIC;
    }

    public static NavTreeSettings forMode( final DomainManageMode domainManageMode )
    {
        return new NavTreeSettings( EditorFilterState.DEFAULT, PwmConstants.DEFAULT_LOCALE, domainManageMode, false );
    }

    public static NavTreeSettings readFromRequest( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException, IOException
    {
        final EditorFilterState filterState = pwmRequest.readBodyAsJsonObject( EditorFilterState.class );
        final DomainStateReader domainStateReader = DomainStateReader.forRequest( pwmRequest );
        final boolean manageHttps = pwmRequest.getPwmApplication().getPwmEnvironment().readPropertyAsBoolean( EnvironmentProperty.ManageHttps );

        return new NavTreeSettings( filterState, pwmRequest.getLocale(), domainStateReader.getMode(), manageHttps );
    }
}
