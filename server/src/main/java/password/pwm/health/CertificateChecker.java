/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2020 The PWM Project
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
import password.pwm.config.AppConfig;
import password.pwm.config.PwmSettingSyntax;
import password.pwm.config.stored.StoredConfigKey;
import password.pwm.config.stored.StoredConfiguration;
import password.pwm.config.value.StoredValue;
import password.pwm.config.value.ValueTypeConverter;
import password.pwm.config.value.data.ActionConfiguration;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.X509Utils;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

public class CertificateChecker implements HealthSupplier
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( CertificateChecker.class );

    @Override
    public List<Supplier<List<HealthRecord>>> jobs( final HealthSupplierRequest request )
    {
        final PwmApplication pwmApplication = request.getPwmApplication();
        return Collections.singletonList( new CertificateCheckJob( pwmApplication.getConfig() ) );
    }

    public static class CertificateCheckJob implements Supplier<List<HealthRecord>>
    {
        private final AppConfig appConfig;

        public CertificateCheckJob( final AppConfig appConfig )
        {
            this.appConfig = appConfig;
        }

        @Override
        public List<HealthRecord> get()
        {
            return checkImpl( appConfig );
        }
    }

    private static List<HealthRecord> checkImpl( final AppConfig appConfig )
    {
        final TimeDuration warnDuration = TimeDuration.of(
                Long.parseLong( appConfig.readAppProperty( AppProperty.HEALTH_CERTIFICATE_WARN_SECONDS ) ),
                TimeDuration.Unit.SECONDS );

        final List<HealthRecord> records = new ArrayList<>();
        CollectionUtil.iteratorToStream( appConfig.getStoredConfiguration().keys() )
                .filter( k -> k.isRecordType( StoredConfigKey.RecordType.SETTING ) )
                .forEach( k ->
                {
                    final PwmSettingSyntax syntax = k.getSyntax();
                    if ( syntax == PwmSettingSyntax.X509CERT )
                    {
                        records.addAll( checkX509Setting( appConfig.getStoredConfiguration(), k, warnDuration ) );
                    }
                    else if ( syntax == PwmSettingSyntax.ACTION )
                    {
                        records.addAll( checkActionSetting( appConfig.getStoredConfiguration(), k, warnDuration ) );
                    }
                } );
        return Collections.unmodifiableList( records );
    }

    private static List<HealthRecord> checkX509Setting(
            final StoredConfiguration storedConfiguration,
            final StoredConfigKey key,
            final TimeDuration timeDuration
    )
    {
        final StoredValue storedValue = storedConfiguration.readStoredValue( key ).orElseThrow();
        final List<X509Certificate> certs = ValueTypeConverter.valueToX509Certificates( key.toPwmSetting(), storedValue );
        return doHealthCheck( key, certs, timeDuration );
    }

    private static List<HealthRecord> checkActionSetting(
            final StoredConfiguration storedConfiguration,
            final StoredConfigKey key,
            final TimeDuration timeDuration
    )
    {
        final StoredValue storedValue = storedConfiguration.readStoredValue( key ).orElseThrow();
        final List<ActionConfiguration> actionConfigurations = ValueTypeConverter.valueToAction( key.toPwmSetting(), storedValue );
        final List<HealthRecord> returnList = new ArrayList<>();
        for ( final ActionConfiguration actionConfiguration : actionConfigurations )
        {
            for ( final ActionConfiguration.WebAction webAction : actionConfiguration.getWebActions() )
            {
                final List<X509Certificate> certificates = webAction.getCertificates();
                returnList.addAll( doHealthCheck( key, certificates, timeDuration ) );
            }
        }
        return Collections.unmodifiableList( returnList );
    }


    private static List<HealthRecord> doHealthCheck(
            final StoredConfigKey storedConfigKey,
            final List<X509Certificate> certificates,
            final TimeDuration warnDuration
    )
    {
        if ( certificates != null )
        {
            final List<HealthRecord> returnList = new ArrayList<>();
            for ( final X509Certificate certificate : certificates )
            {
                try
                {
                    checkCertificate( certificate, warnDuration );
                    return Collections.emptyList();
                }
                catch ( final PwmOperationalException e )
                {
                    final String errorDetail = e.getErrorInformation().getDetailedErrorMsg();
                    final HealthRecord record = HealthRecord.forMessage(
                            storedConfigKey.getDomainID(),
                            HealthMessage.Config_Certificate,
                            storedConfigKey.toPwmSetting().toMenuLocationDebug( storedConfigKey.getProfileID(), PwmConstants.DEFAULT_LOCALE ),
                            errorDetail
                    );
                    returnList.add( record );
                }
            }
            return Collections.unmodifiableList( returnList );
        }
        return Collections.emptyList();
    }

    public static void checkCertificate( final X509Certificate certificate, final TimeDuration warnDuration )
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

        {
            final Instant issueDate = certificate.getNotBefore().toInstant();
            if ( issueDate.isAfter( Instant.now() ) )
            {
                final String errorMsg = "certificate " + X509Utils.makeDebugText( certificate )
                        + " issue date of '" + JavaHelper.toIsoDate( issueDate ) + "' "
                        + " is prior to current time.";

                final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_CERTIFICATE_ERROR, errorMsg, new String[]
                        {
                                errorMsg,
                        }
                );
                throw new PwmOperationalException( errorInformation );
            }
        }

        {
            final Instant expireDate = certificate.getNotAfter().toInstant();
            final TimeDuration durationUntilExpire = TimeDuration.fromCurrent( expireDate );
            if ( durationUntilExpire.isShorterThan( warnDuration ) )
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
}
