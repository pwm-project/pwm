/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2021 The PWM Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package password.pwm.util.secure;

import password.pwm.PwmConstants;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.java.EnumUtil;
import password.pwm.util.java.StringUtil;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public enum X509CertInfo
{
    subject,
    subjectAlternateNames,
    publicKeyType,
    serial,
    issuer,
    issueDate,
    expireDate,
    md5Hash,
    sha1Hash,
    sha256Hash,
    sha512Hash,
    isKeySigning,
    detail,;

    public static List<Map<String, String>> makeDebugInfoMap( final List<X509Certificate> certificates, final X509Utils.DebugInfoFlag... flags )
    {
        final List<Map<String, String>> returnList = new ArrayList<>();
        if ( certificates != null )
        {
            for ( final X509Certificate cert : certificates )
            {
                returnList.add( makeDebugInfoMap( cert, flags ) );
            }
        }
        return Collections.unmodifiableList( returnList );
    }

    public static Map<String, String> makeDebugInfoMap( final X509Certificate cert, final X509Utils.DebugInfoFlag... flags )
    {
        if ( EnumUtil.enumArrayContainsValue( flags, X509Utils.DebugInfoFlag.IncludeCertificateDetail ) )
        {
            final Map<X509CertInfo, String> infoMap = new EnumMap<>( X509CertInfo.class );
            infoMap.putAll( makeDebugInfoMapImpl( cert ) );
            infoMap.put( detail, X509Utils.makeDetailText( cert ) );
            return CollectionUtil.enumMapToStringMap( infoMap );
        }

        return CollectionUtil.enumMapToStringMap( makeDebugInfoMapImpl( cert ) );
    }

    static Map<X509CertInfo, String> makeDebugInfoMapImpl( final X509Certificate cert  )
    {

        final Map<X509CertInfo, String> returnMap = new EnumMap<>( X509CertInfo.class );

        returnMap.put( serial, X509Utils.hexSerial( cert ) );
        returnMap.put( issueDate, StringUtil.toIsoDate( cert.getNotBefore().toInstant() ) );
        returnMap.put( expireDate, StringUtil.toIsoDate( cert.getNotAfter().toInstant() ) );
        returnMap.put( md5Hash, X509Utils.hash( cert, PwmHashAlgorithm.MD5 ) );
        returnMap.put( sha1Hash, X509Utils.hash( cert, PwmHashAlgorithm.SHA1 ) );
        returnMap.put( sha256Hash, X509Utils.hash( cert, PwmHashAlgorithm.SHA256 ) );
        returnMap.put( sha512Hash, X509Utils.hash( cert, PwmHashAlgorithm.SHA512 ) );
        returnMap.put( isKeySigning, LocaleHelper.valueBoolean( PwmConstants.DEFAULT_LOCALE, X509CertDataParser.certIsSigningKey( cert ) ) );

        X509CertDataParser.readCertSubject( cert ).ifPresent( s -> returnMap.put( subject, s ) );
        X509CertDataParser.readCertIssuer( cert ).ifPresent( s -> returnMap.put( issuer, s ) );

        X509CertDataParser.readCertPublicKeyInfo( cert ).ifPresent( s -> returnMap.put( publicKeyType, s ) );

        X509CertDataParser.readCertSubjectAlternativeNames( cert ).ifPresent( s -> returnMap.put( subjectAlternateNames, s ) );

        return Collections.unmodifiableMap( returnMap );
    }

    public static Map<String, String> makeDebugInfoMap( final PrivateKey key )
    {
        final Map<String, String> returnMap = new LinkedHashMap<>();
        returnMap.put( X509Utils.KeyDebugInfoKey.algorithm.toString(), key.getAlgorithm() );
        returnMap.put( X509Utils.KeyDebugInfoKey.format.toString(), key.getFormat() );
        return returnMap;
    }
}
