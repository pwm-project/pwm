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

package password.pwm.svc;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import password.pwm.PwmApplication;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.error.PwmException;
import password.pwm.health.HealthRecord;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An interface for daemon/background services.  Services are initialized, shutdown and accessed via {@link PwmApplication}.  Some services
 * will have associated background threads, so implementations will generally be thread safe.
 */
public interface PwmService
{
    enum STATUS
    {
        OPEN,
        CLOSED,
    }

    STATUS status( );

    void init( PwmApplication pwmApplication ) throws PwmException;

    void close( );

    List<HealthRecord> healthCheck( );

    ServiceInfoBean serviceInfo( );

    interface ServiceInfo
    {
        Collection<DataStorageMethod> getStorageMethods( );

        Map<String, String> getDebugProperties( );
    }

    @Value
    @Builder
    class ServiceInfoBean implements ServiceInfo, Serializable
    {
        @Singular
        private final Set<DataStorageMethod> storageMethods;

        @Singular
        private final Map<String, String> debugProperties;
    }
}
