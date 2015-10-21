/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2015 The PWM Project
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

package password.pwm.svc.intruder;

import password.pwm.error.PwmOperationalException;
import password.pwm.util.ClosableIterator;

public interface RecordManager {
    public boolean checkSubject(final String subject);
    public void markSubject(final String subject);
    public void clearSubject(final String subject);
    public boolean isAlerted(final String subject);
    public void markAlerted(final String subject);
    public IntruderRecord readIntruderRecord(final String subject);
    public ClosableIterator<IntruderRecord> iterator() throws PwmOperationalException;
}
