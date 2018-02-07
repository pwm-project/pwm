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

import java.io.Serializable;
import java.util.List;

@Getter
public class DisplayElement implements Serializable
{
    private String key;
    private Type type;
    private String label;
    private String value;
    private List<String> values;

    public enum Type
    {
        string,
        timestamp,
        number,
        multiString,
    }

    public DisplayElement( final String key, final Type type, final String label, final String value )
    {
        this.key = key;
        this.type = type;
        this.label = label;
        this.value = value;
    }

    public DisplayElement( final String key, final Type type, final String label, final List<String> values )
    {
        this.key = key;
        this.type = type;
        this.label = label;
        this.values = values;
    }
}
