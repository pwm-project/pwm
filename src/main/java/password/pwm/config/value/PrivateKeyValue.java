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

package password.pwm.config.value;

import org.jdom2.Element;
import password.pwm.bean.PrivateKeyCertificate;
import password.pwm.config.PwmSetting;
import password.pwm.config.StoredValue;
import password.pwm.util.JsonUtil;
import password.pwm.util.StringUtil;
import password.pwm.util.X509Utils;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmBlockAlgorithm;
import password.pwm.util.secure.PwmSecurityKey;
import password.pwm.util.secure.SecureEngine;

import java.io.Serializable;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.*;

public class PrivateKeyValue extends AbstractValue {
    private static final PwmLogger LOGGER = PwmLogger.forClass(PrivateKeyValue.class);

    private static final String ELEMENT_NAME_CERTIFICATE = "certificate";
    private static final String ELEMENT_NAME_KEY = "key";

    private PrivateKeyCertificate privateKeyCertificate;

    public static StoredValue.StoredValueFactory factory() {
        return new StoredValue.StoredValueFactory() {
            public PrivateKeyValue fromXmlElement(final Element settingElement, final PwmSecurityKey key) {
                if (settingElement != null && settingElement.getChild("value") != null) {

                    final Element valueElement = settingElement.getChild("value");
                    if (valueElement != null) {
                        final List<X509Certificate> certificates = new ArrayList<>();
                        for (final Element certificateElement : valueElement.getChildren(ELEMENT_NAME_CERTIFICATE)) {
                            try {
                                final String b64Text = certificateElement.getText();
                                final X509Certificate cert = X509Utils.certificateFromBase64(b64Text);
                                certificates.add(cert);
                            } catch (Exception e) {
                                LOGGER.error("error reading certificate: " + e.getMessage(),e);
                            }

                        }


                        PrivateKey privateKey = null;
                        try {
                            final Element keyElement = valueElement.getChild(ELEMENT_NAME_KEY);
                            final String encryptedText = keyElement.getText();
                            final String decryptedText = SecureEngine.decryptStringValue(encryptedText, key, PwmBlockAlgorithm.CONFIG);
                            final byte[] privateKeyBytes = StringUtil.base64Decode(decryptedText);
                            privateKey =  KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(privateKeyBytes));
                        } catch (Exception e) {
                            LOGGER.error("error reading privateKey: " + e.getMessage(),e);
                        }

                        if (!certificates.isEmpty() && privateKey != null) {
                            final X509Certificate[] certs = certificates.toArray(new X509Certificate[certificates.size()]);
                            final PrivateKeyCertificate privateKeyCertificate = new PrivateKeyCertificate(certs, privateKey);
                            return new PrivateKeyValue(privateKeyCertificate);
                        }
                    }
                }
                return new PrivateKeyValue(null);
            }

            public X509CertificateValue fromJson(final String input) {
                return new X509CertificateValue(new X509Certificate[0]);
            }
        };
    }

    public PrivateKeyValue(PrivateKeyCertificate privateKeyCertificate) {
        this.privateKeyCertificate = privateKeyCertificate;
    }


    public List<Element> toXmlValues(final String valueElementName) {
        throw new IllegalStateException("password xml output requires hash key");
    }

    @Override
    public Object toNativeObject()
    {
        return privateKeyCertificate;
    }

    @Override
    public List<String> validateValue(PwmSetting pwm)
    {
        return Collections.emptyList();
    }

    @Override
    public int currentSyntaxVersion()
    {
        return 0;
    }

    public List<Element> toXmlValues(final String valueElementName, final PwmSecurityKey key) {
        final Element valueElement = new Element("value");
        if (privateKeyCertificate != null) {
            try {
                {
                    for (final X509Certificate certificate : privateKeyCertificate.getCertificates()) {
                        final Element certificateElement = new Element(ELEMENT_NAME_CERTIFICATE);
                        certificateElement.setText(X509Utils.certificateToBase64(certificate));
                        valueElement.addContent(certificateElement);
                    }
                }
                {
                    final Element keyElement = new Element(ELEMENT_NAME_KEY);
                    final String b64EncodedKey = StringUtil.base64Encode(privateKeyCertificate.getKey().getEncoded());
                    final String encryptedKey = SecureEngine.encryptToString(b64EncodedKey, key, PwmBlockAlgorithm.CONFIG);
                    keyElement.setText(encryptedKey);
                    valueElement.addContent(keyElement);
                }
            } catch (Exception e) {
                valueElement.addContent("");
                throw new RuntimeException("missing required AES and SHA1 libraries, or other crypto fault: " + e.getMessage());
            }
        }
        return Collections.singletonList(valueElement);
    }

    public String toDebugString(Locale locale) {
        if (privateKeyCertificate != null) {
            return "PrivateKeyCertificate: key=" + JsonUtil.serializeMap(X509Utils.makeDebugInfoMap(privateKeyCertificate.getKey()))
                    + ", certificates=" + JsonUtil.serializeCollection(X509Utils.makeDebugInfoMap(privateKeyCertificate.getCertificates()));
        }
        return "";
    }

    public Map<String,Object> toInfoMap(final boolean includeDetail) {
        if (privateKeyCertificate == null) {
            return null;
        }
        final Map<String,Object> returnMap = new LinkedHashMap<>();
        returnMap.put("certificates", X509Utils.makeDebugInfoMap(privateKeyCertificate.getCertificates(), X509Utils.DebugInfoFlag.IncludeCertificateDetail));
        final Map<String,Object> privateKeyInfo = new LinkedHashMap<>();
        privateKeyInfo.put("algorithm", privateKeyCertificate.getKey().getAlgorithm());
        privateKeyInfo.put("format", privateKeyCertificate.getKey().getFormat());
        returnMap.put("key", privateKeyInfo);
        return returnMap;
    }

    @Override
    public Serializable toDebugJsonObject(Locale locale) {
        return (Serializable)toInfoMap(false);
    }
}
