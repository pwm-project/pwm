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

package password.pwm.http.bean;

import lombok.Getter;
import lombok.Setter;
import password.pwm.config.option.SessionBeanMode;
import password.pwm.error.ErrorInformation;

import java.io.Serializable;
import java.time.Instant;
import java.util.Set;

@Getter
@Setter
public abstract class PwmSessionBean implements Serializable
{
    public enum Type
    {
        PUBLIC,
        AUTHENTICATED,
    }

    private String guid;
    private Instant timestamp;
    private ErrorInformation lastError;

    public abstract Type getType( );

    public abstract Set<SessionBeanMode> supportedModes( );
}
