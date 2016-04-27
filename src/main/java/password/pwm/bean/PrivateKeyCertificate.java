/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2016 The PWM Project
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

import java.io.Serializable;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

public class PrivateKeyCertificate implements Serializable {
    final private X509Certificate[] certificates;
    final private PrivateKey key;

    public PrivateKeyCertificate(X509Certificate certificates[], PrivateKey key) {
        this.certificates = certificates;
        this.key = key;
    }

    public X509Certificate[] getCertificates() {
        return certificates;
    }

    public PrivateKey getKey() {
        return key;
    }
}
