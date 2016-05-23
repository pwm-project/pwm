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

package password.pwm.http.client;

import java.security.cert.X509Certificate;

public class PwmHttpClientConfiguration {
    private X509Certificate[] certificates;
    private boolean promiscuous;

    private PwmHttpClientConfiguration(X509Certificate[] certificate, boolean promiscuous) {
        this.certificates = certificate;
        this.promiscuous = promiscuous;
    }

    public X509Certificate[] getCertificates() {
        return certificates;
    }

    public boolean isPromiscuous() {
        return promiscuous;
    }

    public static class Builder {
        private X509Certificate[] certificate;
        private boolean promiscuous;

        public Builder setCertificate(X509Certificate[] certificate) {
            this.certificate = certificate;
            return this;
        }

        public Builder setPromiscuous(boolean promiscuous) {
            this.promiscuous = promiscuous;
            return this;
        }

        public PwmHttpClientConfiguration create() {
            return new PwmHttpClientConfiguration(certificate, promiscuous);
        }
    }
}
