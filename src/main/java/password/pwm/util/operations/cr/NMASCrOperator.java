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

package password.pwm.util.operations.cr;

import com.novell.ldap.LDAPConnection;
import com.novell.ldap.LDAPException;
import com.novell.ldapchai.ChaiFactory;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.cr.*;
import com.novell.ldapchai.cr.bean.ChallengeBean;
import com.novell.ldapchai.exception.*;
import com.novell.ldapchai.impl.edir.NmasCrFactory;
import com.novell.ldapchai.impl.edir.NmasResponseSet;
import com.novell.ldapchai.provider.*;
import com.novell.security.nmas.client.*;
import com.novell.security.nmas.lcm.*;
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
import password.pwm.util.Helper;
import password.pwm.util.JsonUtil;
import password.pwm.util.PasswordData;
import password.pwm.util.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.*;
import java.io.IOException;
import java.io.Serializable;
import java.io.StringReader;
import java.security.Security;
import java.util.*;

public class NMASCrOperator implements CrOperator {
    private static final PwmLogger LOGGER = PwmLogger.forClass(NMASCrOperator.class);

    private int threadCounter = 0;
    private final List<NMASSessionThread> sessionMonitorThreads = Collections.synchronizedList(new ArrayList<NMASSessionThread>());
    private final PwmApplication pwmApplication;
    private final TimeDuration maxThreadIdleTime;
    private final int maxThreadCount;


    private volatile Timer timer;

    private static final Map<String,Object> CR_OPTIONS_MAP;
    static {
        final HashMap<String,Object> crOptionsMap = new HashMap<>();
        crOptionsMap.put("com.novell.security.sasl.client.pkgs", "com.novell.sasl.client");
        crOptionsMap.put("javax.security.sasl.client.pkgs", "com.novell.sasl.client");
        crOptionsMap.put("LoginSequence", "Challenge Response");
        CR_OPTIONS_MAP = Collections.unmodifiableMap(crOptionsMap);

        Security.addProvider(new com.novell.sasl.client.NovellSaslProvider());
    }

    public NMASCrOperator(PwmApplication pwmApplication) {
        this.pwmApplication = pwmApplication;
        maxThreadCount = Integer.parseInt(pwmApplication.getConfig().readAppProperty(AppProperty.NMAS_THREADS_MAX_COUNT));
        final int MAX_SECONDS = Integer.parseInt(pwmApplication.getConfig().readAppProperty(AppProperty.NMAS_THREADS_MAX_SECONDS));
        final int MIN_SECONDS = Integer.parseInt(pwmApplication.getConfig().readAppProperty(AppProperty.NMAS_THREADS_MIN_SECONDS));

        int maxNmasIdleSeconds = (int)pwmApplication.getConfig().readSettingAsLong(PwmSetting.IDLE_TIMEOUT_SECONDS);
        if (maxNmasIdleSeconds > MAX_SECONDS) {
            maxNmasIdleSeconds = MAX_SECONDS;
        } else if (maxNmasIdleSeconds < MIN_SECONDS) {
            maxNmasIdleSeconds = MIN_SECONDS;
        }
        maxThreadIdleTime = new TimeDuration(maxNmasIdleSeconds * 1000);
    }

    private void controlWatchdogThread() {
        synchronized (sessionMonitorThreads) {
            if (sessionMonitorThreads.isEmpty()) {
                final Timer localTimer = timer;
                if (localTimer != null) {
                    LOGGER.debug("discontinuing NMASCrOperator watchdog timer, no active threads");
                    localTimer.cancel();
                    timer = null;
                }
            } else {
                if (timer == null) {
                    LOGGER.debug("starting NMASCrOperator watchdog timer, maxIdleThreadTime=" + maxThreadIdleTime.asCompactString());
                    timer = new Timer(PwmConstants.PWM_APP_NAME + "-NMASCrOperator watchdog timer",true);
                    final long frequency = Long.parseLong(pwmApplication.getConfig().readAppProperty(AppProperty.NMAS_THREADS_WATCHDOG_FREQUENCY));
                    timer.schedule(new ThreadWatchdogTask(),frequency,frequency);
                }
            }
        }
    }

