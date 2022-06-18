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

package password.pwm.svc.token;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import lombok.Builder;
import lombok.Value;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmApplicationMode;
import password.pwm.PwmConstants;
import password.pwm.PwmDomain;
import password.pwm.bean.DomainID;
import password.pwm.bean.EmailItemBean;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.TokenDestinationItem;
import password.pwm.bean.UserIdentity;
import password.pwm.config.DomainConfig;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.config.option.MessageSendMethod;
import password.pwm.config.option.TokenStorageMethod;
import password.pwm.config.profile.ActivateUserProfile;
import password.pwm.config.profile.ForgottenPasswordProfile;
import password.pwm.config.profile.NewUserProfile;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthMessage;
import password.pwm.health.HealthRecord;
import password.pwm.http.PwmRequestContext;
import password.pwm.user.UserInfo;
import password.pwm.ldap.auth.SessionAuthenticator;
import password.pwm.svc.AbstractPwmService;
import password.pwm.svc.PwmService;
import password.pwm.svc.db.DatabaseDataStore;
import password.pwm.svc.db.DatabaseTable;
import password.pwm.svc.event.AuditEvent;
import password.pwm.svc.event.AuditRecord;
import password.pwm.svc.event.AuditRecordFactory;
import password.pwm.svc.event.AuditServiceClient;
import password.pwm.svc.intruder.IntruderRecordType;
import password.pwm.svc.intruder.IntruderServiceClient;
import password.pwm.svc.sms.SmsQueueService;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsClient;
import password.pwm.util.DataStore;
import password.pwm.util.java.MiscUtil;
import password.pwm.util.java.StatisticCounterBundle;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBDataStore;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroRequest;
import password.pwm.util.password.PasswordUtility;
import password.pwm.util.secure.PwmRandom;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * This PWM service is responsible for reading/writing tokens used for forgotten password,
 * new user registration, account activation, and other functions.  Several implementations
 * of the backing storage method are available.
 *
 * @author jrivard@gmail.com
 */
