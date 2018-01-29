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

package password.pwm.bean;

import lombok.Builder;
import lombok.Getter;
import password.pwm.ldap.PwmLdapVendor;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Getter
@Builder
public class TelemetryPublishBean implements Serializable {
    private final Instant timestamp;
    private final String id;
    private final String instanceHash;
    private final String siteDescription;
    private final Instant installTime;
    private final List<PwmLdapVendor> ldapVendor;
    private final Map<String,String> statistics;
    private final List<String> configuredSettings;
    private final String versionBuild;
    private final String versionVersion;
    private final Map<String,String> about;
}
