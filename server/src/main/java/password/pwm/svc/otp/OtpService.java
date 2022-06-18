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

package password.pwm.svc.otp;

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import lombok.Getter;
import org.apache.commons.codec.binary.Base32;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.PwmDomain;
import password.pwm.bean.DomainID;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.DomainConfig;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.config.option.OTPStorageFormat;
import password.pwm.config.profile.SetupOtpProfile;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthRecord;
import password.pwm.http.PwmRequest;
import password.pwm.ldap.LdapOperationsHelper;
import password.pwm.svc.AbstractPwmService;
import password.pwm.svc.PwmService;
import password.pwm.util.java.MiscUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroRequest;
import password.pwm.util.secure.PwmRandom;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.Serializable;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * @author Menno Pieters, Jason D. Rivard
 */
public class OtpService extends AbstractPwmService implements PwmService
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( OtpService.class );

    private final Map<DataStorageMethod, OtpOperator> operatorMap = new EnumMap<>( DataStorageMethod.class );
    private PwmDomain pwmDomain;
    private OtpSettings settings;

    public OtpService( )
    {
    }

    @Override
    public STATUS postAbstractInit( final PwmApplication pwmApplication, final DomainID domainID ) throws PwmException
    {
        this.pwmDomain = pwmApplication.domains().get( domainID );
        operatorMap.put( DataStorageMethod.LDAP, new LdapOtpOperator( pwmDomain ) );
        operatorMap.put( DataStorageMethod.LOCALDB, new LocalDbOtpOperator( pwmDomain ) );
        operatorMap.put( DataStorageMethod.DB, new DbOtpOperator( pwmDomain ) );
        settings = OtpSettings.fromConfig( pwmDomain.getConfig() );
        return STATUS.OPEN;
    }

    public boolean validateToken(
            final SessionLabel sessionLabel,
            final UserIdentity userIdentity,
            final OTPUserRecord otpUserRecord,
            final String userInput,
            final boolean allowRecoveryCodes
    )
            throws PwmOperationalException, PwmUnrecoverableException
    {
        boolean otpCorrect = false;
        try
        {
            final Base32 base32 = new Base32();
            final byte[] rawSecret = base32.decode( otpUserRecord.getSecret() );
            final Mac mac = Mac.getInstance( "HMACSHA1" );
            mac.init( new SecretKeySpec( rawSecret, "" ) );
            final PasscodeGenerator generator = new PasscodeGenerator( mac, settings.getOtpTokenLength(), settings.getTotpIntervalSeconds() );
            switch ( otpUserRecord.getType() )
            {
                case TOTP:
                    otpCorrect = generator.verifyTimeoutCode( userInput, settings.getTotpPastIntervals(), settings.getTotpFutureIntervals() );
                    break;

                //@todo HOTP implementation

                default:
                    throw new UnsupportedOperationException( "OTP type not supported: " + otpUserRecord.getType() );
            }
        }
        catch ( final Exception e )
        {
            LOGGER.error( sessionLabel, () -> "error checking otp secret: " + e.getMessage() );
        }

        if ( !otpCorrect && allowRecoveryCodes && otpUserRecord.getRecoveryCodes() != null && otpUserRecord.getRecoveryInfo() != null )
        {
            final OTPUserRecord.RecoveryInfo recoveryInfo = otpUserRecord.getRecoveryInfo();
            final String userHashedInput = doRecoveryHash( userInput, recoveryInfo );
            for ( final OTPUserRecord.RecoveryCode code : otpUserRecord.getRecoveryCodes() )
            {
                if ( code.getHashCode().equals( userInput ) || code.getHashCode().equals( userHashedInput ) )
                {
                    if ( code.isUsed() )
                    {
                        throw new PwmOperationalException( PwmError.ERROR_OTP_RECOVERY_USED,
                                "recovery code has been previously used" );
                    }

                    code.setUsed( true );
                    try
                    {
                        pwmDomain.getOtpService().writeOTPUserConfiguration( null, userIdentity, otpUserRecord );
                    }
                    catch ( final ChaiUnavailableException e )
                    {
                        throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_WRITING_OTP_SECRET, e.getMessage() ) );
                    }
                    otpCorrect = true;
                }
            }
        }

        return otpCorrect;
    }

    private List<String> createRawRecoveryCodes( final int numRecoveryCodes, final SessionLabel sessionLabel )
    {
        final MacroRequest macroRequest = MacroRequest.forNonUserSpecific( pwmDomain.getPwmApplication(), sessionLabel );
        final String configuredTokenMacro = settings.getRecoveryTokenMacro();
        final List<String> recoveryCodes = new ArrayList<>( numRecoveryCodes );
        while ( recoveryCodes.size() < numRecoveryCodes )
        {
            final String code = macroRequest.expandMacros( configuredTokenMacro );
            recoveryCodes.add( code );
        }
        return recoveryCodes;
    }

    public List<String> initializeUserRecord(
            final SetupOtpProfile otpProfile,
            final OTPUserRecord otpUserRecord,
            final SessionLabel sessionLabel,
            final String identifier
    )
            throws IOException, PwmUnrecoverableException
    {
        final PwmRandom pwmRandom = pwmDomain.getSecureService().pwmRandom();

        otpUserRecord.setIdentifier( identifier );

        final byte[] rawSecret = generateSecret();
        final String otpEncodedSecret = StringUtil.base32Encode( rawSecret );
        otpUserRecord.setSecret( otpEncodedSecret );

        switch ( settings.getOtpType() )
        {
            case HOTP:
                otpUserRecord.setAttemptCount( pwmRandom.nextLong() );
                otpUserRecord.setType( OTPUserRecord.Type.HOTP );
                break;

            case TOTP:
                otpUserRecord.setType( OTPUserRecord.Type.TOTP );
                break;

            default:
                MiscUtil.unhandledSwitchStatement( settings.getOtpType() );
        }

        final List<String> rawRecoveryCodes;
        if ( settings.getOtpStorageFormat().supportsRecoveryCodes() )
        {
            final int recoveryCodesCount = ( int ) otpProfile.readSettingAsLong( PwmSetting.OTP_RECOVERY_CODES );
            rawRecoveryCodes = createRawRecoveryCodes( recoveryCodesCount, sessionLabel );
            final OTPUserRecord.RecoveryInfo recoveryInfo = new OTPUserRecord.RecoveryInfo();
            if ( settings.getOtpStorageFormat().supportsHashedRecoveryCodes() )
            {
                LOGGER.trace( sessionLabel, () -> "hashing the recovery codes" );
                final int saltCharLength = Integer.parseInt( pwmDomain.getConfig().readAppProperty( AppProperty.OTP_SALT_CHARLENGTH ) );
                recoveryInfo.setSalt( pwmRandom.alphaNumericString( saltCharLength ) );
                recoveryInfo.setHashCount( settings.getRecoveryHashIterations() );
                recoveryInfo.setHashMethod( settings.getRecoveryHashMethod() );
            }
            else
            {
                LOGGER.trace( sessionLabel, () -> "not hashing the recovery codes" );
                recoveryInfo.setSalt( null );
                recoveryInfo.setHashCount( 0 );
                recoveryInfo.setHashMethod( null );
            }
            otpUserRecord.setRecoveryInfo( recoveryInfo );


            final List<OTPUserRecord.RecoveryCode> recoveryCodeList = new ArrayList<>( rawRecoveryCodes.size() );
            for ( final String rawCode : rawRecoveryCodes )
            {
                final String hashedCode;
                if ( settings.getOtpStorageFormat().supportsHashedRecoveryCodes() )
                {
                    hashedCode = doRecoveryHash( rawCode, recoveryInfo );
                }
                else
                {
                    hashedCode = rawCode;
                }
                final OTPUserRecord.RecoveryCode recoveryCode = new OTPUserRecord.RecoveryCode();
                recoveryCode.setHashCode( hashedCode );
                recoveryCode.setUsed( false );
                recoveryCodeList.add( recoveryCode );
            }
            otpUserRecord.setRecoveryCodes( recoveryCodeList );
        }
        else
        {
            rawRecoveryCodes = new ArrayList<>();
        }
        return rawRecoveryCodes;
    }

    private byte[] generateSecret( )
    {
        final PwmRandom pwmRandom = pwmDomain.getSecureService().pwmRandom();
        final byte[] secArray = new byte[ 10 ];
        pwmRandom.nextBytes( secArray );
        return secArray;
    }

    public String doRecoveryHash(
            final String input,
            final OTPUserRecord.RecoveryInfo recoveryInfo
    )
            throws IllegalStateException, PwmUnrecoverableException
    {
        final String algorithm = settings.getRecoveryHashMethod();
        final MessageDigest md;
        try
        {
            md = MessageDigest.getInstance( algorithm );
        }
        catch ( final NoSuchAlgorithmException e )
        {
            throw new IllegalStateException( "unable to load " + algorithm + " message digest algorithm: " + e.getMessage() );
        }

        final String raw = recoveryInfo.getSalt() == null
                ? input.trim()
                : recoveryInfo.getSalt().trim() + input.trim();

        final int hashCount = recoveryInfo.getHashCount();
        byte[] hashedBytes = raw.getBytes( PwmConstants.DEFAULT_CHARSET );
        for ( int i = 0; i < hashCount; i++ )
        {
            hashedBytes = md.digest( hashedBytes );
        }
        return StringUtil.base64Encode( hashedBytes );
    }

    @Override
    public void shutdownImpl( )
    {
        for ( final OtpOperator operator : operatorMap.values() )
        {
            operator.close();
        }
        operatorMap.clear();
    }

    @Override
    public List<HealthRecord> serviceHealthCheck( )
    {
        return Collections.emptyList();
    }

    public OTPUserRecord readOTPUserConfiguration(
            final SessionLabel sessionLabel,
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException, ChaiUnavailableException
    {
        OTPUserRecord otpConfig = null;
        final DomainConfig config = pwmDomain.getConfig();
        final Instant methodStartTime = Instant.now();

        final List<DataStorageMethod> otpSecretStorageLocations = config.readGenericStorageLocations(
                PwmSetting.OTP_SECRET_READ_PREFERENCE );

        if ( otpSecretStorageLocations != null )
        {
            final String userGUID = readGuidIfNeeded( pwmDomain, sessionLabel, otpSecretStorageLocations, userIdentity );
            final Iterator<DataStorageMethod> locationIterator = otpSecretStorageLocations.iterator();
            while ( otpConfig == null && locationIterator.hasNext() )
            {
                final DataStorageMethod location = locationIterator.next();
                final OtpOperator operator = operatorMap.get( location );
                if ( operator != null )
                {
                    try
                    {
                        otpConfig = operator.readOtpUserConfiguration( sessionLabel, userIdentity, userGUID ).orElse( null );
                    }
                    catch ( final Exception e )
                    {
                        LOGGER.error( sessionLabel, () -> "unexpected error reading stored otp configuration from "
                                + location + " for user " + userIdentity + ", error: " + e.getMessage() );
                    }
                }
                else
                {
                    LOGGER.warn( sessionLabel, () -> String.format( "storage location %s not implemented", location.toString() ) );
                }
            }
        }

        {
            final OTPUserRecord finalOtpConfig = otpConfig;
            final Supplier<CharSequence> msg = () -> finalOtpConfig == null
                    ? "no otp record found for user " + userIdentity.toDisplayString()
                    : "loaded otp record for user " + userIdentity.toDisplayString()
                    + " [recordType=" + finalOtpConfig.getType() + ", identifier=" + finalOtpConfig.getIdentifier() + ", timestamp="
                    + StringUtil.toIsoDate( finalOtpConfig.getTimestamp() ) + "]";
            LOGGER.trace( sessionLabel, msg, TimeDuration.fromCurrent(  methodStartTime ) );
        }

        return otpConfig;
    }

    public void writeOTPUserConfiguration(
            final PwmRequest pwmRequest,
            final UserIdentity userIdentity,
            final OTPUserRecord otp
    )
            throws PwmOperationalException, ChaiUnavailableException, PwmUnrecoverableException
    {
        int attempts = 0;
        int successes = 0;

        final DomainConfig config = pwmDomain.getConfig();
        final List<DataStorageMethod> otpSecretStorageLocations = config.readGenericStorageLocations(
                PwmSetting.OTP_SECRET_READ_PREFERENCE );
        final String userGUID = readGuidIfNeeded( pwmDomain, pwmRequest == null ? null : pwmRequest.getLabel(), otpSecretStorageLocations, userIdentity );

        final StringBuilder errorMsgs = new StringBuilder();
        if ( otpSecretStorageLocations != null )
        {
            for ( final DataStorageMethod otpSecretStorageLocation : otpSecretStorageLocations )
            {
                attempts++;
                final OtpOperator operator = operatorMap.get( otpSecretStorageLocation );
                if ( operator != null )
                {
                    try
                    {
                        operator.writeOtpUserConfiguration( pwmRequest, userIdentity, userGUID, otp );
                        successes++;
                    }
                    catch ( final PwmUnrecoverableException e )
                    {
                        LOGGER.error( pwmRequest, () -> "error writing to " + otpSecretStorageLocation + ", error: " + e.getMessage() );
                        errorMsgs.append( otpSecretStorageLocation ).append( " error: " ).append( e.getMessage() );
                    }
                }
                else
                {
                    LOGGER.warn( pwmRequest, () -> String.format( "storage location %s not implemented", otpSecretStorageLocation.toString() ) );
                }
            }
        }

        if ( attempts == 0 )
        {
            final String errorMsg = "no OTP secret save methods are available or configured";
            final ErrorInformation errorInfo = new ErrorInformation( PwmError.ERROR_WRITING_OTP_SECRET, errorMsg );
            throw new PwmOperationalException( errorInfo );
        }

        if ( attempts != successes )
        {
            // should be impossible to read here, but just in case.
            final String errorMsg = "OTP secret write only partially successful; attempts=" + attempts + ", successes=" + successes + ", errors: " + errorMsgs;
            final ErrorInformation errorInfo = new ErrorInformation( PwmError.ERROR_WRITING_OTP_SECRET, errorMsg );
            throw new PwmOperationalException( errorInfo );
        }
    }

    public void clearOTPUserConfiguration(
            final PwmRequest pwmRequest,
            final UserIdentity userIdentity,
            final ChaiUser chaiUser
    )
            throws PwmOperationalException, ChaiUnavailableException, PwmUnrecoverableException
    {
        LOGGER.trace( pwmRequest, () -> "beginning clear otp user configuration" );

        int attempts = 0;
        int successes = 0;

        final DomainConfig config = pwmDomain.getConfig();
        final List<DataStorageMethod> otpSecretStorageLocations = config.readGenericStorageLocations( PwmSetting.OTP_SECRET_READ_PREFERENCE );

        final String userGUID = readGuidIfNeeded( pwmDomain, pwmRequest.getLabel(), otpSecretStorageLocations, userIdentity );

        final StringBuilder errorMsgs = new StringBuilder();
        if ( otpSecretStorageLocations != null )
        {
            for ( final DataStorageMethod otpSecretStorageLocation : otpSecretStorageLocations )
            {
                attempts++;
                final OtpOperator operator = operatorMap.get( otpSecretStorageLocation );
                if ( operator != null )
                {
                    try
                    {
                        operator.clearOtpUserConfiguration( pwmRequest, userIdentity, chaiUser, userGUID );
                        successes++;
                    }
                    catch ( final PwmUnrecoverableException e )
                    {
                        LOGGER.error( pwmRequest, () -> "error clearing " + otpSecretStorageLocation + ", error: " + e.getMessage() );
                        errorMsgs.append( otpSecretStorageLocation ).append( " error: " ).append( e.getMessage() );
                    }
                }
                else
                {
                    LOGGER.warn( pwmRequest, () -> String.format( "storage location %s not implemented", otpSecretStorageLocation.toString() ) );
                }
            }
        }

        if ( attempts == 0 )
        {
            final String errorMsg = "no OTP secret clear methods are available or configured";
            //@todo: replace error message
            final ErrorInformation errorInfo = new ErrorInformation( PwmError.ERROR_WRITING_OTP_SECRET, errorMsg );
            throw new PwmOperationalException( errorInfo );
        }

        if ( attempts != successes )
        {
            // should be impossible to read here, but just in case.
            final String errorMsg = "OTP secret clearing only partially successful; attempts=" + attempts + ", successes=" + successes + ", error: " + errorMsgs;
            //@todo: replace error message
            final ErrorInformation errorInfo = new ErrorInformation( PwmError.ERROR_WRITING_OTP_SECRET, errorMsg );
            throw new PwmOperationalException( errorInfo );
        }
    }

    public OtpSettings getSettings( )
    {
        return settings;
    }

    @Override
    public ServiceInfoBean serviceInfo( )
    {
        return ServiceInfoBean.builder().build();
    }

    private static String readGuidIfNeeded(
            final PwmDomain pwmDomain,
            final SessionLabel sessionLabel,
            final Collection<DataStorageMethod> otpSecretStorageLocations,
            final UserIdentity userIdentity

    )
            throws ChaiUnavailableException, PwmUnrecoverableException
    {
        final String userGUID;
        if ( otpSecretStorageLocations.contains( DataStorageMethod.DB ) || otpSecretStorageLocations.contains(
                DataStorageMethod.LOCALDB ) )
        {
            userGUID = LdapOperationsHelper.readLdapGuidValue( pwmDomain, sessionLabel, userIdentity, false );
        }
        else
        {
            userGUID = null;
        }
        return userGUID;
    }

    @Getter
    public static class OtpSettings implements Serializable
    {
        private OTPStorageFormat otpStorageFormat;
        private OTPUserRecord.Type otpType = OTPUserRecord.Type.TOTP;
        private int totpPastIntervals;
        private int totpFutureIntervals;
        private int totpIntervalSeconds;
        private int otpTokenLength;
        private String recoveryTokenMacro;
        private int recoveryHashIterations;
        private String recoveryHashMethod;


        static OtpSettings fromConfig( final DomainConfig config )
        {
            final OtpSettings otpSettings = new OtpSettings();

            otpSettings.otpStorageFormat = config.readSettingAsEnum( PwmSetting.OTP_SECRET_STORAGEFORMAT, OTPStorageFormat.class );
            otpSettings.totpPastIntervals = Integer.parseInt( config.readAppProperty( AppProperty.TOTP_PAST_INTERVALS ) );
            otpSettings.totpFutureIntervals = Integer.parseInt( config.readAppProperty( AppProperty.TOTP_FUTURE_INTERVALS ) );
            otpSettings.totpIntervalSeconds = Integer.parseInt( config.readAppProperty( AppProperty.TOTP_INTERVAL ) );
            otpSettings.otpTokenLength = Integer.parseInt( config.readAppProperty( AppProperty.OTP_TOKEN_LENGTH ) );
            otpSettings.recoveryTokenMacro = config.readAppProperty( AppProperty.OTP_RECOVERY_TOKEN_MACRO );
            otpSettings.recoveryHashIterations = Integer.parseInt( config.readAppProperty( AppProperty.OTP_RECOVERY_HASH_COUNT ) );
            otpSettings.recoveryHashMethod = config.readAppProperty( AppProperty.OTP_RECOVERY_HASH_METHOD );
            return otpSettings;
        }
    }
}
