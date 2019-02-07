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

package password.pwm.svc.event;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public enum CEFExtension
{
    cat( AuditField.type ),
    act( AuditField.eventCode ),
    rt( AuditField.timestamp ),
    msg( AuditField.message ),
    reason ( AuditField.narrative ),
    suid( AuditField.perpetratorID ),
    suser( AuditField.perpetratorDN ),
    src ( AuditField.sourceAddress ),
    srchost( AuditField.sourceHost ),
    duid( AuditField.targetID ),
    duser( AuditField.targetDN ),
    dvchost( null ),
    dtz( null ),;

    private final AuditField auditField;

    public static final String CEF_EXTENSION_SEPARATOR = "|";

    public static final Map<String, String> CEF_VALUE_ESCAPES;

    static
    {
        final Map<String, String> map = new LinkedHashMap<>( );
        map.put( "\\", "\\\\" );
        map.put( "=", "\\=" );
        map.put( "|", "\"" );
        CEF_VALUE_ESCAPES = Collections.unmodifiableMap( map );
    }

    CEFExtension( final AuditField auditField )
    {
        this.auditField = auditField;
    }

    public AuditField getAuditField()
    {
        return auditField;
    }
}
