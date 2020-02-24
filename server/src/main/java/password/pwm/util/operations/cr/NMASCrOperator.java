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

package password.pwm.util.operations.cr;

import com.novell.ldap.LDAPConnection;
import com.novell.ldap.LDAPException;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.cr.ChaiChallenge;
import com.novell.ldapchai.cr.ChaiChallengeSet;
import com.novell.ldapchai.cr.Challenge;
import com.novell.ldapchai.cr.ChallengeSet;
import com.novell.ldapchai.cr.ResponseSet;
import com.novell.ldapchai.cr.bean.ChallengeBean;
import com.novell.ldapchai.exception.ChaiError;
import com.novell.ldapchai.exception.ChaiException;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.exception.ChaiValidationException;
import com.novell.ldapchai.impl.edir.NmasCrFactory;
import com.novell.ldapchai.impl.edir.NmasResponseSet;
import com.novell.ldapchai.provider.ChaiConfiguration;
import com.novell.ldapchai.provider.ChaiProvider;
import com.novell.ldapchai.provider.ChaiProviderFactory;
import com.novell.ldapchai.provider.ChaiProviderImplementor;
import com.novell.ldapchai.provider.ChaiSetting;
import com.novell.ldapchai.provider.DirectoryVendor;
import com.novell.ldapchai.provider.JLDAPProviderImpl;
import com.novell.security.nmas.NMASConstants;
import com.novell.security.nmas.client.NMASCallback;
import com.novell.security.nmas.client.NMASCompletionCallback;
import com.novell.security.nmas.lcm.LCMEnvironment;
import com.novell.security.nmas.lcm.LCMUserPrompt;
import com.novell.security.nmas.lcm.LCMUserPromptCallback;
import com.novell.security.nmas.lcm.LCMUserPromptException;
import com.novell.security.nmas.lcm.LCMUserResponse;
import com.novell.security.nmas.lcm.registry.GenLCMRegistry;
import com.novell.security.nmas.lcm.registry.LCMRegistry;
import com.novell.security.nmas.lcm.registry.LCMRegistryException;
import com.novell.security.nmas.ui.GenLcmUI;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.ResponseInfoBean;
import password.pwm.bean.UserIdentity;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.config.profile.LdapProfile;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.ldap.LdapOperationsHelper;
import password.pwm.util.PasswordData;
import password.pwm.util.java.AtomicLoopIntIncrementer;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslClientFactory;
import javax.security.sasl.SaslException;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.io.StringReader;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.Provider;
import java.security.Security;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;

