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

package password.pwm.util.secure.self;

import password.pwm.AppProperty;
import password.pwm.PwmConstants;
import password.pwm.config.AppConfig;
import password.pwm.config.PwmSetting;
import password.pwm.util.logging.PwmLogger;

import java.net.URI;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

public record SelfCertSettings(
        String subjectAlternateName,
        int keySize,
        String keyAlg,
        Duration futureSeconds,
        String siteUrl
)
{
    public SelfCertSettings(
            final String subjectAlternateName,
            final int keySize,
            final String keyAlg,
            final Duration futureSeconds,
            final String siteUrl
    )
    {
        this.subjectAlternateName = Objects.requireNonNull( subjectAlternateName );
        this.keySize = keySize;
        this.keyAlg = Objects.requireNonNull( keyAlg );
        this.futureSeconds = Objects.requireNonNull( futureSeconds );
        this.siteUrl = URI.create( siteUrl ).toString();
    }

    private static final URI EXAMPLE_URI = URI.create( "https://" + PwmConstants.PWM_APP_NAME.toLowerCase() + ".example.com" );
    private static final SelfCertSettings EXAMPLE = new SelfCertSettings(
            EXAMPLE_URI.getHost(),
            1024,
            "RSA",
            Duration.of( 30, ChronoUnit.DAYS ),
            EXAMPLE_URI.toString() );


    public static SelfCertSettings example()
    {
        return EXAMPLE;
    }

    public static SelfCertSettings fromConfiguration ( final AppConfig config )
    {
        final URI configuredSiteUrl = calculateConfiguredSiteUrl( config );
        final Duration futureSeconds = Duration.of(
                Long.parseLong( config.readAppProperty( AppProperty.SECURITY_HTTPSSERVER_SELF_FUTURESECONDS ) ),
                ChronoUnit.SECONDS );

        return new SelfCertSettings(
                configuredSiteUrl.getHost(),
                Integer.parseInt( config.readAppProperty( AppProperty.SECURITY_HTTPSSERVER_SELF_KEY_SIZE )  ),
                config.readAppProperty( AppProperty.SECURITY_HTTPSSERVER_SELF_ALG ),
                futureSeconds,
                configuredSiteUrl.toString() );

    }

    public static URI calculateConfiguredSiteUrl( final AppConfig config )
    {
        final String configuredSiteUrl = config.readSettingAsString( PwmSetting.PWM_SITE_URL );

        try
        {
            return URI.create( configuredSiteUrl );
        }
        catch ( final Exception e )
        {
            PwmLogger.forClass( SelfCertSettings.class ).warn( () -> "configured site URL ("
                    + PwmSetting.PWM_SITE_URL.toMenuLocationDebug( null, PwmConstants.DEFAULT_LOCALE )
                    + ") is not a valid url: " + e.getMessage() );
        }

        return EXAMPLE_URI;
    }
}