    public void close() {
        final List<NMASSessionThread> threads = new ArrayList<>(sessionMonitorThreads);
        for (final NMASSessionThread thread : threads) {
            LOGGER.debug("killing thread due to NMASCrOperator service closing: " + thread.toDebugString());
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
        pwmApplication.getIntruderManager().convenience().checkUserIdentity(userIdentity);

        try {
            if (theUser.getChaiProvider().getDirectoryVendor() != ChaiProvider.DIRECTORY_VENDOR.NOVELL_EDIRECTORY) {
                return null;
            }

            final ResponseSet responseSet = new NMASCRResponseSet(pwmApplication, userIdentity);
            if (responseSet.getChallengeSet() == null) {
                return null;
            }
            return responseSet;
        } catch (PwmException e) {
            throw new PwmUnrecoverableException(e.getErrorInformation());
        } catch (Exception e) {
            final String errorMsg = "unexpected error loading NMAS responses: " + e.getMessage();
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_RESPONSES_NORESPONSES,errorMsg));
        }
    }

    @Override
    public ResponseInfoBean readResponseInfo(ChaiUser theUser, final UserIdentity userIdentity, String userGUID)
            throws PwmUnrecoverableException
    {
        try {
            if (theUser.getChaiProvider().getDirectoryVendor() != ChaiProvider.DIRECTORY_VENDOR.NOVELL_EDIRECTORY) {
                LOGGER.debug("skipping request to read NMAS responses for " + userIdentity + ", directory type is not eDirectory");
                return null;
            }

            final ResponseSet responseSet = NmasCrFactory.readNmasResponseSet(theUser);
            if (responseSet == null) {
                return null;
            }
            final ResponseInfoBean responseInfoBean = CrOperators.convertToNoAnswerInfoBean(responseSet,DataStorageMethod.NMAS);
            responseInfoBean.setTimestamp(null);
            return responseInfoBean;
        } catch (ChaiException e) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_RESPONSES_NORESPONSES,"unexpected error reading response info " + e.getMessage()));
        }
    }

    public void clearResponses(
            final ChaiUser theUser,
            final String user
    )
            throws PwmUnrecoverableException
    {
        try {
            if (theUser.getChaiProvider().getDirectoryVendor() == ChaiProvider.DIRECTORY_VENDOR.NOVELL_EDIRECTORY) {
                NmasCrFactory.clearResponseSet(theUser);
                LOGGER.info("cleared responses for user " + theUser.getEntryDN() + " using NMAS method ");
            }
        } catch (ChaiException e) {
            final String errorMsg = "error clearing responses from nmas: " + e.getMessage();
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_CLEARING_RESPONSES, errorMsg);
            final PwmUnrecoverableException pwmOE = new PwmUnrecoverableException(errorInfo);
            pwmOE.initCause(e);
            throw pwmOE;
        }
    }

    public void writeResponses(
            final ChaiUser theUser,
            final String userGuid,
            final ResponseInfoBean responseInfoBean
    )
            throws PwmUnrecoverableException
    {
        try {
            if (theUser.getChaiProvider().getDirectoryVendor() == ChaiProvider.DIRECTORY_VENDOR.NOVELL_EDIRECTORY) {

                final NmasResponseSet nmasResponseSet = NmasCrFactory.newNmasResponseSet(
                        responseInfoBean.getCrMap(),
                        responseInfoBean.getLocale(),
                        responseInfoBean.getMinRandoms(),
                        theUser,
                        responseInfoBean.getCsIdentifier()
                );
                NmasCrFactory.writeResponseSet(nmasResponseSet);
                LOGGER.info("saved responses for user using NMAS method ");
            }
        } catch (ChaiException e) {
            final String errorMsg = "error writing responses to nmas: " + e.getMessage();
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_WRITING_RESPONSES, errorMsg);
            final PwmUnrecoverableException pwmOE = new PwmUnrecoverableException(errorInfo);
            pwmOE.initCause(e);
            throw pwmOE;
        }
    }

    private static ChallengeSet questionsToChallengeSet(final List<String> questions) throws ChaiValidationException {
        if (questions == null || questions.isEmpty()) {
            return null;
        }
        final List<Challenge> challenges = new ArrayList<>();
        for (final String question : questions) {
            challenges.add(new ChaiChallenge(true, question, 1, 256, true, 0, false));
        }
        return new ChaiChallengeSet(challenges,challenges.size(), PwmConstants.DEFAULT_LOCALE,"NMAS-LDAP ChallengeResponse Set");
    }

    private static List<String> documentToQuestions(Document doc) throws XPathExpressionException {
        final XPath xpath = XPathFactory.newInstance().newXPath();
        final XPathExpression challengesExpr = xpath.compile("/Challenges/Challenge/text()");
        final NodeList challenges = (NodeList)challengesExpr.evaluate(doc, XPathConstants.NODESET);
        final List<String> res = new ArrayList<>();
        for (int i = 0; i < challenges.getLength(); ++i) {
            String question = challenges.item(i).getTextContent();
            res.add(question);
        }
        return Collections.unmodifiableList(res);
    }

    private static Document answersToDocument(final List<String> answers)
            throws ParserConfigurationException, IOException, SAXException {
        final StringBuilder xml = new StringBuilder();
        xml.append("<Responses>");
        for(int i = 0; i < answers.size(); i++) {
            xml.append("<Response index=\"").append(i + 1).append("\">");
            xml.append("<![CDATA[").append(answers.get(i)).append("]]>");
            xml.append("</Response>");
        }
        xml.append("</Responses>");
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new InputSource(new StringReader(xml.toString())));
    }

    public class NMASCRResponseSet implements ResponseSet, Serializable {
        private final PwmApplication pwmApplication;
        private final UserIdentity userIdentity;

        final private ChaiConfiguration chaiConfiguration;
        private ChallengeSet challengeSet;
        private transient NMASResponseSession ldapChallengeSession;
        boolean passed;

        private NMASCRResponseSet(PwmApplication pwmApplication, final UserIdentity userIdentity)
                throws Exception
        {
            this.pwmApplication = pwmApplication;
            this.userIdentity = userIdentity;

            final LdapProfile ldapProfile = pwmApplication.getConfig().getLdapProfiles().get(userIdentity.getLdapProfileID());

            final Configuration config = pwmApplication.getConfig();
            final List<String> ldapURLs = ldapProfile.readSettingAsStringArray(PwmSetting.LDAP_SERVER_URLS);
            final String proxyDN = ldapProfile.readSettingAsString(PwmSetting.LDAP_PROXY_USER_DN);
            final PasswordData proxyPW = ldapProfile.readSettingAsPassword(PwmSetting.LDAP_PROXY_USER_PASSWORD);
            chaiConfiguration = LdapOperationsHelper.createChaiConfiguration(config, ldapProfile, ldapURLs, proxyDN,
                    proxyPW);
            chaiConfiguration.setSetting(ChaiSetting.PROVIDER_IMPLEMENTATION, JLDAPProviderImpl.class.getName());

            cycle();
        }

        private void cycle() throws Exception {
            if (ldapChallengeSession != null) {
                ldapChallengeSession.close();
                ldapChallengeSession = null;
            }
            final LDAPConnection ldapConnection = makeLdapConnection();
            ldapChallengeSession = new NMASResponseSession(userIdentity.getUserDN(),ldapConnection);
            final List<String> questions = ldapChallengeSession.getQuestions();
            challengeSet = questionsToChallengeSet(questions);
        }

        private LDAPConnection makeLdapConnection() throws Exception {
            final ChaiProvider chaiProvider = ChaiProviderFactory.createProvider(chaiConfiguration);
            final ChaiUser theUser = ChaiFactory.createChaiUser(userIdentity.getUserDN(), chaiProvider);
            try {
                if (theUser.isPasswordLocked()) {
                    LOGGER.trace("user " + theUser.getEntryDN() + " appears to be intruder locked, aborting nmas ResponseSet loading" );
                    throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_INTRUDER_LDAP,"nmas account is intruder locked-out"));
                } else if (!theUser.isAccountEnabled()) {
                    throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_RESPONSES_NORESPONSES,"nmas account is disabled"));
                }
            } catch (ChaiException e) {
                throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_RESPONSES_NORESPONSES,"unable to evaluate nmas account status attributes"));
            }
            return (LDAPConnection)((ChaiProviderImplementor)chaiProvider).getConnectionObject();
        }

        public ChallengeSet getChallengeSet() throws ChaiValidationException {
            if (passed) {
                throw new IllegalStateException("validation has already been passed");
            }
            return challengeSet;
        }

        public ChallengeSet getPresentableChallengeSet() throws ChaiValidationException {
            return getChallengeSet();
        }

        public boolean meetsChallengeSetRequirements(ChallengeSet challengeSet) throws ChaiValidationException {
            if (challengeSet.getRequiredChallenges().size() > this.getChallengeSet().getRequiredChallenges().size()) {
                LOGGER.debug("failed meetsChallengeSetRequirements, not enough required challenge");
                return false;
            }

            for (final Challenge loopChallenge : challengeSet.getRequiredChallenges()) {
                if (loopChallenge.isAdminDefined()) {
                    if (!this.getChallengeSet().getChallengeTexts().contains(loopChallenge.getChallengeText())) {
                        LOGGER.debug("failed meetsChallengeSetRequirements, missing required challenge text: '" + loopChallenge.getChallengeText() + "'");
                        return false;
                    }
                }
            }

            if (challengeSet.getMinRandomRequired() > 0) {
                if (this.getChallengeSet().getChallenges().size() < challengeSet.getMinRandomRequired()) {
                    LOGGER.debug("failed meetsChallengeSetRequirements, not enough questions to meet minrandom; minRandomRequired=" + challengeSet.getMinRandomRequired() + ", ChallengesInSet=" + this.getChallengeSet().getChallenges().size());
                    return false;
                }
            }

            return true;
        }

        public String stringValue() throws UnsupportedOperationException, ChaiOperationException {
            throw new UnsupportedOperationException("not supported");
        }

        public boolean test(Map<Challenge, String> challengeStringMap)
                throws ChaiUnavailableException
        {
            if (passed) {
                throw new IllegalStateException("test may not be called after success returned");
            }
            final List<String> answers = new ArrayList<>(challengeStringMap == null ? Collections.<String>emptyList() : challengeStringMap.values());
            if (answers.isEmpty() || answers.size() < challengeSet.minimumResponses()) {
                return false;
            }
            for (final String answer : answers) {
                if (answer == null || answer.length() < 1) {
                    return false;
                }
            }
            try {
                passed = ldapChallengeSession.testAnswers(answers);
            } catch (Exception e) {
                LOGGER.error("error testing responses: " + e.getMessage());
            }
            if (!passed) {
                try {
                    cycle();
                    pwmApplication.getIntruderManager().convenience().checkUserIdentity(userIdentity);
                    if (challengeSet == null) {
                        final String errorMsg = "unable to load next challenge set";
                        throw new ChaiUnavailableException(errorMsg, ChaiError.UNKNOWN);
                    }
                } catch (PwmException e) {
                    final String errorMsg = "error reading next challenges after testing responses: " + e.getMessage();
                    LOGGER.error("error reading next challenges after testing responses: " + e.getMessage());
                    ChaiUnavailableException chaiUnavailableException = new ChaiUnavailableException(errorMsg,ChaiError.UNKNOWN);
                    chaiUnavailableException.initCause(e);
                    throw chaiUnavailableException;
                } catch (Exception e) {
                    final String errorMsg = "error reading next challenges after testing responses: " + e.getMessage();
                    LOGGER.error("error reading next challenges after testing responses: " + e.getMessage());
                    throw new ChaiUnavailableException(errorMsg,ChaiError.UNKNOWN);
                }
            } else {
                ldapChallengeSession.close();
            }
            return passed;
        }

        @Override
        public Locale getLocale() throws ChaiUnavailableException, IllegalStateException, ChaiOperationException {
            return PwmConstants.DEFAULT_LOCALE;
        }

        @Override
        public Date getTimestamp() throws ChaiUnavailableException, IllegalStateException, ChaiOperationException {
            return null;
        }

        @Override
        public Map<Challenge, String> getHelpdeskResponses() {
            return Collections.emptyMap();
        }

        @Override
        public List<ChallengeBean> asChallengeBeans(boolean b) {
            return Collections.emptyList();
        }

        @Override
        public List<ChallengeBean> asHelpdeskChallengeBeans(boolean b) {
            return Collections.emptyList();
        }
    }

    private class NMASResponseSession {

        private LDAPConnection ldapConnection;
        final private GenLcmUI lcmEnv;
        private NMASSessionThread nmasSessionThread;

        public NMASResponseSession(String userDN, LDAPConnection ldapConnection) throws LCMRegistryException, PwmUnrecoverableException {
            this.ldapConnection = ldapConnection;
            lcmEnv = new GenLcmUI();
            final GenLCMRegistry lcmRegistry = new GenLCMRegistry();
            lcmRegistry.registerLcm("com.novell.security.nmas.lcm.chalresp.XmlChalRespLCM");

            nmasSessionThread = new NMASSessionThread(this);
            final ChalRespCallbackHandler cbh = new ChalRespCallbackHandler(lcmEnv, lcmRegistry);
            nmasSessionThread.startLogin(userDN, ldapConnection, cbh);
        }

        public List<String> getQuestions() throws XPathExpressionException {

            final LCMUserPrompt prompt = lcmEnv.getNextUserPrompt();
            if (prompt == null) {
                return null;
            }
            final Document doc = prompt.getLcmXmlDataDoc();
            return documentToQuestions(doc);
        }

        public boolean testAnswers(List<String> answers)
                throws SAXException, IOException, ParserConfigurationException, PwmUnrecoverableException
        {
            if (nmasSessionThread.getLoginState() == NMASThreadState.ABORTED) {
                throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_DIRECTORY_UNAVAILABLE,"nmas ldap connection has been disconnected or timed out"));
            }

            final Document doc = answersToDocument(answers);
            lcmEnv.setUserResponse(new LCMUserResponse(doc));
            final NMASLoginResult loginResult = nmasSessionThread.getLoginResult();
            final boolean result = loginResult.getNmasRetCode() == 0;
            if (result) {
                ldapConnection = loginResult.getLdapConnection();
            }
            return result;
        }

        public void close() {
            if (this.ldapConnection != null) {
                try {
                    this.ldapConnection.disconnect();
                } catch (LDAPException e) {
                    LOGGER.error("error closing ldap connection: " + e.getMessage(), e);
                }
                this.ldapConnection = null;
            }
        }

        private class ChalRespCallbackHandler extends NMASCallbackHandler
        {
            public ChalRespCallbackHandler(LCMEnvironment lcmenvironment, LCMRegistry lcmregistry)
            {
                super(lcmenvironment, lcmregistry);
            }

            public void handle(final Callback[] callbacks) throws UnsupportedCallbackException
            {
                for (final Callback callback : callbacks) {
                    if (callback instanceof NMASCompletionCallback) {
                        LOGGER.trace("received NMASCompletionCallback, ignoring");
                    } else if (callback instanceof NMASCallback) {
                        try {
                            handleNMASCallback((NMASCallback) callback);
                        } catch (InvalidNMASCallbackException e) {
                            LOGGER.error("error processing NMASCallback: " + e.getMessage(),e);
                        }
                    } else if (callback instanceof LCMUserPromptCallback) {
                        try {
                            handleLCMUserPromptCallback((LCMUserPromptCallback) callback);
                        } catch (LCMUserPromptException e) {
                            LOGGER.error("error processing LCMUserPromptCallback: " + e.getMessage(),e);
                        }
                    } else {
                        throw new UnsupportedCallbackException(callback);
                    }
                }
            }

            public int awaitRetCode() {
                final Date startTime = new Date();
                boolean done = this.isNmasDone();
                Date lastLogTime = new Date();
                while (!done && TimeDuration.fromCurrent(startTime).isShorterThan(maxThreadIdleTime)) {
                    LOGGER.trace("attempt to read return code, but isNmasDone=false, will await completion");
                    Helper.pause(10);
                    done = this.isNmasDone();
                    if (TimeDuration.SECOND.isLongerThan(TimeDuration.fromCurrent(lastLogTime))) {
                        LOGGER.trace("waiting for return code: " + TimeDuration.fromCurrent(startTime).asCompactString());
                        lastLogTime = new Date();
                    }
                }
                LOGGER.debug("read return code in " + TimeDuration.fromCurrent(startTime).asCompactString());
                return this.getNmasRetCode();
            }
        }
    }

    private enum NMASThreadState { NEW, BIND, COMPLETED, ABORTED, }

    private class NMASSessionThread extends Thread {
        private volatile Date lastActivityTimestamp = new Date();
        private volatile NMASThreadState loginState = NMASThreadState.NEW;
        private volatile boolean loginResultReady = false;
        private volatile NMASLoginResult loginResult = null;
        private volatile NMASResponseSession.ChalRespCallbackHandler callbackHandler = null;
        private volatile LDAPConnection ldapConn = null;
        private volatile String loginDN = null;
        private final NMASResponseSession nmasResponseSession;

        private final int threadID;

        public NMASSessionThread(final NMASResponseSession nmasResponseSession)
        {
            this.nmasResponseSession = nmasResponseSession;
            this.threadID = threadCounter++;
            setLoginState(NMASThreadState.NEW);
        }

        private void setLoginState(NMASThreadState paramInt)
        {
            this.loginState = paramInt;
        }

        public NMASThreadState getLoginState()
        {
            return this.loginState;
        }

        public Date getLastActivityTimestamp() {
            return lastActivityTimestamp;
        }

        private synchronized void setLoginResult(NMASLoginResult paramNMASLoginResult)
        {
            this.loginResult = paramNMASLoginResult;
            this.loginResultReady = true;
            this.lastActivityTimestamp = new Date();
        }

        public final synchronized NMASLoginResult getLoginResult()
        {
            while (!this.loginResultReady) {
                try {
                    wait();
                } catch (Exception localException) {
                    /* noop */
                }
            }

            lastActivityTimestamp = new Date();
            return this.loginResult;
        }

        public void startLogin(
                String userDN,
                LDAPConnection ldapConnection,
                NMASResponseSession.ChalRespCallbackHandler  paramCallbackHandler
        )
                throws PwmUnrecoverableException
        {
            if (sessionMonitorThreads.size() > maxThreadCount) {
                throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_TOO_MANY_THREADS,"NMASSessionMonitor maximum thread count (" + maxThreadCount + ") exceeded"));
            }
            this.loginDN = userDN;
            this.ldapConn = ldapConnection;
            this.callbackHandler = paramCallbackHandler;
            this.loginResultReady = false;
            setLoginState(NMASThreadState.NEW);
            setDaemon(true);
            setName(PwmConstants.PWM_APP_NAME + "-NMASSessionThread thread id=" + threadID);
            lastActivityTimestamp = new Date();
            start();
        }

        public void run()
        {
            try {
                LOGGER.trace("starting NMASSessionThread, activeCount=" + sessionMonitorThreads.size() + ", " + this.toDebugString());
                sessionMonitorThreads.add(this);
                controlWatchdogThread();
                doLoginSequence();
            } finally {
                sessionMonitorThreads.remove(this);
                controlWatchdogThread();
                LOGGER.trace("exiting NMASSessionThread, activeCount=" + sessionMonitorThreads.size() + ", " + this.toDebugString());
            }
        }

        private void doLoginSequence() {
            if (loginState == NMASThreadState.ABORTED || loginState == NMASThreadState.COMPLETED) {
                return;
            }
            lastActivityTimestamp = new Date();
            if (this.ldapConn == null)
            {
                setLoginState(NMASThreadState.COMPLETED);
                setLoginResult(new NMASLoginResult(-1681));
                lastActivityTimestamp = new Date();
                return;
            }

            try
            {
                setLoginState(NMASThreadState.BIND);
                lastActivityTimestamp = new Date();
                try {
                    this.ldapConn.bind(
                            this.loginDN,
                            "dn:" + this.loginDN,
                            new String[] { "NMAS_LOGIN" },
                            new HashMap<>(CR_OPTIONS_MAP),
                            this.callbackHandler
                    );
                } catch (NullPointerException e) {
                    LOGGER.error("NullPointer error during CallBackHandler-NMASCR-bind; this is usually the result of an ldap disconnection, thread=" + this.toDebugString());
                    this.setLoginState(NMASThreadState.ABORTED);
                    return;

                }

                if (loginState == NMASThreadState.ABORTED) {
                    return;
                }

                setLoginState(NMASThreadState.COMPLETED);
                lastActivityTimestamp = new Date();
                setLoginResult(new NMASLoginResult(this.callbackHandler.awaitRetCode(), this.ldapConn));
                lastActivityTimestamp = new Date();
            }
            catch (LDAPException e)
            {
                if (loginState == NMASThreadState.ABORTED) {
                    return;
                }
                final String ldapErrorMessage = e.getLDAPErrorMessage();
                if (ldapErrorMessage != null) {
                    LOGGER.error("NMASLoginMonitor: LDAP error (" + ldapErrorMessage + ")");
                } else {
                    LOGGER.error("NMASLoginMonitor: LDAPException " + e.toString());
                }
                setLoginState(NMASThreadState.COMPLETED);
                final NMASLoginResult localNMASLoginResult = new NMASLoginResult(this.callbackHandler.awaitRetCode(), e);
                setLoginResult(localNMASLoginResult);
            }
            lastActivityTimestamp = new Date();
        }

        public void abort() {
            setLoginState(NMASThreadState.ABORTED);
            setLoginResult(new NMASLoginResult(-1681));

            try {
                this.notify();
            } catch (Exception e) {
                /* ignore */
            }

            try {
                this.nmasResponseSession.lcmEnv.setUserResponse(null);
            } catch (Exception e) {
                LOGGER.trace("error during NMASResponseSession abort: " + e.getMessage(),e);
            }
        }

        public String toDebugString() {
            final TreeMap<String,String> debugInfo = new TreeMap<>();
            debugInfo.put("loginDN", this.loginDN);
            debugInfo.put("id",Integer.toString(threadID));
            debugInfo.put("loginState", this.getLoginState().toString());
            debugInfo.put("loginResultReady",Boolean.toString(this.loginResultReady));
            debugInfo.put("idleTime", TimeDuration.fromCurrent(this.getLastActivityTimestamp()).asCompactString());

            return "NMASSessionThread: " + JsonUtil.serialize(debugInfo);
        }
    }

    private class ThreadWatchdogTask extends TimerTask {
        @Override
        public void run() {
            logThreadInfo();
            final List<NMASSessionThread> threads = new ArrayList<>(sessionMonitorThreads);
            for (final NMASSessionThread thread : threads) {
                final TimeDuration idleTime = TimeDuration.fromCurrent(thread.getLastActivityTimestamp());
                if (idleTime.isLongerThan(maxThreadIdleTime)) {
                    LOGGER.debug("killing thread due to inactivity " + thread.toDebugString());
                    thread.abort();
                }
            }
        }


        private void logThreadInfo() {
            final List<NMASSessionThread> threads = new ArrayList<>(sessionMonitorThreads);
            final StringBuilder threadDebugInfo = new StringBuilder();
            threadDebugInfo.append("NMASCrOperator watchdog timer, activeCount=").append(threads.size());
            threadDebugInfo.append(", maxIdleThreadTime=").append(maxThreadIdleTime.asCompactString());
            for (final NMASSessionThread thread : threads) {
                threadDebugInfo.append("\n ").append(thread.toDebugString());
            }
            LOGGER.trace(threadDebugInfo.toString());
        }

    }
}

