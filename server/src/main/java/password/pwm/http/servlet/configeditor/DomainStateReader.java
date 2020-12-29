/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2020 The PWM Project
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

package password.pwm.http.servlet.configeditor;

import password.pwm.bean.DomainID;
import password.pwm.config.AppConfig;
import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingScope;
import password.pwm.config.stored.StoredConfiguration;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmURL;
import password.pwm.http.bean.ConfigManagerBean;
import password.pwm.http.servlet.PwmServletDefinition;

import java.util.Optional;
import java.util.Set;

class DomainStateReader
{
    private final PwmRequest pwmRequest;

    private DomainStateReader( final PwmRequest pwmRequest )
    {
        this.pwmRequest = pwmRequest;
    }

    public static DomainStateReader forRequest( final PwmRequest pwmRequest )
    {
        return new DomainStateReader( pwmRequest );
    }

    public boolean isCorrectlyIndicated()
            throws PwmUnrecoverableException
    {
        final DomainManageMode mode = getMode();
        if ( mode == DomainManageMode.single )
        {
            return true;
        }

        return readDomainIdFromRequest().isPresent();
    }

    private AppConfig getAppConfig()
            throws PwmUnrecoverableException
    {
        final ConfigManagerBean configManagerBean = ConfigEditorServlet.getBean( pwmRequest );
        final StoredConfiguration storedConfiguration = configManagerBean.getStoredConfiguration();
        return new AppConfig( storedConfiguration );
    }

    public DomainManageMode getMode()
            throws PwmUnrecoverableException
    {
        if ( getAppConfig().getDomainIDs().size() < 2 )
        {
            return DomainManageMode.single;
        }

        final Optional<DomainID> optionalDomainID = readDomainIdFromRequest();
        if ( optionalDomainID.isEmpty() )
        {
            return DomainManageMode.system;
        }

        if ( optionalDomainID.get().isSystem() )
        {
            return DomainManageMode.system;
        }

        return DomainManageMode.domain;
    }

    public DomainID getDomainID( final PwmSetting pwmSetting )
            throws PwmUnrecoverableException
    {
        final DomainManageMode mode = getMode();
        if ( mode == DomainManageMode.system )
        {
            return DomainID.systemId();
        }

        final Optional<DomainID> optionalDomainID = readDomainIdFromRequest();
        if ( mode == DomainManageMode.domain )
        {
            if ( optionalDomainID.isPresent() )
            {
                return optionalDomainID.get();
            }
            throw new IllegalStateException( "invalid domain" );
        }

        if ( pwmSetting.getCategory().getScope() == PwmSettingScope.SYSTEM )
        {
            return DomainID.systemId();
        }
        return DomainID.create( pwmRequest.getAppConfig().getDomainIDs().stream().findFirst().orElseThrow() );
    }

    public Set<DomainID> searchIDs()
            throws PwmUnrecoverableException
    {
        final DomainManageMode mode = getMode();
        if ( mode == DomainManageMode.single )
        {
            return Set.of(
                    DomainID.systemId(),
                    DomainID.create( pwmRequest.getAppConfig().getDomainIDs().stream().findFirst().orElseThrow() ) );
        }

        if ( mode == DomainManageMode.system )
        {
            return Set.of( DomainID.systemId() );
        }

        return Set.of( readDomainIdFromRequest().orElseThrow() );
    }

    private Optional<DomainID> readDomainIdFromRequest()
            throws PwmUnrecoverableException
    {
        final PwmURL pwmURL = pwmRequest.getURL();
        String postPath = pwmURL.getPostServletPath( PwmServletDefinition.ConfigEditor );

        while ( postPath.startsWith( "/" ) )
        {
            postPath = postPath.substring( 1 );
        }

        if ( DomainID.systemId().stringValue().equals( postPath ) )
        {
            return Optional.of( DomainID.systemId() );
        }

        if ( getAppConfig().getDomainIDs().contains( postPath ) )
        {
            return Optional.of( DomainID.create( postPath ) );
        }
        return Optional.empty();
    }
}
