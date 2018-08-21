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

package password.pwm.svc.wordlist;

import lombok.Builder;
import lombok.Getter;

import java.io.Serializable;
import java.time.Instant;

@Getter
@Builder
public class StoredWordlistDataBean implements Serializable
{
    private boolean completed;
    private Source source;
    private Instant storeDate;
    private String sha1hash;
    private int size;

    public enum Source
    {
        BuiltIn( "Built-In" ),
        AutoImport( "Import from configured URL" ),
        User( "Uploaded" ),;

        private final String label;

        Source( final String label )
        {
            this.label = label;
        }

        public String getLabel( )
        {
            return label;
        }
    }
}