public class NMASCrOperator implements CrOperator
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( NMASCrOperator.class );

    private final AtomicLoopIntIncrementer threadCounter = new AtomicLoopIntIncrementer();
    private final List<NMASSessionThread> sessionMonitorThreads = Collections.synchronizedList( new ArrayList<NMASSessionThread>() );
    private final PwmApplication pwmApplication;
    private final TimeDuration maxThreadIdleTime;
    private final int maxThreadCount;


    private volatile Timer timer;

    private Provider saslProvider;

    private static final Map<String, Object> CR_OPTIONS_MAP;

    static
    {
        final HashMap<String, Object> crOptionsMap = new HashMap<>();
        crOptionsMap.put( "com.novell.security.sasl.client.pkgs", "com.novell.sasl.client" );
        crOptionsMap.put( "javax.security.sasl.client.pkgs", "com.novell.sasl.client" );
        crOptionsMap.put( "LoginSequence", "Challenge Response" );
        CR_OPTIONS_MAP = Collections.unmodifiableMap( crOptionsMap );
    }

    public NMASCrOperator( final PwmApplication pwmApplication )
    {
        this.pwmApplication = pwmApplication;
        maxThreadCount = Integer.parseInt( pwmApplication.getConfig().readAppProperty( AppProperty.NMAS_THREADS_MAX_COUNT ) );
        final int maxSeconds = Integer.parseInt( pwmApplication.getConfig().readAppProperty( AppProperty.NMAS_THREADS_MAX_SECONDS ) );
        final int minSeconds = Integer.parseInt( pwmApplication.getConfig().readAppProperty( AppProperty.NMAS_THREADS_MIN_SECONDS ) );

        int maxNmasIdleSeconds = ( int ) pwmApplication.getConfig().readSettingAsLong( PwmSetting.IDLE_TIMEOUT_SECONDS );
        if ( maxNmasIdleSeconds > maxSeconds )
        {
            maxNmasIdleSeconds = maxSeconds;
        }
        else if ( maxNmasIdleSeconds < minSeconds )
        {
            maxNmasIdleSeconds = minSeconds;
        }
        maxThreadIdleTime = TimeDuration.of( maxNmasIdleSeconds, TimeDuration.Unit.SECONDS );

        registerSaslProvider();
    }

    private void registerSaslProvider( )
    {
        final boolean forceRegistration = Boolean.parseBoolean( pwmApplication.getConfig().readAppProperty( AppProperty.NMAS_FORCE_SASL_FACTORY_REGISTRATION ) );

        if ( Security.getProvider( NMASCrPwmSaslProvider.SASL_PROVIDER_NAME ) != null )
        {
            if ( forceRegistration )
            {
                LOGGER.warn( () -> "SASL provider '" + NMASCrPwmSaslProvider.SASL_PROVIDER_NAME + "' is already defined, however forcing registration due to app property "
                        + AppProperty.NMAS_FORCE_SASL_FACTORY_REGISTRATION.getKey() + " value" );
            }
            else
            {
                LOGGER.warn( () -> "SASL provider '" + NMASCrPwmSaslProvider.SASL_PROVIDER_NAME + "' is already defined, skipping SASL factory registration" );
                return;
            }
        }
        else
        {
            LOGGER.trace( () -> "pre-existing SASL provider for " + NMASCrPwmSaslProvider.SASL_PROVIDER_NAME + " has not been detected" );
        }

        final boolean useLocalProvider = Boolean.parseBoolean( pwmApplication.getConfig().readAppProperty( AppProperty.NMAS_USE_LOCAL_SASL_FACTORY ) );

        try
        {
            if ( useLocalProvider )
            {
                LOGGER.trace( () -> "registering built-in local SASL provider" );
                saslProvider = new NMASCrPwmSaslProvider();
            }
            else
            {
                LOGGER.trace( () -> "registering NMAS library SASL provider" );
                saslProvider = new com.novell.sasl.client.NovellSaslProvider();
            }
            LOGGER.trace( () -> "initialized security provider " + saslProvider.getClass().getName() );
        }
        catch ( final Throwable t )
        {
            LOGGER.warn( () -> "unable to create SASL provider, error: " + t.getMessage(), t );
        }

        if ( saslProvider != null )
        {
            try
            {
                Security.addProvider( saslProvider );
            }
            catch ( final Exception e )
            {
                LOGGER.warn( () -> "error registering security provider" );
            }
        }
    }

    private void unregisterSaslProvider( )
    {
        if ( saslProvider != null )
        {
            saslProvider = null;
            try
            {
                Security.removeProvider( NMASCrPwmSaslProvider.SASL_PROVIDER_NAME );
            }
            catch ( final Exception e )
            {
                LOGGER.warn( () -> "error removing provider " + NMASCrPwmSaslProvider.SASL_PROVIDER_NAME + ", error: " + e.getMessage() );
            }
        }
    }

    private void controlWatchdogThread( )
    {
        synchronized ( sessionMonitorThreads )
        {
            if ( sessionMonitorThreads.isEmpty() )
            {
                final Timer localTimer = timer;
                if ( localTimer != null )
                {
                    LOGGER.debug( () -> "discontinuing NMASCrOperator watchdog timer, no active threads" );
                    localTimer.cancel();
                    timer = null;
                }
            }
            else
            {
                if ( timer == null )
                {
                    LOGGER.debug( () -> "starting NMASCrOperator watchdog timer, maxIdleThreadTime=" + maxThreadIdleTime.asCompactString() );
                    timer = new Timer( PwmConstants.PWM_APP_NAME + "-NMASCrOperator watchdog timer", true );
                    final long frequency = Long.parseLong( pwmApplication.getConfig().readAppProperty( AppProperty.NMAS_THREADS_WATCHDOG_FREQUENCY ) );
                    final boolean debugOutput = Boolean.parseBoolean( pwmApplication.getConfig().readAppProperty( AppProperty.NMAS_THREADS_WATCHDOG_DEBUG ) );
                    timer.schedule( new ThreadWatchdogTask( debugOutput ), frequency, frequency );
                }
            }
        }
    }

    public void close( )
    {
        unregisterSaslProvider();

        final List<NMASSessionThread> threads = new ArrayList<>( sessionMonitorThreads );
        for ( final NMASSessionThread thread : threads )
        {
            LOGGER.debug( () -> "killing thread due to NMASCrOperator service closing: " + thread.toDebugString() );
            thread.abort();
        }
    }

    public ResponseSet readResponseSet(
            final ChaiUser theUser,
            final UserIdentity userIdentity,
            final String userGuid
    )
            throws PwmUnrecoverableException
    {
        pwmApplication.getIntruderManager().convenience().checkUserIdentity( userIdentity );

        try
        {
            if ( theUser.getChaiProvider().getDirectoryVendor() != DirectoryVendor.EDIRECTORY )
            {
                return null;
            }

            final ResponseSet responseSet = new NMASCRResponseSet( pwmApplication, userIdentity );
            if ( responseSet.getChallengeSet() == null )
            {
                return null;
            }
            return responseSet;
        }
        catch ( final PwmException e )
        {
            throw new PwmUnrecoverableException( e.getErrorInformation() );
        }
        catch ( final Exception e )
        {
            final String errorMsg = "unexpected error loading NMAS responses: " + e.getMessage();
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_RESPONSES_NORESPONSES, errorMsg ) );
        }
    }

    @Override
    public ResponseInfoBean readResponseInfo( final ChaiUser theUser, final UserIdentity userIdentity, final String userGUID )
            throws PwmUnrecoverableException
    {
        try
        {
            if ( theUser.getChaiProvider().getDirectoryVendor() != DirectoryVendor.EDIRECTORY )
            {
                LOGGER.debug( () -> "skipping request to read NMAS responses for " + userIdentity + ", directory type is not eDirectory" );
                return null;
            }

            final ResponseSet responseSet = NmasCrFactory.readNmasResponseSet( theUser );
            if ( responseSet == null )
            {
                return null;
            }
            final ResponseInfoBean responseInfoBean = CrOperators.convertToNoAnswerInfoBean( responseSet, DataStorageMethod.NMAS );
            responseInfoBean.setTimestamp( null );
            return responseInfoBean;
        }
        catch ( final ChaiException e )
        {
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_RESPONSES_NORESPONSES, "unexpected error reading response info " + e.getMessage() ) );
        }
    }

    public void clearResponses(
            final UserIdentity userIdentity, final ChaiUser theUser,
            final String user
    )
            throws PwmUnrecoverableException
    {
        try
        {
            if ( theUser.getChaiProvider().getDirectoryVendor() == DirectoryVendor.EDIRECTORY )
            {
                NmasCrFactory.clearResponseSet( theUser );
                LOGGER.info( () -> "cleared responses for user " + theUser.getEntryDN() + " using NMAS method " );
            }
        }
        catch ( final ChaiException e )
        {
            final String errorMsg = "error clearing responses from nmas: " + e.getMessage();
            final ErrorInformation errorInfo = new ErrorInformation( PwmError.ERROR_CLEARING_RESPONSES, errorMsg );
            final PwmUnrecoverableException pwmOE = new PwmUnrecoverableException( errorInfo );
            pwmOE.initCause( e );
            throw pwmOE;
        }
    }

    public void writeResponses(
            final UserIdentity userIdentity, final ChaiUser theUser,
            final String userGuid,
            final ResponseInfoBean responseInfoBean
    )
            throws PwmUnrecoverableException
    {
        try
        {
            if ( theUser.getChaiProvider().getDirectoryVendor() == DirectoryVendor.EDIRECTORY )
            {

                final NmasResponseSet nmasResponseSet = NmasCrFactory.newNmasResponseSet(
                        responseInfoBean.getCrMap(),
                        responseInfoBean.getLocale(),
                        responseInfoBean.getMinRandoms(),
                        theUser,
                        responseInfoBean.getCsIdentifier()
                );
                NmasCrFactory.writeResponseSet( nmasResponseSet );
                LOGGER.info( () -> "saved responses for user using NMAS method " );
            }
        }
        catch ( final ChaiException e )
        {
            final String errorMsg = "error writing responses to nmas: " + e.getMessage();
            final ErrorInformation errorInfo = new ErrorInformation( PwmError.ERROR_WRITING_RESPONSES, errorMsg );
            final PwmUnrecoverableException pwmOE = new PwmUnrecoverableException( errorInfo );
            pwmOE.initCause( e );
            throw pwmOE;
        }
    }

    private static ChallengeSet questionsToChallengeSet( final List<String> questions ) throws ChaiValidationException
    {
        if ( questions == null || questions.isEmpty() )
        {
            return null;
        }
        final List<Challenge> challenges = new ArrayList<>();
        for ( final String question : questions )
        {
            challenges.add( new ChaiChallenge( true, question, 1, 256, true, 0, false ) );
        }
        return new ChaiChallengeSet( challenges, challenges.size(), PwmConstants.DEFAULT_LOCALE, "NMAS-LDAP ChallengeResponse Set" );
    }

    private static List<String> documentToQuestions( final Document doc ) throws XPathExpressionException
    {
        final XPath xpath = XPathFactory.newInstance().newXPath();
        final XPathExpression challengesExpr = xpath.compile( "/Challenges/Challenge/text()" );
        final NodeList challenges = ( NodeList ) challengesExpr.evaluate( doc, XPathConstants.NODESET );
        final List<String> res = new ArrayList<>();
        for ( int i = 0; i < challenges.getLength(); ++i )
        {
            final String question = challenges.item( i ).getTextContent();
            res.add( question );
        }
        return Collections.unmodifiableList( res );
    }

    private static Document answersToDocument( final List<String> answers )
            throws ParserConfigurationException, IOException, SAXException
    {
        final StringBuilder xml = new StringBuilder();
        xml.append( "<Responses>" );
        for ( int i = 0; i < answers.size(); i++ )
        {
            xml.append( "<Response index=\"" ).append( i + 1 ).append( "\">" );
            xml.append( "<![CDATA[" ).append( answers.get( i ) ).append( "]]>" );
            xml.append( "</Response>" );
        }
        xml.append( "</Responses>" );
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse( new InputSource( new StringReader( xml.toString() ) ) );
    }

    public class NMASCRResponseSet implements ResponseSet
    {
        private final PwmApplication pwmApplication;
        private final UserIdentity userIdentity;

        private final ChaiConfiguration chaiConfiguration;
        private ChallengeSet challengeSet;
        private transient NMASResponseSession ldapChallengeSession;
        boolean passed;

        private NMASCRResponseSet( final PwmApplication pwmApplication, final UserIdentity userIdentity )
                throws Exception
        {
            this.pwmApplication = pwmApplication;
            this.userIdentity = userIdentity;

            final LdapProfile ldapProfile = pwmApplication.getConfig().getLdapProfiles().get( userIdentity.getLdapProfileID() );

            final Configuration config = pwmApplication.getConfig();
            final List<String> ldapURLs = ldapProfile.readSettingAsStringArray( PwmSetting.LDAP_SERVER_URLS );
            final String proxyDN = ldapProfile.readSettingAsString( PwmSetting.LDAP_PROXY_USER_DN );
            final PasswordData proxyPW = ldapProfile.readSettingAsPassword( PwmSetting.LDAP_PROXY_USER_PASSWORD );
            final ChaiConfiguration newChaiConfig = LdapOperationsHelper.createChaiConfiguration( config, ldapProfile, ldapURLs, proxyDN,
                    proxyPW );

            chaiConfiguration = ChaiConfiguration.builder( newChaiConfig )
                    .setSetting( ChaiSetting.PROVIDER_IMPLEMENTATION, JLDAPProviderImpl.class.getName() )
                    .build();

            cycle();
        }

        private void cycle( ) throws Exception
        {
            if ( ldapChallengeSession != null )
            {
                ldapChallengeSession.close();
                ldapChallengeSession = null;
            }
            final LDAPConnection ldapConnection = makeLdapConnection();
            ldapChallengeSession = new NMASResponseSession( userIdentity.getUserDN(), ldapConnection );
            final List<String> questions = ldapChallengeSession.getQuestions();
            challengeSet = questionsToChallengeSet( questions );
        }

        private LDAPConnection makeLdapConnection( ) throws Exception
        {
            final ChaiProviderFactory chaiProviderFactory = pwmApplication.getLdapConnectionService().getChaiProviderFactory();
            final ChaiProvider chaiProvider = chaiProviderFactory.newProvider( chaiConfiguration );
            final ChaiUser theUser = chaiProvider.getEntryFactory().newChaiUser( userIdentity.getUserDN() );
            try
            {
                if ( theUser.isPasswordLocked() )
                {
                    LOGGER.trace( () -> "user " + theUser.getEntryDN() + " appears to be intruder locked, aborting nmas ResponseSet loading" );
                    throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_INTRUDER_LDAP, "nmas account is intruder locked-out" ) );
                }
                else if ( !theUser.isAccountEnabled() )
                {
                    throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_RESPONSES_NORESPONSES, "nmas account is disabled" ) );
                }
            }
            catch ( final ChaiException e )
            {
                throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_RESPONSES_NORESPONSES, "unable to evaluate nmas account status attributes" ) );
            }
            return ( LDAPConnection ) ( ( ChaiProviderImplementor ) chaiProvider ).getConnectionObject();
        }

        public ChallengeSet getChallengeSet( ) throws ChaiValidationException
        {
            if ( passed )
            {
                throw new IllegalStateException( "validation has already been passed" );
            }
            return challengeSet;
        }

        public ChallengeSet getPresentableChallengeSet( ) throws ChaiValidationException
        {
            return getChallengeSet();
        }

        public boolean meetsChallengeSetRequirements( final ChallengeSet challengeSet ) throws ChaiValidationException
        {
            if ( challengeSet.getRequiredChallenges().size() > this.getChallengeSet().getRequiredChallenges().size() )
            {
                LOGGER.debug( () -> "failed meetsChallengeSetRequirements, not enough required challenge" );
                return false;
            }

            for ( final Challenge loopChallenge : challengeSet.getRequiredChallenges() )
            {
                if ( loopChallenge.isAdminDefined() )
                {
                    if ( !this.getChallengeSet().getChallengeTexts().contains( loopChallenge.getChallengeText() ) )
                    {
                        LOGGER.debug( () -> "failed meetsChallengeSetRequirements, missing required challenge text: '" + loopChallenge.getChallengeText() + "'" );
                        return false;
                    }
                }
            }

            if ( challengeSet.getMinRandomRequired() > 0 )
            {
                if ( this.getChallengeSet().getChallenges().size() < challengeSet.getMinRandomRequired() )
                {
                    final int challengesInSet = challengeSet.getChallenges().size();
                    LOGGER.debug( () -> "failed meetsChallengeSetRequirements, not enough questions to meet minrandom; minRandomRequired="
                            + challengeSet.getMinRandomRequired() + ", ChallengesInSet=" + challengesInSet );
                    return false;
                }
            }

            return true;
        }

        public String stringValue( ) throws UnsupportedOperationException, ChaiOperationException
        {
            throw new UnsupportedOperationException( "not supported" );
        }

        public boolean test( final Map<Challenge, String> challengeStringMap )
                throws ChaiUnavailableException
        {
            if ( passed )
            {
                throw new IllegalStateException( "test may not be called after success returned" );
            }
            final List<String> answers = new ArrayList<>( challengeStringMap == null ? Collections.<String>emptyList() : challengeStringMap.values() );
            if ( answers.isEmpty() || answers.size() < challengeSet.minimumResponses() )
            {
                return false;
            }
            for ( final String answer : answers )
            {
                if ( answer == null || answer.length() < 1 )
                {
                    return false;
                }
            }
            try
            {
                passed = ldapChallengeSession.testAnswers( answers );
            }
            catch ( final Exception e )
            {
                LOGGER.error( () -> "error testing responses: " + e.getMessage() );
            }
            if ( !passed )
            {
                try
                {
                    cycle();
                    pwmApplication.getIntruderManager().convenience().checkUserIdentity( userIdentity );
                    if ( challengeSet == null )
                    {
                        final String errorMsg = "unable to load next challenge set";
                        throw new ChaiUnavailableException( errorMsg, ChaiError.UNKNOWN );
                    }
                }
                catch ( final PwmException e )
                {
                    final String errorMsg = "error reading next challenges after testing responses: " + e.getMessage();
                    LOGGER.error( () -> "error reading next challenges after testing responses: " + e.getMessage() );
                    final ChaiUnavailableException chaiUnavailableException = new ChaiUnavailableException( errorMsg, ChaiError.UNKNOWN );
                    chaiUnavailableException.initCause( e );
                    throw chaiUnavailableException;
                }
                catch ( final Exception e )
                {
                    final String errorMsg = "error reading next challenges after testing responses: " + e.getMessage();
                    LOGGER.error( () -> "error reading next challenges after testing responses: " + e.getMessage() );
                    throw new ChaiUnavailableException( errorMsg, ChaiError.UNKNOWN );
                }
            }
            else
            {
                ldapChallengeSession.close();
            }
            return passed;
        }

        @Override
        public Locale getLocale( ) throws ChaiUnavailableException, IllegalStateException, ChaiOperationException
        {
            return PwmConstants.DEFAULT_LOCALE;
        }

        @Override
        public Date getTimestamp( ) throws ChaiUnavailableException, IllegalStateException, ChaiOperationException
        {
            return null;
        }

        @Override
        public Map<Challenge, String> getHelpdeskResponses( )
        {
            return Collections.emptyMap();
        }

        @Override
        public List<ChallengeBean> asChallengeBeans( final boolean b )
        {
            return Collections.emptyList();
        }

        @Override
        public List<ChallengeBean> asHelpdeskChallengeBeans( final boolean b )
        {
            return Collections.emptyList();
        }
    }

    private class NMASResponseSession
    {

        private LDAPConnection ldapConnection;
        private final GenLcmUI lcmEnv;
        private NMASSessionThread nmasSessionThread;
        private boolean completeOnUnsupportedFailure = false;

        NMASResponseSession( final String userDN, final LDAPConnection ldapConnection ) throws LCMRegistryException, PwmUnrecoverableException
        {
            this.ldapConnection = ldapConnection;
            lcmEnv = new GenLcmUI();
            final GenLCMRegistry lcmRegistry = new GenLCMRegistry();
            lcmRegistry.registerLcm( "com.novell.security.nmas.lcm.chalresp.XmlChalRespLCM" );

            nmasSessionThread = new NMASSessionThread( this );
            final ChalRespCallbackHandler cbh = new ChalRespCallbackHandler( lcmEnv, lcmRegistry );
            nmasSessionThread.startLogin( userDN, ldapConnection, cbh );
        }

        public List<String> getQuestions( ) throws XPathExpressionException
        {

            final LCMUserPrompt prompt = lcmEnv.getNextUserPrompt();
            if ( prompt == null )
            {
                return null;
            }
            final Document doc = prompt.getLcmXmlDataDoc();
            return documentToQuestions( doc );
        }

        public boolean testAnswers( final List<String> answers )
                throws SAXException, IOException, ParserConfigurationException, PwmUnrecoverableException
        {
            if ( nmasSessionThread.getLoginState() == NMASThreadState.ABORTED )
            {
                throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_DIRECTORY_UNAVAILABLE, "nmas ldap connection has been disconnected or timed out" ) );
            }

            final Document doc = answersToDocument( answers );
            lcmEnv.setUserResponse( new LCMUserResponse( doc ) );
            final com.novell.security.nmas.client.NMASLoginResult loginResult = nmasSessionThread.getLoginResult();
            final boolean result = loginResult.getNmasRetCode() == 0;
            if ( result )
            {
                ldapConnection = loginResult.getLdapConnection();
            }
            return result;
        }

        public void close( )
        {
            if ( this.ldapConnection != null )
            {
                try
                {
                    this.ldapConnection.disconnect();
                }
                catch ( final LDAPException e )
                {
                    LOGGER.error( () -> "error closing ldap connection: " + e.getMessage(), e );
                }
                this.ldapConnection = null;
            }
        }

        private boolean unsupportedCallbackHasOccurred = false;

        private class ChalRespCallbackHandler extends com.novell.security.nmas.client.NMASCallbackHandler
        {
            ChalRespCallbackHandler( final LCMEnvironment lcmenvironment, final LCMRegistry lcmregistry )
            {
                super( lcmenvironment, lcmregistry );
            }

            public void handle( final Callback[] callbacks ) throws UnsupportedCallbackException
            {
                LOGGER.trace( () -> "entering ChalRespCallbackHandler.handle()" );
                for ( final Callback callback : callbacks )
                {
                    final String callbackClassname = callback.getClass().getName();
                    LOGGER.trace( () -> "evaluating callback: " + callback.toString() + ", class=" + callbackClassname );

                    // note in some cases instanceof check fails due to classloader issues, using getName string comparison instead
                    if ( NMASCompletionCallback.class.getName().equals( callbackClassname ) )
                    {
                        LOGGER.trace( () -> "received NMASCompletionCallback, ignoring" );
                    }
                    else if ( NMASCallback.class.getName().equals( callbackClassname ) )
                    {
                        LOGGER.trace( () -> "callback is instance of NMASCompletionCallback, calling handleNMASCallback()" );
                        try
                        {
                            handleNMASCallback( ( NMASCallback ) callback );
                        }
                        catch ( final com.novell.security.nmas.client.InvalidNMASCallbackException e )
                        {
                            LOGGER.error( () -> "error processing NMASCallback: " + e.getMessage(), e );
                        }
                    }
                    else if ( LCMUserPromptCallback.class.getName().equals( callbackClassname ) )
                    {
                        LOGGER.trace( () -> "callback is instance of LCMUserPromptCallback, calling handleLCMUserPromptCallback()" );
                        try
                        {
                            handleLCMUserPromptCallback( ( LCMUserPromptCallback ) callback );
                        }
                        catch ( final LCMUserPromptException e )
                        {
                            LOGGER.error( () -> "error processing LCMUserPromptCallback: " + e.getMessage(), e );
                        }
                    }
                    else
                    {
                        unsupportedCallbackHasOccurred = true;
                        LOGGER.trace( () -> "throwing UnsupportedCallbackException for " + callback.toString() + ", class=" + callback.getClass().getName() );
                        throw new UnsupportedCallbackException( callback );
                    }
                }
            }

            public int awaitRetCode( )
            {
                final Instant startTime = Instant.now();
                boolean done = this.isNmasDone();
                Instant lastLogTime = Instant.now();
                while ( !done && TimeDuration.fromCurrent( startTime ).isShorterThan( maxThreadIdleTime ) )
                {
                    LOGGER.trace( () -> "attempt to read return code, but isNmasDone=false, will await completion" );
                    TimeDuration.of( 10, TimeDuration.Unit.SECONDS ).pause();
                    if ( completeOnUnsupportedFailure )
                    {
                        done = unsupportedCallbackHasOccurred || this.isNmasDone();
                    }
                    else
                    {
                        done = this.isNmasDone();
                    }
                    if ( TimeDuration.SECOND.isLongerThan( TimeDuration.fromCurrent( lastLogTime ) ) )
                    {
                        LOGGER.trace( () -> "waiting for return code: " + TimeDuration.fromCurrent( startTime ).asCompactString()
                                + " unsupportedCallbackHasOccurred=" + unsupportedCallbackHasOccurred );
                        lastLogTime = Instant.now();
                    }
                }
                LOGGER.debug( () -> "read return code in " + TimeDuration.fromCurrent( startTime ).asCompactString() );
                return this.getNmasRetCode();
            }
        }
    }

    private enum NMASThreadState
    {
        NEW, BIND, COMPLETED, ABORTED,
    }

    private class NMASSessionThread extends Thread
    {
        private volatile Instant lastActivityTimestamp = Instant.now();
        private volatile NMASThreadState loginState = NMASThreadState.NEW;
        private volatile boolean loginResultReady = false;
        private volatile com.novell.security.nmas.client.NMASLoginResult loginResult = null;
        private volatile NMASResponseSession.ChalRespCallbackHandler callbackHandler = null;
        private volatile LDAPConnection ldapConn = null;
        private volatile String loginDN = null;
        private final NMASResponseSession nmasResponseSession;

        private final int threadID;

        NMASSessionThread( final NMASResponseSession nmasResponseSession )
        {
            this.nmasResponseSession = nmasResponseSession;
            this.threadID = threadCounter.next();
            setLoginState( NMASThreadState.NEW );
        }

        private void setLoginState( final NMASThreadState paramInt )
        {
            this.loginState = paramInt;
        }

        public NMASThreadState getLoginState( )
        {
            return this.loginState;
        }

        public Instant getLastActivityTimestamp( )
        {
            return lastActivityTimestamp;
        }

        private synchronized void setLoginResult( final com.novell.security.nmas.client.NMASLoginResult paramNMASLoginResult )
        {
            this.loginResult = paramNMASLoginResult;
            this.loginResultReady = true;
            this.lastActivityTimestamp = Instant.now();
        }

        public final synchronized com.novell.security.nmas.client.NMASLoginResult getLoginResult( )
        {
            while ( !this.loginResultReady )
            {
                try
                {
                    wait();
                }
                catch ( final Exception localException )
                {
                    /* noop */
                }
            }

            lastActivityTimestamp = Instant.now();
            return this.loginResult;
        }

        public void startLogin(
                final String userDN,
                final LDAPConnection ldapConnection,
                final NMASResponseSession.ChalRespCallbackHandler paramCallbackHandler
        )
                throws PwmUnrecoverableException
        {
            if ( sessionMonitorThreads.size() > maxThreadCount )
            {
                throw new PwmUnrecoverableException( new ErrorInformation(
                        PwmError.ERROR_TOO_MANY_THREADS,
                        "NMASSessionMonitor maximum thread count (" + maxThreadCount + ") exceeded" ) );
            }
            this.loginDN = userDN;
            this.ldapConn = ldapConnection;
            this.callbackHandler = paramCallbackHandler;
            this.loginResultReady = false;
            setLoginState( NMASThreadState.NEW );
            setDaemon( true );
            setName( PwmConstants.PWM_APP_NAME + "-NMASSessionThread thread id=" + threadID );
            lastActivityTimestamp = Instant.now();
            start();
        }

        public void run( )
        {
            try
            {
                LOGGER.trace( () -> "starting NMASSessionThread, activeCount=" + sessionMonitorThreads.size() + ", " + this.toDebugString() );
                sessionMonitorThreads.add( this );
                controlWatchdogThread();
                doLoginSequence();
            }
            finally
            {
                sessionMonitorThreads.remove( this );
                controlWatchdogThread();
                LOGGER.trace( () -> "exiting NMASSessionThread, activeCount=" + sessionMonitorThreads.size() + ", " + this.toDebugString() );
            }
        }

        private void doLoginSequence( )
        {
            if ( loginState == NMASThreadState.ABORTED || loginState == NMASThreadState.COMPLETED )
            {
                return;
            }
            lastActivityTimestamp = Instant.now();
            if ( this.ldapConn == null )
            {
                setLoginState( NMASThreadState.COMPLETED );
                setLoginResult( new com.novell.security.nmas.client.NMASLoginResult( NMASConstants.NMAS_E_TRANSPORT ) );
                lastActivityTimestamp = Instant.now();
                return;
            }

            try
            {
                setLoginState( NMASThreadState.BIND );
                lastActivityTimestamp = Instant.now();
                try
                {
                    this.ldapConn.bind(
                            this.loginDN,
                            "dn:" + this.loginDN,
                            new String[]
                                    {
                                            "NMAS_LOGIN",
                                    },
                            new HashMap<>( CR_OPTIONS_MAP ),
                            this.callbackHandler
                    );
                }
                catch ( final NullPointerException e )
                {
                    LOGGER.error( () -> "NullPointer error during CallBackHandler-NMASCR-bind; "
                            + "this is usually the result of an ldap disconnection, thread=" + this.toDebugString() );
                    this.setLoginState( NMASThreadState.ABORTED );
                    return;

                }

                if ( loginState == NMASThreadState.ABORTED )
                {
                    return;
                }

                setLoginState( NMASThreadState.COMPLETED );
                lastActivityTimestamp = Instant.now();
                setLoginResult( new com.novell.security.nmas.client.NMASLoginResult( this.callbackHandler.awaitRetCode(), this.ldapConn ) );
                lastActivityTimestamp = Instant.now();
            }
            catch ( final LDAPException e )
            {
                if ( loginState == NMASThreadState.ABORTED )
                {
                    return;
                }
                final String ldapErrorMessage = e.getLDAPErrorMessage();
                if ( ldapErrorMessage != null )
                {
                    LOGGER.error( () -> "NMASLoginMonitor: LDAP error (" + ldapErrorMessage + ")" );
                }
                else
                {
                    LOGGER.error( () -> "NMASLoginMonitor: LDAPException " + e.toString() );
                }
                setLoginState( NMASThreadState.COMPLETED );
                final com.novell.security.nmas.client.NMASLoginResult localNMASLoginResult
                        = new com.novell.security.nmas.client.NMASLoginResult( this.callbackHandler.awaitRetCode(), e );
                setLoginResult( localNMASLoginResult );
            }
            lastActivityTimestamp = Instant.now();
        }

        public void abort( )
        {
            setLoginState( NMASThreadState.ABORTED );
            setLoginResult( new com.novell.security.nmas.client.NMASLoginResult( NMASConstants.NMAS_E_TRANSPORT ) );

            try
            {
                this.notify();
            }
            catch ( final Exception e )
            {
                /* ignore */
            }

            try
            {
                this.nmasResponseSession.lcmEnv.setUserResponse( null );
            }
            catch ( final Exception e )
            {
                LOGGER.trace( () -> "error during NMASResponseSession abort: " + e.getMessage() );
            }
        }

        public String toDebugString( )
        {
            final TreeMap<String, String> debugInfo = new TreeMap<>();
            debugInfo.put( "loginDN", this.loginDN );
            debugInfo.put( "id", Integer.toString( threadID ) );
            debugInfo.put( "loginState", this.getLoginState().toString() );
            debugInfo.put( "loginResultReady", Boolean.toString( this.loginResultReady ) );
            debugInfo.put( "idleTime", TimeDuration.fromCurrent( this.getLastActivityTimestamp() ).asCompactString() );

            return "NMASSessionThread: " + JsonUtil.serialize( debugInfo );
        }
    }

    private class ThreadWatchdogTask extends TimerTask
    {

        private final boolean debugOutput;

        ThreadWatchdogTask( final boolean debugOutput )
        {
            this.debugOutput = debugOutput;
        }

        @Override
        public void run( )
        {
            if ( debugOutput )
            {
                logThreadInfo();
            }

            final List<NMASSessionThread> threads = new ArrayList<>( sessionMonitorThreads );
            for ( final NMASSessionThread thread : threads )
            {
                final TimeDuration idleTime = TimeDuration.fromCurrent( thread.getLastActivityTimestamp() );
                if ( idleTime.isLongerThan( maxThreadIdleTime ) )
                {
                    LOGGER.debug( () -> "killing thread due to inactivity " + thread.toDebugString() );
                    thread.abort();
                }
            }
        }


        private void logThreadInfo( )
        {
            final List<NMASSessionThread> threads = new ArrayList<>( sessionMonitorThreads );
            final StringBuilder threadDebugInfo = new StringBuilder();
            threadDebugInfo.append( "NMASCrOperator watchdog timer, activeCount=" ).append( threads.size() );
            threadDebugInfo.append( ", maxIdleThreadTime=" ).append( maxThreadIdleTime.asCompactString() );
            for ( final NMASSessionThread thread : threads )
            {
                threadDebugInfo.append( "\n " ).append( thread.toDebugString() );
            }
            LOGGER.trace( () -> threadDebugInfo.toString() );
        }

    }

    /**
     * This SASL Provider is a replacement for ldap.jar!/com/novell/sasl/client/NovellSaslProvider.class.  The primary
     * difference is that it registers <code>{@link NMASCrPwmSaslFactory}</code> as the factory instead of
     * ldap-2013.04.26.jar!/com/novell/sasl/client/ClientFactory.class
     */
    public static class NMASCrPwmSaslProvider extends Provider
    {
        private static final PwmLogger LOGGER = PwmLogger.forClass( NMASCrPwmSaslProvider.class );
        public static final String SASL_PROVIDER_NAME = "NMAS_LOGIN";

        private static final String INFO = "PWM NMAS Sasl Provider";

        public NMASCrPwmSaslProvider( )
        {
            super( "SaslClientFactory", 1.1, INFO );
            final NMASCrPwmSaslProvider thisInstance = NMASCrPwmSaslProvider.this;
            AccessController.doPrivileged( new PrivilegedAction()
            {
                public Object run( )
                {
                    try
                    {
                        final String saslFactoryName = password.pwm.util.operations.cr.NMASCrOperator.NMASCrPwmSaslFactory.class.getName();
                        thisInstance.put( "SaslClientFactory." + SASL_PROVIDER_NAME, saslFactoryName );
                    }
                    catch ( final SecurityException e )
                    {
                        LOGGER.warn( () -> "error registering " + NMASCrPwmSaslProvider.class.getSimpleName() + " SASL Provider, error: " + e.getMessage(), e );
                    }

                    return null;
                }
            } );
        }
    }

    /**
     * This SASL Client Factory is a replacement for ldap.jar!/com/novell/sasl/client/ClientFactory.class.  It's only difference with
     * that class is that it uses a threadlocal classloader to load a backing NMAS Sasl Client.  The default factory uses a static reference
     * to create a new SaslClient, which causes issues with tomcat and multiple classloaders.
     */
    public static class NMASCrPwmSaslFactory implements SaslClientFactory
    {
        private static final PwmLogger LOGGER = PwmLogger.forClass( NMASCrPwmSaslFactory.class );

        public NMASCrPwmSaslFactory( )
        {
            LOGGER.debug( () -> "initializing NMASCrPwmSaslFactory instance" );
        }

        @Override
        public SaslClient createSaslClient(
                final String[] mechanisms,
                final String authorizationId,
                final String protocol,
                final String serverName,
                final Map<String, ?> props,
                final CallbackHandler cbh
        )
                throws SaslException
        {
            try
            {
                LOGGER.trace( () -> "creating new SASL Client instance" );
                final SaslClientFactory realFactory = getRealSaslClientFactory();
                return realFactory.createSaslClient( mechanisms, authorizationId, protocol, serverName, props, cbh );
            }
            catch ( final Throwable t )
            {
                LOGGER.error( () -> "error creating backing sasl factory: " + t.getMessage(), t );
            }
            return null;
        }

        private SaslClientFactory getRealSaslClientFactory( ) throws IllegalAccessException, InstantiationException, ClassNotFoundException
        {
            final String className = "com.novell.sasl.client.ClientFactory";
            final ClassLoader threadLocalClassLoader = Thread.currentThread().getContextClassLoader();
            final Class threadLocalClass = threadLocalClassLoader.loadClass( className );
            return ( SaslClientFactory ) threadLocalClass.newInstance();
        }

        @Override
        public String[] getMechanismNames( final Map<String, ?> props )
        {
            try
            {
                final SaslClientFactory realFactory = getRealSaslClientFactory();
                return realFactory.getMechanismNames( props );
            }
            catch ( final Throwable t )
            {
                LOGGER.error( () -> "error creating backing sasl factory: " + t.getMessage(), t );
            }
            return new String[ 0 ];
        }
    }
}
