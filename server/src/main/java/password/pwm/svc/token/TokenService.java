/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
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

package password.pwm.svc.token;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import lombok.Builder;
import lombok.Value;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.EmailItemBean;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.TokenDestinationItem;
import password.pwm.bean.UserIdentity;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.config.option.MessageSendMethod;
import password.pwm.config.option.TokenStorageMethod;
import password.pwm.config.profile.ForgottenPasswordProfile;
import password.pwm.config.profile.NewUserProfile;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthMessage;
import password.pwm.health.HealthRecord;
import password.pwm.http.PwmSession;
import password.pwm.ldap.UserInfo;
import password.pwm.ldap.auth.SessionAuthenticator;
import password.pwm.svc.PwmService;
import password.pwm.svc.event.AuditEvent;
import password.pwm.svc.event.AuditRecord;
import password.pwm.svc.event.AuditRecordFactory;
import password.pwm.svc.intruder.RecordType;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsManager;
import password.pwm.util.DataStore;
import password.pwm.util.db.DatabaseDataStore;
import password.pwm.util.db.DatabaseTable;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBDataStore;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;
import password.pwm.util.operations.PasswordUtility;
import password.pwm.util.secure.PwmRandom;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * This PWM service is responsible for reading/writing tokens used for forgotten password,
 * new user registration, account activation, and other functions.  Several implementations
 * of the backing storage method are available.
 *
 * @author jrivard@gmail.com
 */