public class TokenService extends AbstractPwmService implements PwmService
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( TokenService.class );

    private PwmDomain pwmDomain;
    private DomainConfig domainConfig;
    private TokenStorageMethod storageMethod;
    private DataStorageMethod dataStorageMethod;

    private TokenMachine tokenMachine;

    private final StatisticCounterBundle<StatsKey> stats = new StatisticCounterBundle<>( StatsKey.class );

    private boolean verifyPwModifyTime = true;

    enum StatsKey
    {
        tokensIssued,
        tokenValidations,
        tokenValidationsPassed,
        tokenValidationsFailed, tokensRemoved,
    }

    public enum TokenEntryType
    {
        unauthenticated,
        authenticated,
    }

    public TokenService( )
            throws PwmOperationalException
    {
    }

    public TokenPayload createTokenPayload(
            final TokenType name,
            final TimeDuration lifetime,
            final Map<String, String> data,
            final UserIdentity userIdentity,
            final TokenDestinationItem destination
    )
    {
        final String guid = PwmRandom.getInstance().randomUUID().toString();
        final Instant expiration = lifetime.incrementFromInstant( Instant.now() );
        return new TokenPayload( name.name(), expiration, data, userIdentity, destination, guid );
    }

    @Override
    public STATUS postAbstractInit( final PwmApplication pwmApplication, final DomainID domainID )
            throws PwmException
    {
        this.pwmDomain = pwmApplication.domains().get( domainID );

        LOGGER.trace( getSessionLabel(), () -> "opening" );

        this.domainConfig = pwmDomain.getConfig();

        storageMethod = domainConfig.getTokenStorageMethod().orElseThrow( () ->
        {
            final String errorMsg = "no storage method specified";
            setStartupError( new ErrorInformation( PwmError.ERROR_INVALID_CONFIG, errorMsg ) );
            return new PwmOperationalException( getStartupError() );
        } );

        if ( pwmApplication.getLocalDB() == null )
        {
            LOGGER.trace( getSessionLabel(), () -> "localDB is not available, will remain closed" );
            return STATUS.CLOSED;
        }

        if ( pwmDomain.getApplicationMode() != PwmApplicationMode.RUNNING )
        {
            LOGGER.trace( getSessionLabel(), () -> "Application mode is not 'running', will remain closed." );
            return STATUS.CLOSED;
        }

        try
        {
            DataStorageMethod usedStorageMethod = null;
            switch ( storageMethod )
            {
                case STORE_LOCALDB:
                {
                    final DataStore dataStore = new LocalDBDataStore( pwmApplication.getLocalDB(), LocalDB.DB.TOKENS );
                    tokenMachine = new DataStoreTokenMachine( pwmDomain, this, dataStore );
                    usedStorageMethod = DataStorageMethod.LOCALDB;
                    break;
                }

                case STORE_DB:
                {
                    final DataStore dataStore = new DatabaseDataStore( pwmDomain.getPwmApplication().getDatabaseService(), DatabaseTable.TOKENS );
                    tokenMachine = new DataStoreTokenMachine( pwmDomain, this, dataStore );
                    usedStorageMethod = DataStorageMethod.DB;
                    break;
                }

                case STORE_CRYPTO:
                    tokenMachine = new CryptoTokenMachine( this );
                    usedStorageMethod = DataStorageMethod.CRYPTO;
                    break;

                case STORE_LDAP:
                    tokenMachine = new LdapTokenMachine( this, pwmDomain );
                    usedStorageMethod = DataStorageMethod.LDAP;
                    break;

                default:
                    MiscUtil.unhandledSwitchStatement( storageMethod );
            }
            dataStorageMethod = usedStorageMethod;
        }
        catch ( final PwmException e )
        {
            final String errorMsg = "unable to start token manager: " + e.getErrorInformation().getDetailedErrorMsg();
            final ErrorInformation newErrorInformation = new ErrorInformation( e.getError(), errorMsg );
            setStartupError( newErrorInformation );
            LOGGER.error( newErrorInformation::toDebugStr );
            setStatus( STATUS.CLOSED );
            return STATUS.CLOSED;
        }

        verifyPwModifyTime = Boolean.parseBoolean( domainConfig.readAppProperty( AppProperty.TOKEN_VERIFY_PW_MODIFY_TIME ) );

        LOGGER.debug( getSessionLabel(), () -> "open" );

        return STATUS.OPEN;
    }

    public boolean supportsName( )
    {
        return tokenMachine.supportsName();
    }

    public String generateNewToken( final TokenPayload tokenPayload, final SessionLabel sessionLabel )
            throws PwmUnrecoverableException, PwmOperationalException
    {
        checkStatus();

        final String tokenKey;
        try
        {
            tokenKey = tokenMachine.generateToken( sessionLabel, tokenPayload );
            tokenMachine.storeToken( tokenMachine.keyFromKey( tokenKey ), tokenPayload );
        }
        catch ( final PwmException e )
        {
            final String errorMsg = "unexpected error trying to store token in datastore: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation( e.getError(), errorMsg );
            throw new PwmOperationalException( errorInformation );
        }

        LOGGER.trace( sessionLabel, () -> "generated token with payload: " + tokenPayload.toDebugString() );

        final AuditRecord auditRecord = AuditRecordFactory.make( sessionLabel, pwmDomain ).createUserAuditRecord(
                AuditEvent.TOKEN_ISSUED,
                tokenPayload.getUserIdentity(),
                sessionLabel,
                JsonFactory.get().serialize( tokenPayload )
        );

        stats.increment( StatsKey.tokensIssued );
        AuditServiceClient.submit( pwmDomain.getPwmApplication(), sessionLabel, auditRecord );
        return tokenKey;
    }


    private void markTokenAsClaimed(
            final TokenKey tokenKey,
            final SessionLabel sessionLabel,
            final TokenPayload tokenPayload
    )
            throws PwmUnrecoverableException
    {
        if ( tokenPayload == null || tokenPayload.getUserIdentity() == null )
        {
            return;
        }

        final boolean removeOnClaim = Boolean.parseBoolean( domainConfig.readAppProperty( AppProperty.TOKEN_REMOVE_ON_CLAIM ) );

        if ( removeOnClaim )
        {
            try
            {
                LOGGER.trace( sessionLabel, () -> "removing claimed token: " + tokenPayload.toDebugString() );
                tokenMachine.removeToken( tokenKey );
            }
            catch ( final PwmOperationalException e )
            {
                LOGGER.error( sessionLabel, () -> "error clearing claimed token: " + e.getMessage() );
            }
        }

        final AuditRecord auditRecord = AuditRecordFactory.make( sessionLabel, pwmDomain ).createUserAuditRecord(
                AuditEvent.TOKEN_CLAIMED,
                tokenPayload.getUserIdentity(),
                sessionLabel,
                JsonFactory.get().serialize( tokenPayload )
        );
        AuditServiceClient.submit( pwmDomain.getPwmApplication(), sessionLabel, auditRecord );

        StatisticsClient.incrementStat( pwmDomain, Statistic.TOKENS_PASSSED );
    }

    public TokenPayload retrieveTokenData( final SessionLabel sessionLabel, final String tokenKey )
            throws PwmOperationalException
    {
        checkStatus();

        try
        {
            final Optional<TokenPayload> storedToken = tokenMachine.retrieveToken( sessionLabel, tokenMachine.keyFromKey( tokenKey ) );
            if ( storedToken.isPresent() )
            {

                if ( testIfTokenIsExpired( sessionLabel, storedToken.get() ) )
                {
                    throw new PwmOperationalException( new ErrorInformation( PwmError.ERROR_TOKEN_EXPIRED ) );
                }

                return storedToken.get();
            }
        }
        catch ( final PwmException e )
        {
            if ( e.getError() == PwmError.ERROR_TOKEN_EXPIRED || e.getError() == PwmError.ERROR_TOKEN_INCORRECT || e.getError() == PwmError.ERROR_TOKEN_MISSING_CONTACT )
            {
                throw new PwmOperationalException( e.getErrorInformation() );
            }
            final String errorMsg = "error trying to retrieve token from datastore: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_TOKEN_INCORRECT, errorMsg );
            throw new PwmOperationalException( errorInformation );
        }
        return null;
    }

    @Override
    public void shutdownImpl( )
    {
        setStatus( STATUS.CLOSED );
    }

    @Override
    public List<HealthRecord> serviceHealthCheck( )
    {
        final List<HealthRecord> returnRecords = new ArrayList<>();

        if ( tokensAreUsedInConfig( domainConfig ) )
        {
            if ( getStartupError() != null )
            {
                returnRecords.add( HealthRecord.forMessage( DomainID.systemId(), HealthMessage.CryptoTokenWithNewUserVerification, getStartupError().toDebugStr() ) );
            }
        }

        if ( storageMethod == TokenStorageMethod.STORE_LDAP )
        {
            if ( domainConfig.readSettingAsBoolean( PwmSetting.NEWUSER_ENABLE ) )
            {
                for ( final NewUserProfile newUserProfile : domainConfig.getNewUserProfiles().values() )
                {
                    if ( newUserProfile.readSettingAsBoolean( PwmSetting.NEWUSER_EMAIL_VERIFICATION ) )
                    {
                        final String label = PwmSetting.NEWUSER_EMAIL_VERIFICATION.toMenuLocationDebug( newUserProfile.getIdentifier(), PwmConstants.DEFAULT_LOCALE );
                        final String label2 = PwmSetting.TOKEN_STORAGEMETHOD.toMenuLocationDebug( null, PwmConstants.DEFAULT_LOCALE );
                        returnRecords.add( HealthRecord.forMessage( DomainID.systemId(), HealthMessage.CryptoTokenWithNewUserVerification, label, label2 ) );
                    }
                    if ( newUserProfile.readSettingAsBoolean( PwmSetting.NEWUSER_SMS_VERIFICATION ) )
                    {
                        final String label = PwmSetting.NEWUSER_SMS_VERIFICATION.toMenuLocationDebug( newUserProfile.getIdentifier(), PwmConstants.DEFAULT_LOCALE );
                        final String label2 = PwmSetting.TOKEN_STORAGEMETHOD.toMenuLocationDebug( null, PwmConstants.DEFAULT_LOCALE );
                        returnRecords.add( HealthRecord.forMessage( DomainID.systemId(), HealthMessage.CryptoTokenWithNewUserVerification, label, label2 ) );
                    }
                }
            }
        }

        return returnRecords;
    }

    private boolean testIfTokenIsExpired( final SessionLabel sessionLabel, final TokenPayload theToken )
    {
        if ( theToken == null )
        {
            return false;
        }
        final Instant issueDate = theToken.getIssueTime();
        if ( issueDate == null )
        {
            LOGGER.error( sessionLabel, () -> "retrieved token has no issueDate, marking as expired: " + theToken.toDebugString() );
            return true;
        }
        if ( theToken.getExpiration() == null )
        {
            LOGGER.error( sessionLabel, () -> "retrieved token has no expiration timestamp, marking as expired: " + theToken.toDebugString() );
            return true;
        }
        return theToken.getExpiration().isBefore( Instant.now() );
    }

    private static String makeRandomCode( final DomainConfig config )
    {
        final String randomChars = config.readSettingAsString( PwmSetting.TOKEN_CHARACTERS );
        final int codeLength = ( int ) config.readSettingAsLong( PwmSetting.TOKEN_LENGTH );
        final PwmRandom random = PwmRandom.getInstance();

        return random.alphaNumericString( randomChars, codeLength );
    }

    void cleanup()
    {
        try
        {
            tokenMachine.cleanup();
        }
        catch ( final Exception e )
        {
            LOGGER.warn( getSessionLabel(), () -> "unexpected error while cleaning expired stored tokens: " + e.getMessage() );
        }
    }

    protected StatisticCounterBundle<StatsKey> getStats()
    {
        return stats;
    }

    private void checkStatus( ) throws PwmOperationalException
    {
        if ( status() != STATUS.OPEN )
        {
            throw new PwmOperationalException( new ErrorInformation( PwmError.ERROR_SERVICE_NOT_AVAILABLE, "token manager is not open" ) );
        }
    }

    public long size( )
    {
        if ( status() != STATUS.OPEN )
        {
            return -1;
        }

        try
        {
            return tokenMachine.size();
        }
        catch ( final Exception e )
        {
            LOGGER.error( getSessionLabel(), () -> "unexpected error reading size of token storage table: " + e.getMessage() );
        }

        return -1;
    }

    String makeUniqueTokenForMachine( final SessionLabel sessionLabel, final TokenMachine machine )
            throws PwmUnrecoverableException, PwmOperationalException
    {
        String tokenKey = null;
        int attempts = 0;
        final int maxUniqueCreateAttempts = Integer.parseInt( pwmDomain.getConfig().readAppProperty( AppProperty.TOKEN_MAX_UNIQUE_CREATE_ATTEMPTS ) );
        while ( tokenKey == null && attempts < maxUniqueCreateAttempts )
        {
            tokenKey = makeRandomCode( domainConfig );
            LOGGER.trace( sessionLabel, () -> "generated new token random code, checking for uniqueness" );
            final Optional<TokenPayload> existingPayload = machine.retrieveToken( sessionLabel, tokenMachine.keyFromKey( tokenKey ) );
            if ( existingPayload.isPresent() )
            {
                tokenKey = null;
            }
            attempts++;
        }

        if ( tokenKey == null )
        {
            throw new PwmOperationalException( new ErrorInformation( PwmError.ERROR_INTERNAL, "unable to generate a unique token key after " + attempts + " attempts" ) );
        }

        {
            final int finalAttempts = attempts;
            LOGGER.trace( sessionLabel, () -> "created new unique random token value after " + finalAttempts + " attempts" );
        }
        return tokenKey;
    }

    private static boolean tokensAreUsedInConfig( final DomainConfig domainConfig )
    {
        if ( domainConfig.readSettingAsBoolean( PwmSetting.NEWUSER_ENABLE ) )
        {
            for ( final NewUserProfile newUserProfile : domainConfig.getNewUserProfiles().values() )
            {
                if ( newUserProfile.readSettingAsBoolean( PwmSetting.NEWUSER_EMAIL_VERIFICATION ) )
                {
                    return true;
                }
            }
        }

        if ( domainConfig.readSettingAsBoolean( PwmSetting.ACTIVATE_USER_ENABLE ) )
        {
            for ( final ActivateUserProfile activateUserProfile : domainConfig.getUserActivationProfiles().values() )
            {
                final MessageSendMethod activateMethod = activateUserProfile.readSettingAsEnum( PwmSetting.ACTIVATE_TOKEN_SEND_METHOD, MessageSendMethod.class );
                if ( MessageSendMethod.NONE != activateMethod )
                {
                    return true;
                }
            }
        }

        if ( domainConfig.readSettingAsBoolean( PwmSetting.SETUP_RESPONSE_ENABLE ) )
        {
            for ( final ForgottenPasswordProfile forgottenPasswordProfile : domainConfig.getForgottenPasswordProfiles().values() )
            {
                final MessageSendMethod messageSendMethod = forgottenPasswordProfile.readSettingAsEnum( PwmSetting.RECOVERY_TOKEN_SEND_METHOD, MessageSendMethod.class );
                if ( messageSendMethod != null && messageSendMethod != MessageSendMethod.NONE )
                {
                    return true;
                }
            }
        }
        return false;
    }

    String toEncryptedString( final TokenPayload tokenPayload )
            throws PwmUnrecoverableException, PwmOperationalException
    {
        final String jsonPayload = JsonFactory.get().serialize( tokenPayload );
        return pwmDomain.getSecureService().encryptToString( jsonPayload );
    }

    TokenPayload fromEncryptedString( final String inputString )
            throws PwmOperationalException, PwmUnrecoverableException
    {
        final String deWhiteSpacedToken = inputString.replaceAll( "\\s", "" );
        try
        {
            final String decryptedString = pwmDomain.getSecureService().decryptStringValue( deWhiteSpacedToken );
            return JsonFactory.get().deserialize( decryptedString, TokenPayload.class );
        }
        catch ( final PwmUnrecoverableException e )
        {
            final String errorMsg = "unable to decrypt token payload: " + e.getErrorInformation().toDebugStr();
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_TOKEN_INCORRECT, errorMsg );
            throw new PwmOperationalException( errorInformation );
        }
    }

    @Override
    public ServiceInfoBean serviceInfo( )
    {
        return ServiceInfoBean.builder()
                .debugProperties( stats.debugStats() )
                .storageMethod( dataStorageMethod )
                .build();
    }

    public TokenPayload processUserEnteredCode(
            final PwmRequestContext pwmRequestContext,
            final UserIdentity sessionUserIdentity,
            final TokenType tokenType,
            final String userEnteredCode,
            final TokenEntryType tokenEntryType
    )
            throws PwmOperationalException, PwmUnrecoverableException
    {
        stats.increment( StatsKey.tokenValidations );
        final SessionLabel sessionLabel = pwmRequestContext.getSessionLabel();
        try
        {
            final TokenPayload tokenPayload = processUserEnteredCodeImpl(
                    sessionLabel,
                    sessionUserIdentity,
                    tokenType,
                    userEnteredCode
            );
            if ( tokenPayload.getDestination() != null && StringUtil.notEmpty( tokenPayload.getDestination().getValue() ) )
            {
                pwmDomain.getIntruderService().clear( IntruderRecordType.TOKEN_DEST, tokenPayload.getDestination().getValue() );
            }
            markTokenAsClaimed( tokenMachine.keyFromKey( userEnteredCode ), sessionLabel, tokenPayload );
            stats.increment( StatsKey.tokenValidationsPassed );
            return tokenPayload;
        }
        catch ( final Exception e )
        {
            final ErrorInformation errorInformation;
            if ( e instanceof PwmException )
            {
                errorInformation = ( ( PwmException ) e ).getErrorInformation();
            }
            else
            {
                errorInformation = new ErrorInformation( PwmError.ERROR_TOKEN_INCORRECT, e.getMessage() );
            }

            LOGGER.debug( sessionLabel, errorInformation );

            if ( sessionUserIdentity != null && tokenEntryType == TokenEntryType.unauthenticated )
            {
                SessionAuthenticator.simulateBadPassword( pwmRequestContext, sessionUserIdentity );
                IntruderServiceClient.markUserIdentity( pwmRequestContext.getPwmDomain(), pwmRequestContext.getSessionLabel(), sessionUserIdentity );
            }
            StatisticsClient.incrementStat( pwmDomain, Statistic.RECOVERY_FAILURES );
            stats.increment( StatsKey.tokenValidationsFailed );
            throw new PwmOperationalException( errorInformation );
        }
    }

    private TokenPayload processUserEnteredCodeImpl(
            final SessionLabel sessionLabel,
            final UserIdentity sessionUserIdentity,
            final TokenType tokenType,
            final String userEnteredCode
    )
            throws PwmOperationalException, PwmUnrecoverableException
    {
        final TokenPayload tokenPayload;
        try
        {
            tokenPayload = pwmDomain.getTokenService().retrieveTokenData( sessionLabel, userEnteredCode );
        }
        catch ( final PwmOperationalException e )
        {
            final String errorMsg = "unexpected error attempting to read token from storage: " + e.getErrorInformation().toDebugStr();
            throw new PwmOperationalException( PwmError.ERROR_TOKEN_INCORRECT, errorMsg );
        }

        if ( tokenPayload == null )
        {
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_TOKEN_INCORRECT, "token not found" );
            throw new PwmOperationalException( errorInformation );
        }

        LOGGER.trace( sessionLabel, () -> "retrieved tokenPayload: " + tokenPayload.toDebugString() );

        if ( tokenType != null && pwmDomain.getTokenService().supportsName() )
        {
            if ( !tokenType.matchesName( tokenPayload.getName() ) )
            {
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_TOKEN_INCORRECT, "incorrect token/name format" );
                throw new PwmOperationalException( errorInformation );
            }
        }

        // check current session identity
        if ( tokenPayload.getUserIdentity() != null && sessionUserIdentity != null )
        {
            if ( !tokenPayload.getUserIdentity().canonicalEquals( sessionLabel, sessionUserIdentity, pwmDomain.getPwmApplication() ) )
            {
                final String errorMsg = "user in session '" + sessionUserIdentity + "' entered code for user '" + tokenPayload.getUserIdentity() + "', counting as invalid attempt";
                throw new PwmOperationalException( PwmError.ERROR_TOKEN_INCORRECT, errorMsg );
            }
        }

        // check if password-last-modified is same as when tried to read it before.
        if ( verifyPwModifyTime
                && tokenPayload.getUserIdentity() != null
                && tokenPayload.getData() != null
                && tokenPayload.getData().containsKey( PwmConstants.TOKEN_KEY_PWD_CHG_DATE )
        )
        {
            try
            {
                final Instant userLastPasswordChange = PasswordUtility.determinePwdLastModified(
                        pwmDomain,
                        sessionLabel,
                        tokenPayload.getUserIdentity() );

                final String dateStringInToken = tokenPayload.getData().get( PwmConstants.TOKEN_KEY_PWD_CHG_DATE );

                LOGGER.trace( sessionLabel, () -> "tokenPayload=" + tokenPayload.toDebugString()
                        + ", sessionUser=" + ( sessionUserIdentity == null ? "null" : sessionUserIdentity.toDisplayString() )
                        + ", payloadUserIdentity=" + tokenPayload.getUserIdentity().toDisplayString()
                        + ", userLastPasswordChange=" + StringUtil.toIsoDate( userLastPasswordChange )
                        + ", dateStringInToken=" + dateStringInToken );

                if ( userLastPasswordChange != null && dateStringInToken != null )
                {

                    final String userChangeString = StringUtil.toIsoDate( userLastPasswordChange );

                    if ( !dateStringInToken.equalsIgnoreCase( userChangeString ) )
                    {
                        final String errorString = "user password has changed since token issued, token rejected;"
                                + " currentValue=" + userChangeString + ", tokenValue=" + dateStringInToken;
                        LOGGER.trace( sessionLabel, () -> errorString + "; token=" + tokenPayload.toDebugString() );
                        final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_TOKEN_EXPIRED, errorString );
                        throw new PwmOperationalException( errorInformation );
                    }
                }
            }
            catch ( final ChaiUnavailableException | PwmUnrecoverableException e )
            {
                final String errorMsg = "unexpected error reading user's last password change time while validating token: " + e.getMessage();
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_TOKEN_INCORRECT, errorMsg );
                throw new PwmOperationalException( errorInformation );
            }
        }

        LOGGER.debug( sessionLabel, () -> "token validation has been passed" );
        return tokenPayload;
    }

    @Value
    @Builder
    public static class TokenSendInfo
    {
        private PwmDomain pwmDomain;
        private UserInfo userInfo;
        private MacroRequest macroRequest;
        private EmailItemBean configuredEmailSetting;
        private TokenDestinationItem tokenDestinationItem;
        private String smsMessage;
        private String tokenKey;
        private SessionLabel sessionLabel;
    }

    public static class TokenSender
    {
        public static void sendToken(
                final TokenSendInfo tokenSendInfo
        )
                throws PwmUnrecoverableException
        {
            final boolean success;

            switch ( tokenSendInfo.getTokenDestinationItem().getType() )
            {
                case sms:
                    // Only try SMS
                    success = sendSmsToken( tokenSendInfo );

                    break;
                case email:
                default:
                    // Only try email
                    success = sendEmailToken( tokenSendInfo );
                    break;
            }

            if ( !success )
            {
                throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_TOKEN_MISSING_CONTACT ) );
            }

            final PwmDomain pwmDomain = tokenSendInfo.getPwmDomain();
            StatisticsClient.incrementStat( pwmDomain, Statistic.TOKENS_SENT );
        }

        private static boolean sendEmailToken(
                final TokenSendInfo tokenSendInfo
        )
                throws PwmUnrecoverableException
        {
            final String toAddress = tokenSendInfo.getTokenDestinationItem().getValue();
            if ( StringUtil.isEmpty( toAddress ) )
            {
                return false;
            }

            final PwmDomain pwmDomain = tokenSendInfo.getPwmDomain();
            pwmDomain.getIntruderService().mark( IntruderRecordType.TOKEN_DEST, toAddress, null );

            final EmailItemBean configuredEmailSetting = tokenSendInfo.getConfiguredEmailSetting();
            final EmailItemBean tokenizedEmail = configuredEmailSetting.applyBodyReplacement(
                    "%TOKEN%",
                    tokenSendInfo.getTokenKey() );

            pwmDomain.getPwmApplication().getEmailQueue().submitEmailImmediate(
                    tokenizedEmail,
                    tokenSendInfo.getUserInfo(),
                    tokenSendInfo.getMacroRequest() );

            LOGGER.debug( tokenSendInfo.getSessionLabel(),  () -> "token email added to send queue for " + toAddress );
            return true;
        }

        private static boolean sendSmsToken(
                final TokenSendInfo tokenSendInfo
        )
                throws PwmUnrecoverableException
        {
            final String smsNumber = tokenSendInfo.getTokenDestinationItem().getValue();
            if ( StringUtil.isEmpty( smsNumber ) )
            {
                return false;
            }


            final String modifiedMessage = tokenSendInfo.getSmsMessage().replaceAll( "%TOKEN%", tokenSendInfo.getTokenKey() );

            final PwmDomain pwmDomain = tokenSendInfo.getPwmDomain();
            pwmDomain.getIntruderService().mark( IntruderRecordType.TOKEN_DEST, smsNumber, tokenSendInfo.getSessionLabel() );

            SmsQueueService.sendSmsUsingQueue( pwmDomain.getPwmApplication(), smsNumber, modifiedMessage, tokenSendInfo.getSessionLabel(), tokenSendInfo.getMacroRequest() );
            LOGGER.debug( tokenSendInfo.getSessionLabel(), () -> "token SMS added to send queue for " + smsNumber );
            return true;
        }
    }

    static TimeDuration maxTokenAge( final DomainConfig domainConfig )
    {
        long maxValue = 0;
        maxValue = Math.max( maxValue, domainConfig.readSettingAsLong( PwmSetting.TOKEN_LIFETIME ) );
        maxValue = Math.max( maxValue, domainConfig.readSettingAsLong( PwmSetting.TOKEN_LIFETIME ) );
        for ( final NewUserProfile newUserProfile : domainConfig.getNewUserProfiles().values() )
        {
            maxValue = Math.max( maxValue, newUserProfile.readSettingAsLong( PwmSetting.NEWUSER_TOKEN_LIFETIME_EMAIL ) );
            maxValue = Math.max( maxValue, newUserProfile.readSettingAsLong( PwmSetting.NEWUSER_TOKEN_LIFETIME_SMS ) );
        }
        return TimeDuration.of( maxValue, TimeDuration.Unit.SECONDS );
    }
}
