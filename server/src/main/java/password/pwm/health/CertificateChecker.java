/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2019 The PWM Project
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

package password.pwm.health;

import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingSyntax;
import password.pwm.config.profile.LdapProfile;
import password.pwm.config.stored.StoredConfigItemKey;
import password.pwm.config.stored.StoredConfiguration;
import password.pwm.config.value.ActionValue;
import password.pwm.config.value.data.ActionConfiguration;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class CertificateChecker implements HealthChecker
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( CertificateChecker.class );

    @Override
    public List<HealthRecord> doHealthCheck( final PwmApplication pwmApplication )
    {
        final List<HealthRecord> records = new ArrayList<>( doHealthCheck( pwmApplication.getConfig() ) );
        try
        {
            records.addAll( doActionHealthCheck( pwmApplication.getConfig() ) );
        }
        catch ( final PwmUnrecoverableException e )
        {
            LOGGER.error( () -> "error while checking action certificates: " + e.getMessage(), e );
        }
        return records;
    }

    private static List<HealthRecord> doHealthCheck( final Configuration configuration )
    {
        final List<HealthRecord> returnList = new ArrayList<>();
        for ( final PwmSetting setting : PwmSetting.values() )
        {
            if ( setting.getSyntax() == PwmSettingSyntax.X509CERT && !setting.getCategory().hasProfiles() )
            {
                if ( setting != PwmSetting.LDAP_SERVER_CERTS )
                {
                    final List<X509Certificate> certs = configuration.readSettingAsCertificate( setting );
                    returnList.addAll( doHealthCheck( configuration, setting, null, certs ) );
                }
            }
        }
        for ( final LdapProfile ldapProfile : configuration.getLdapProfiles().values() )
        {
            final List<X509Certificate> certificates = configuration.getLdapProfiles().get( ldapProfile.getIdentifier() ).readSettingAsCertificate( PwmSetting.LDAP_SERVER_CERTS );
            returnList.addAll( doHealthCheck( configuration, PwmSetting.LDAP_SERVER_CERTS, ldapProfile.getIdentifier(), certificates ) );
        }
        return Collections.unmodifiableList( returnList );
    }

    private static List<HealthRecord> doActionHealthCheck( final Configuration configuration ) throws PwmUnrecoverableException
    {

        final StoredConfiguration storedConfiguration = configuration.getStoredConfiguration();

        final List<HealthRecord> returnList = new ArrayList<>();
        final Set<StoredConfigItemKey> modifiedReferences = storedConfiguration.modifiedItems();
        for ( final StoredConfigItemKey storedConfigItemKey : modifiedReferences )
        {
            if ( storedConfigItemKey.getRecordType() == StoredConfigItemKey.RecordType.SETTING )
            {
                final PwmSetting pwmSetting = PwmSetting.forKey( storedConfigItemKey.getRecordID() );
                if ( pwmSetting != null && pwmSetting.getSyntax() == PwmSettingSyntax.ACTION )
                {
                    final ActionValue value = ( ActionValue ) storedConfiguration.readSetting( pwmSetting, storedConfigItemKey.getProfileID() );
                    for ( final ActionConfiguration actionConfiguration : value.toNativeObject() )
                    {
                        for ( final ActionConfiguration.WebAction webAction : actionConfiguration.getWebActions()  )
                        {
                            final List<X509Certificate> certificates = webAction.getCertificates();
                            returnList.addAll( doHealthCheck( configuration, pwmSetting, storedConfigItemKey.getProfileID(), certificates ) );
                        }
                    }
                }
            }
        }
        return Collections.unmodifiableList( returnList );
    }

    private static List<HealthRecord> doHealthCheck(
            final Configuration configuration,
            final PwmSetting setting,
            final String profileID,
            final List<X509Certificate> certificates
    )
    {
        final long warnDurationMs = 1000 * Long.parseLong( configuration.readAppProperty( AppProperty.HEALTH_CERTIFICATE_WARN_SECONDS ) );

        if ( certificates != null )
        {
            final List<HealthRecord> returnList = new ArrayList<>();
            for ( final X509Certificate certificate : certificates )
            {
                try
                {
                    checkCertificate( certificate, warnDurationMs );
                    return Collections.emptyList();
                }
                catch ( final PwmOperationalException e )
                {
                    final String errorDetail = e.getErrorInformation().getDetailedErrorMsg();
                    final HealthRecord record = HealthRecord.forMessage( HealthMessage.Config_Certificate,
                            setting.toMenuLocationDebug( profileID, PwmConstants.DEFAULT_LOCALE ),
                            errorDetail
                    );
                    return Collections.singletonList( record );
                }
            }
            return returnList;
        }
        return Collections.emptyList();
    }

    public static void checkCertificate( final X509Certificate certificate, final long warnDurationMs )
            throws PwmOperationalException
    {
        if ( certificate == null )
        {
            return;
        }

        try
        {
            certificate.checkValidity();
        }
        catch ( final CertificateException e )
        {
            final StringBuilder errorMsg = new StringBuilder();
            errorMsg.append( "certificate for subject " );
            errorMsg.append( certificate.getSubjectDN().getName() );
            errorMsg.append( " is not valid: " );
            errorMsg.append( e.getMessage() );
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_CERTIFICATE_ERROR, errorMsg.toString(), new String[]
                    {
                            errorMsg.toString(),
                    }
            );
            throw new PwmOperationalException( errorInformation );
        }

        final Instant expireDate = certificate.getNotAfter().toInstant();
        final TimeDuration durationUntilExpire = TimeDuration.fromCurrent( expireDate );
        if ( durationUntilExpire.isShorterThan( warnDurationMs ) )
        {
            final StringBuilder errorMsg = new StringBuilder();
            errorMsg.append( "certificate for subject " );
            errorMsg.append( certificate.getSubjectDN().getName() );
            errorMsg.append( " will expire on: " );
            errorMsg.append( JavaHelper.toIsoDate( expireDate ) );
            errorMsg.append( " (" ).append( durationUntilExpire.asCompactString() ).append( " from now)" );
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_CERTIFICATE_ERROR, errorMsg.toString(), new String[]
                    {
                            errorMsg.toString(),
                    }
            );
            throw new PwmOperationalException( errorInformation );
        }
    }
}
