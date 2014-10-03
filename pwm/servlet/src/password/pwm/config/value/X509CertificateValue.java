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

package password.pwm.config.value;

import com.google.gson.GsonBuilder;
import org.jdom2.Element;
import password.pwm.config.PwmSetting;
import password.pwm.config.StoredValue;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.JsonUtil;
import password.pwm.util.SecureHelper;
import password.pwm.util.StringUtil;
import password.pwm.util.X509Utils;
import password.pwm.util.logging.PwmLogger;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.*;

public class X509CertificateValue extends AbstractValue implements StoredValue {
    private static final PwmLogger LOGGER = PwmLogger.forClass(X509CertificateValue.class);
    private X509Certificate[] certificates;

    public X509CertificateValue(X509Certificate[] certificates) {
        if (certificates == null) {
            throw new NullPointerException("certificates cannot be null");
        }
        this.certificates = certificates;
    }

    public boolean hasCertificates() {
        return certificates != null && certificates.length > 0;
    }

    public X509CertificateValue(Collection<X509Certificate> certificates) {
        if (certificates == null) {
            throw new NullPointerException("certificates cannot be null");
        }
        this.certificates = certificates.toArray(new X509Certificate[certificates.size()]);
    }

    static X509CertificateValue fromJson(final String input) {
        return new X509CertificateValue(new X509Certificate[0]);
    }

    static X509CertificateValue fromXmlElement(final Element settingElement) {
        final List<X509Certificate> certificates = new ArrayList<>();
        final List<Element> valueElements = settingElement.getChildren("value");
        for (final Element loopValueElement : valueElements) {
            final String b64encodedStr = loopValueElement.getText();
            try {
                final CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
                final byte[] certByteValue = StringUtil.base64Decode(b64encodedStr);
                final X509Certificate certificate = (X509Certificate)certificateFactory.generateCertificate(new ByteArrayInputStream(certByteValue));
                certificates.add(certificate);
            } catch (Exception e) {
                LOGGER.error("error decoding certificate: " + e.getMessage());
            }
        }
        return new X509CertificateValue(certificates.toArray(new X509Certificate[certificates.size()]));
    }

    @Override
    public List<Element> toXmlValues(String valueElementName) {
        final List<Element> returnList = new ArrayList<>();
        for (final X509Certificate value : certificates) {
            final Element valueElement = new Element(valueElementName);
            try {
                valueElement.addContent(StringUtil.base64Encode(value.getEncoded()));
            } catch (CertificateEncodingException e) {
                LOGGER.error("error encoding certificate: " + e.getMessage());
            }
            returnList.add(valueElement);
        }
        return returnList;
    }

    @Override
    public Object toNativeObject() {
        return certificates;
    }

    @Override
    public List<String> validateValue(PwmSetting pwm) {
        return Collections.emptyList();
    }

    public String toDebugString(boolean prettyFormat, Locale locale) {
        final List<Map<String,String>> list = new ArrayList<>();
        for (X509Certificate cert : certificates) {
            final LinkedHashMap<String,String> map = new LinkedHashMap<>();
            map.put("subject",cert.getSubjectDN().toString());
            map.put("serial", X509Utils.hexSerial(cert));
            map.put("issuer",cert.getIssuerDN().toString());
            map.put("issueDate",cert.getNotBefore().toString());
            map.put("expireDate",cert.getNotAfter().toString());
            try {
                map.put("md5Hash", SecureHelper.hash(new ByteArrayInputStream(cert.getEncoded()),
                        SecureHelper.HashAlgorithm.MD5));
                map.put("sha1Hash", SecureHelper.hash(new ByteArrayInputStream(cert.getEncoded()),
                        SecureHelper.HashAlgorithm.SHA1));
            } catch (PwmUnrecoverableException | CertificateEncodingException e) {
                LOGGER.warn("error generating hash for certificate: " + e.getMessage());
            }
            list.add(map);
        }
        return prettyFormat
                ? JsonUtil.getGson(new GsonBuilder().disableHtmlEscaping().setPrettyPrinting()).toJson(list)
                : JsonUtil.serializeCollection(list);
    }
}
