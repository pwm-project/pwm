/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2014 The PWM Project
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

package password.pwm.util;

import java.io.Serializable;
import java.text.DecimalFormat;
import java.util.Date;

public class ProgressInfo implements Serializable {
    private final Date startTime;
    private final Date nowTime;
    private final long totalItems;
    private final long nowItems;

    public ProgressInfo(
            final Date startTime,
            final long totalItems,
            final long nowItems
    )
    {
        this.startTime = startTime;
        this.nowTime = new Date();
        this.totalItems = totalItems;
        this.nowItems = nowItems;
    }

    public float percentComplete() {
        return nowItems / totalItems;
    }

    public String formattedPercentComplete() {
        return DecimalFormat.getPercentInstance().format(percentComplete());
    }

    public TimeDuration elapsed() {
        return new TimeDuration(startTime, nowTime);
    }

    public long itemsRemaining() {
        return totalItems - nowItems;
    }

    public TimeDuration remainingDuration() {
        final long lps = elapsed().getTotalSeconds() <= 0 ? 0 : totalItems / elapsed().getTotalSeconds();
        final long linesRemaining = totalItems - nowItems;
        final long msRemaining = lps <= 0 ? 0 : (linesRemaining / lps) * 1000;
        return new TimeDuration(msRemaining);
    }

    public String debugOutput() {
        return "processed " + nowItems + "/" + totalItems + ", "
                + formattedPercentComplete() + " remaining (" + itemsRemaining() + "/" + remainingDuration().asCompactString() + ")";
    }
}