public class TokenService implements PwmService
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( TokenService.class );

    private ScheduledExecutorService executorService;

    private PwmApplication pwmApplication;
    private Configuration configuration;
    private TokenStorageMethod storageMethod;
    private TokenMachine tokenMachine;

    private ServiceInfoBean serviceInfo = new ServiceInfoBean( Collections.emptyList() );
    private STATUS status = STATUS.NEW;

    private ErrorInformation errorInformation = null;

    private boolean verifyPwModifyTime = true;

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

    public void init( final PwmApplication pwmApplication )
            throws PwmException
    {
        LOGGER.trace( "opening" );
        status = STATUS.OPENING;

        this.pwmApplication = pwmApplication;
        this.configuration = pwmApplication.getConfig();

        storageMethod = configuration.getTokenStorageMethod();
        if ( storageMethod == null )
        {
            final String errorMsg = "no storage method specified";
            errorInformation = new ErrorInformation( PwmError.ERROR_INVALID_CONFIG, errorMsg );
            status = STATUS.CLOSED;
            throw new PwmOperationalException( errorInformation );
        }

        try
        {
            DataStorageMethod usedStorageMethod = null;
            switch ( storageMethod )
            {
                case STORE_LOCALDB:
                {
                    final DataStore dataStore = new LocalDBDataStore( pwmApplication.getLocalDB(), LocalDB.DB.TOKENS );
                    tokenMachine = new DataStoreTokenMachine( pwmApplication, this, dataStore );
                    usedStorageMethod = DataStorageMethod.LOCALDB;
                    break;
                }

                case STORE_DB:
                {
                    final DataStore dataStore = new DatabaseDataStore( pwmApplication.getDatabaseService(), DatabaseTable.TOKENS );
                    tokenMachine = new DataStoreTokenMachine( pwmApplication, this, dataStore );
                    usedStorageMethod = DataStorageMethod.DB;
                    break;
                }

                case STORE_CRYPTO:
                    tokenMachine = new CryptoTokenMachine( this );
                    usedStorageMethod = DataStorageMethod.CRYPTO;
                    break;

                case STORE_LDAP:
                    tokenMachine = new LdapTokenMachine( this, pwmApplication );
                    usedStorageMethod = DataStorageMethod.LDAP;
                    break;

                default:
                    JavaHelper.unhandledSwitchStatement( storageMethod );
            }
            serviceInfo = new ServiceInfoBean( Collections.singletonList( usedStorageMethod ) );
        }
        catch ( PwmException e )
        {
            final String errorMsg = "unable to start token manager: " + e.getErrorInformation().getDetailedErrorMsg();
            final ErrorInformation newErrorInformation = new ErrorInformation( e.getError(), errorMsg );
            errorInformation = newErrorInformation;
            LOGGER.error( newErrorInformation.toDebugStr() );
            status = STATUS.CLOSED;
            return;
        }

        executorService = Executors.newSingleThreadScheduledExecutor(
                JavaHelper.makePwmThreadFactory(
                        JavaHelper.makeThreadName( pwmApplication, this.getClass() ) + "-",
                        true
                ) );

        final TimerTask cleanerTask = new CleanerTask();

        {
            final int cleanerFrequencySeconds = Integer.parseInt( configuration.readAppProperty( AppProperty.TOKEN_CLEANER_INTERVAL_SECONDS ) );
            final TimeDuration cleanerFrequency = new TimeDuration( cleanerFrequencySeconds, TimeUnit.SECONDS );
            executorService.scheduleAtFixedRate( cleanerTask, 10, cleanerFrequencySeconds, TimeUnit.SECONDS );
            LOGGER.trace( "token cleanup will occur every " + cleanerFrequency.asCompactString() );
        }

        verifyPwModifyTime = Boolean.parseBoolean( configuration.readAppProperty( AppProperty.TOKEN_VERIFY_PW_MODIFY_TIME ) );

        status = STATUS.OPEN;
        LOGGER.debug( "open" );
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
        catch ( PwmException e )
        {
            final String errorMsg = "unexpected error trying to store token in datastore: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation( e.getError(), errorMsg );
            throw new PwmOperationalException( errorInformation );
        }

        LOGGER.trace( sessionLabel, "generated token with payload: " + tokenPayload.toDebugString() );

        final AuditRecord auditRecord = new AuditRecordFactory( pwmApplication ).createUserAuditRecord(
                AuditEvent.TOKEN_ISSUED,
                tokenPayload.getUserIdentity(),
                sessionLabel,
                JsonUtil.serialize( tokenPayload )
        );
        pwmApplication.getAuditManager().submit( auditRecord );
        return tokenKey;
    }


    private void markTokenAsClaimed(
            final TokenKey tokenKey,
            final PwmSession pwmSession,
            final TokenPayload tokenPayload
    )
            throws PwmUnrecoverableException
    {
        if ( tokenPayload == null || tokenPayload.getUserIdentity() == null )
        {
            return;
        }

        final boolean removeOnClaim = Boolean.parseBoolean( configuration.readAppProperty( AppProperty.TOKEN_REMOVE_ON_CLAIM ) );

        if ( removeOnClaim )
        {
            try
            {
                LOGGER.trace( pwmSession, "removing claimed token: " + tokenPayload.toDebugString() );
                tokenMachine.removeToken( tokenKey );
            }
            catch ( PwmOperationalException e )
            {
                LOGGER.error( pwmSession, "error clearing claimed token: " + e.getMessage() );
            }
        }

        final AuditRecord auditRecord = new AuditRecordFactory( pwmApplication ).createUserAuditRecord(
                AuditEvent.TOKEN_CLAIMED,
                tokenPayload.getUserIdentity(),
                pwmSession.getLabel(),
                JsonUtil.serialize( tokenPayload )
        );
        pwmApplication.getAuditManager().submit( auditRecord );

        StatisticsManager.incrementStat( pwmApplication, Statistic.TOKENS_PASSSED );
    }

    public TokenPayload retrieveTokenData( final SessionLabel sessionLabel, final String tokenKey )
            throws PwmOperationalException
    {
        checkStatus();

        try
        {
            final TokenPayload storedToken = tokenMachine.retrieveToken( tokenMachine.keyFromKey( tokenKey ) );
            if ( storedToken != null )
            {

                if ( testIfTokenIsExpired( sessionLabel, storedToken ) )
                {
                    throw new PwmOperationalException( new ErrorInformation( PwmError.ERROR_TOKEN_EXPIRED ) );
                }

                return storedToken;
            }
        }
        catch ( PwmException e )
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

    public STATUS status( )
    {
        return status;
    }

    public void close( )
    {
        if ( executorService != null )
        {
            executorService.shutdown();
        }
        status = STATUS.CLOSED;
    }

    public List<HealthRecord> healthCheck( )
    {
        final List<HealthRecord> returnRecords = new ArrayList<>();

        if ( tokensAreUsedInConfig( configuration ) )
        {
            if ( errorInformation != null )
            {
                returnRecords.add( HealthRecord.forMessage( HealthMessage.CryptoTokenWithNewUserVerification, errorInformation.toDebugStr() ) );
            }
        }

        if ( storageMethod == TokenStorageMethod.STORE_LDAP )
        {
            if ( configuration.readSettingAsBoolean( PwmSetting.NEWUSER_ENABLE ) )
            {
                for ( final NewUserProfile newUserProfile : configuration.getNewUserProfiles().values() )
                {
                    if ( newUserProfile.readSettingAsBoolean( PwmSetting.NEWUSER_EMAIL_VERIFICATION ) )
                    {
                        final String label = PwmSetting.NEWUSER_EMAIL_VERIFICATION.toMenuLocationDebug( newUserProfile.getIdentifier(), PwmConstants.DEFAULT_LOCALE );
                        final String label2 = PwmSetting.TOKEN_STORAGEMETHOD.toMenuLocationDebug( null, PwmConstants.DEFAULT_LOCALE );
                        returnRecords.add( HealthRecord.forMessage( HealthMessage.CryptoTokenWithNewUserVerification, label, label2 ) );
                    }
                    if ( newUserProfile.readSettingAsBoolean( PwmSetting.NEWUSER_SMS_VERIFICATION ) )
                    {
                        final String label = PwmSetting.NEWUSER_SMS_VERIFICATION.toMenuLocationDebug( newUserProfile.getIdentifier(), PwmConstants.DEFAULT_LOCALE );
                        final String label2 = PwmSetting.TOKEN_STORAGEMETHOD.toMenuLocationDebug( null, PwmConstants.DEFAULT_LOCALE );
                        returnRecords.add( HealthRecord.forMessage( HealthMessage.CryptoTokenWithNewUserVerification, label, label2 ) );
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
            LOGGER.error( sessionLabel, "retrieved token has no issueDate, marking as expired: " + theToken.toDebugString() );
            return true;
        }
        if ( theToken.getExpiration() == null )
        {
            LOGGER.error( sessionLabel, "retrieved token has no expiration timestamp, marking as expired: " + theToken.toDebugString() );
            return true;
        }
        return theToken.getExpiration().isBefore( Instant.now() );
    }

    private static String makeRandomCode( final Configuration config )
    {
        final String randomChars = config.readSettingAsString( PwmSetting.TOKEN_CHARACTERS );
        final int codeLength = ( int ) config.readSettingAsLong( PwmSetting.TOKEN_LENGTH );
        final PwmRandom random = PwmRandom.getInstance();

        return random.alphaNumericString( randomChars, codeLength );
    }

    private class CleanerTask extends TimerTask
    {
        public void run( )
        {
            try
            {
                tokenMachine.cleanup();
            }
            catch ( Exception e )
            {
                LOGGER.warn( "unexpected error while cleaning expired stored tokens: " + e.getMessage(), e );
            }
        }
    }

    private void checkStatus( ) throws PwmOperationalException
    {
        if ( status != STATUS.OPEN )
        {
            throw new PwmOperationalException( new ErrorInformation( PwmError.ERROR_SERVICE_NOT_AVAILABLE, "token manager is not open" ) );
        }
    }

    public int size( ) throws PwmUnrecoverableException
    {
        if ( status != STATUS.OPEN )
        {
            return -1;
        }

        try
        {
            return tokenMachine.size();
        }
        catch ( Exception e )
        {
            LOGGER.error( "unexpected error reading size of token storage table: " + e.getMessage() );
        }

        return -1;
    }

    String makeUniqueTokenForMachine( final SessionLabel sessionLabel, final TokenMachine machine )
            throws PwmUnrecoverableException, PwmOperationalException
    {
        String tokenKey = null;
        int attempts = 0;
        final int maxUniqueCreateAttempts = Integer.parseInt( pwmApplication.getConfig().readAppProperty( AppProperty.TOKEN_MAX_UNIQUE_CREATE_ATTEMPTS ) );
        while ( tokenKey == null && attempts < maxUniqueCreateAttempts )
        {
            tokenKey = makeRandomCode( configuration );
            LOGGER.trace( sessionLabel, "generated new token random code, checking for uniqueness" );
            final TokenPayload existingPayload = machine.retrieveToken( tokenMachine.keyFromKey( tokenKey ) );
            if ( existingPayload != null )
            {
                tokenKey = null;
            }
            attempts++;
        }

        if ( tokenKey == null )
        {
            throw new PwmOperationalException( new ErrorInformation( PwmError.ERROR_UNKNOWN, "unable to generate a unique token key after " + attempts + " attempts" ) );
        }

        LOGGER.trace( sessionLabel, "created new unique random token value after " + attempts + " attempts" );
        return tokenKey;
    }

    private static boolean tokensAreUsedInConfig( final Configuration configuration )
    {
        if ( configuration.readSettingAsBoolean( PwmSetting.NEWUSER_ENABLE ) )
        {
            for ( final NewUserProfile newUserProfile : configuration.getNewUserProfiles().values() )
            {
                if ( newUserProfile.readSettingAsBoolean( PwmSetting.NEWUSER_EMAIL_VERIFICATION ) )
                {
                    return true;
                }
            }
            return true;
        }


        if ( configuration.readSettingAsBoolean( PwmSetting.ACTIVATE_USER_ENABLE ) )
        {
            final MessageSendMethod activateMethod = configuration.readSettingAsEnum( PwmSetting.ACTIVATE_TOKEN_SEND_METHOD, MessageSendMethod.class );
            if ( MessageSendMethod.NONE != activateMethod )
            {
                return true;
            }
        }

        if ( configuration.readSettingAsBoolean( PwmSetting.CHALLENGE_ENABLE ) )
        {
            for ( final ForgottenPasswordProfile forgottenPasswordProfile : configuration.getForgottenPasswordProfiles().values() )
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
        final String jsonPayload = JsonUtil.serialize( tokenPayload );
        return pwmApplication.getSecureService().encryptToString( jsonPayload );
    }

    TokenPayload fromEncryptedString( final String inputString )
            throws PwmOperationalException, PwmUnrecoverableException
    {
        final String deWhiteSpacedToken = inputString.replaceAll( "\\s", "" );
        try
        {
            final String decryptedString = pwmApplication.getSecureService().decryptStringValue( deWhiteSpacedToken );
            return JsonUtil.deserialize( decryptedString, TokenPayload.class );
        }
        catch ( PwmUnrecoverableException e )
        {
            final String errorMsg = "unable to decrypt token payload: " + e.getErrorInformation().toDebugStr();
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_TOKEN_INCORRECT, errorMsg );
            throw new PwmOperationalException( errorInformation );
        }
    }

    public ServiceInfoBean serviceInfo( )
    {
        return serviceInfo;
    }

    public TokenPayload processUserEnteredCode(
            final PwmSession pwmSession,
            final UserIdentity sessionUserIdentity,
            final TokenType tokenType,
            final String userEnteredCode,
            final TokenEntryType tokenEntryType
    )
            throws PwmOperationalException, PwmUnrecoverableException
    {
        try
        {
            final TokenPayload tokenPayload = processUserEnteredCodeImpl(
                    pwmSession,
                    sessionUserIdentity,
                    tokenType,
                    userEnteredCode
            );
            if ( tokenPayload.getDestination() != null && !StringUtil.isEmpty( tokenPayload.getDestination().getValue() ) )
            {
                pwmApplication.getIntruderManager().clear( RecordType.TOKEN_DEST, tokenPayload.getDestination().getValue() );
            }
            markTokenAsClaimed( tokenMachine.keyFromKey( userEnteredCode ), pwmSession, tokenPayload );
            return tokenPayload;
        }
        catch ( Exception e )
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

            LOGGER.debug( pwmSession, errorInformation.toDebugStr() );

            if ( sessionUserIdentity != null && tokenEntryType == TokenEntryType.unauthenticated )
            {
                final SessionAuthenticator sessionAuthenticator = new SessionAuthenticator( pwmApplication, pwmSession, null );
                sessionAuthenticator.simulateBadPassword( sessionUserIdentity );
                pwmApplication.getIntruderManager().convenience().markUserIdentity( sessionUserIdentity, pwmSession );
            }
            pwmApplication.getIntruderManager().convenience().markAddressAndSession( pwmSession );
            pwmApplication.getStatisticsManager().incrementValue( Statistic.RECOVERY_FAILURES );
            throw new PwmOperationalException( errorInformation );
        }
    }

    private TokenPayload processUserEnteredCodeImpl(
            final PwmSession pwmSession,
            final UserIdentity sessionUserIdentity,
            final TokenType tokenType,
            final String userEnteredCode
    )
            throws PwmOperationalException, PwmUnrecoverableException
    {
        final TokenPayload tokenPayload;
        try
        {
            tokenPayload = pwmApplication.getTokenService().retrieveTokenData( pwmSession.getLabel(), userEnteredCode );
        }
        catch ( PwmOperationalException e )
        {
            final String errorMsg = "unexpected error attempting to read token from storage: " + e.getErrorInformation().toDebugStr();
            throw new PwmOperationalException( PwmError.ERROR_TOKEN_INCORRECT, errorMsg );
        }

        if ( tokenPayload == null )
        {
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_TOKEN_INCORRECT, "token not found" );
            throw new PwmOperationalException( errorInformation );
        }

        LOGGER.trace( pwmSession, "retrieved tokenPayload: " + tokenPayload.toDebugString() );

        if ( tokenType != null && pwmApplication.getTokenService().supportsName() )
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
            if ( !tokenPayload.getUserIdentity().canonicalEquals( sessionUserIdentity, pwmApplication ) )
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
                        pwmApplication,
                        pwmSession.getLabel(),
                        tokenPayload.getUserIdentity() );

                final String dateStringInToken = tokenPayload.getData().get( PwmConstants.TOKEN_KEY_PWD_CHG_DATE );

                LOGGER.trace( pwmSession, "tokenPayload=" + tokenPayload.toDebugString()
                        + ", sessionUser=" + ( sessionUserIdentity == null ? "null" : sessionUserIdentity.toDisplayString() )
                        + ", payloadUserIdentity=" + tokenPayload.getUserIdentity().toDisplayString()
                        + ", userLastPasswordChange=" + JavaHelper.toIsoDate( userLastPasswordChange )
                        + ", dateStringInToken=" + dateStringInToken );

                if ( userLastPasswordChange != null && dateStringInToken != null )
                {

                    final String userChangeString = JavaHelper.toIsoDate( userLastPasswordChange );

                    if ( !dateStringInToken.equalsIgnoreCase( userChangeString ) )
                    {
                        final String errorString = "user password has changed since token issued, token rejected;"
                                + " currentValue=" + userChangeString + ", tokenValue=" + dateStringInToken;
                        LOGGER.trace( pwmSession, errorString + "; token=" + tokenPayload.toDebugString() );
                        final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_TOKEN_EXPIRED, errorString );
                        throw new PwmOperationalException( errorInformation );
                    }
                }
            }
            catch ( ChaiUnavailableException | PwmUnrecoverableException e )
            {
                final String errorMsg = "unexpected error reading user's last password change time while validating token: " + e.getMessage();
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_TOKEN_INCORRECT, errorMsg );
                throw new PwmOperationalException( errorInformation );
            }
        }

        LOGGER.debug( pwmSession, "token validation has been passed" );
        return tokenPayload;
    }

    @Value
    @Builder
    public static class TokenSendInfo
    {
        private PwmApplication pwmApplication;
        private UserInfo userInfo;
        private MacroMachine macroMachine;
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

            final PwmApplication pwmApplication = tokenSendInfo.getPwmApplication();
            pwmApplication.getStatisticsManager().incrementValue( Statistic.TOKENS_SENT );
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

            final PwmApplication pwmApplication = tokenSendInfo.getPwmApplication();
            pwmApplication.getIntruderManager().mark( RecordType.TOKEN_DEST, toAddress, null );

            final EmailItemBean configuredEmailSetting = tokenSendInfo.getConfiguredEmailSetting();
            pwmApplication.getEmailQueue().submitEmailImmediate( new EmailItemBean(
                    toAddress,
                    configuredEmailSetting.getFrom(),
                    configuredEmailSetting.getSubject(),
                    configuredEmailSetting.getBodyPlain().replace( "%TOKEN%", tokenSendInfo.getTokenKey() ),
                    configuredEmailSetting.getBodyHtml().replace( "%TOKEN%", tokenSendInfo.getTokenKey() )
            ), tokenSendInfo.getUserInfo(), tokenSendInfo.getMacroMachine() );
            LOGGER.debug( "token email added to send queue for " + toAddress );
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

            final PwmApplication pwmApplication = tokenSendInfo.getPwmApplication();
            pwmApplication.getIntruderManager().mark( RecordType.TOKEN_DEST, smsNumber, tokenSendInfo.getSessionLabel() );

            pwmApplication.sendSmsUsingQueue( smsNumber, modifiedMessage, tokenSendInfo.getSessionLabel(), tokenSendInfo.getMacroMachine() );
            LOGGER.debug( "token SMS added to send queue for " + smsNumber );
            return true;
        }
        }

    static TimeDuration maxTokenAge( final Configuration configuration )
    {
        long maxValue = 0;
        maxValue = Math.max( maxValue, configuration.readSettingAsLong( PwmSetting.TOKEN_LIFETIME ) );
        maxValue = Math.max( maxValue, configuration.readSettingAsLong( PwmSetting.TOKEN_LIFETIME ) );
        for ( NewUserProfile newUserProfile : configuration.getNewUserProfiles().values() )
        {
            maxValue = Math.max( maxValue, newUserProfile.readSettingAsLong( PwmSetting.NEWUSER_TOKEN_LIFETIME_EMAIL ) );
            maxValue = Math.max( maxValue, newUserProfile.readSettingAsLong( PwmSetting.NEWUSER_TOKEN_LIFETIME_SMS ) );
        }
        return new TimeDuration( maxValue, TimeUnit.SECONDS );
    }
}
