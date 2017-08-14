/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2017 The PWM Project
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

package password.pwm.svc;

import lombok.AllArgsConstructor;
import lombok.Getter;
import password.pwm.PwmApplication;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.error.PwmException;
import password.pwm.health.HealthRecord;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * An interface for daemon/background services.  Services are initialized, shutdown and accessed via {@link PwmApplication}.  Some services
 * will have associated background threads, so implementations will generally be thread safe.
 */
public interface PwmService {

    enum STATUS {
        NEW,
        OPENING,
        OPEN,
        CLOSED
    }

    STATUS status();

    void init(PwmApplication pwmApplication) throws PwmException;
    
    void close();

    List<HealthRecord> healthCheck();

    ServiceInfoBean serviceInfo();

    interface ServiceInfo {
        Collection<DataStorageMethod> getUsedStorageMethods();

        Map<String,String> getDebugProperties();
    }

    @Getter
    @AllArgsConstructor
    class ServiceInfoBean implements ServiceInfo, Serializable {
        private final Collection<DataStorageMethod> usedStorageMethods;
        private final Map<String,String> debugProperties;

        public ServiceInfoBean(final Collection<DataStorageMethod> usedStorageMethods)
        {
            this.usedStorageMethods = usedStorageMethods;
            this.debugProperties = Collections.emptyMap();
        }
    }
}
