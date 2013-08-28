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
import com.novell.security.nmas.ui.NMASTrace;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.PwmSession;
import password.pwm.SessionManager;
import password.pwm.bean.ResponseInfoBean;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.error.*;
import password.pwm.util.Helper;
import password.pwm.util.PwmLogger;
import password.pwm.ws.client.novell.pwdmgt.*;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.rpc.Stub;
import javax.xml.xpath.*;
import java.io.IOException;
import java.io.Serializable;
import java.io.StringReader;
import java.net.URL;
import java.rmi.RemoteException;
import java.security.Security;
import java.util.*;

public class NMASCrOperator implements CrOperator {
    private static final PwmLogger LOGGER = PwmLogger.getLogger(NMASCrOperator.class);

    private int threadCounter = 0;
    private List<NMASSessionMonitor> sessionMonitorThreads = new ArrayList();
    private final PwmApplication pwmApplication;

    public NMASCrOperator(PwmApplication pwmApplication) {
        this.pwmApplication = pwmApplication;
    }

    public ResponseSet readResponseSet(
            final ChaiUser theUser,
            final String user
    )
            throws PwmUnrecoverableException
    {
        final String userDN = theUser.getEntryDN();
        pwmApplication.getIntruderManager().check(null,theUser.getEntryDN(),null);

        try {
            if (theUser == null || theUser.getChaiProvider().getDirectoryVendor() != ChaiProvider.DIRECTORY_VENDOR.NOVELL_EDIRECTORY) {
                return null;
            }

            final ResponseSet responseSet = new NMASCRResponseSet(pwmApplication, userDN);
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
    public ResponseInfoBean readResponseInfo(ChaiUser theUser, String userGUID)
            throws PwmUnrecoverableException
    {
        try {
            final ResponseSet responseSet = NmasCrFactory.readNmasResponseSet(theUser);
            if (responseSet == null) {
                return null;
            }
            final ResponseInfoBean responseInfoBean = CrOperators.convertToNoAnswerInfoBean(responseSet);
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

    public class NMASCRResponseSet implements ResponseSet, Serializable {
        private final PwmApplication pwmApplication;
        private final String userDN;

        private ChaiConfiguration chaiConfiguration;
        private ChallengeSet challengeSet;
        private transient NMASResponseSession ldapChallengeSession;
        boolean passed;

        private NMASCRResponseSet(PwmApplication pwmApplication, final String userDN)
                throws Exception
        {
            this.pwmApplication = pwmApplication;
            this.userDN = userDN;

            final Configuration config = pwmApplication.getConfig();
            final List<String> ldapURLs = config.readSettingAsStringArray(PwmSetting.LDAP_SERVER_URLS);
            final String proxyDN = config.readSettingAsString(PwmSetting.LDAP_PROXY_USER_DN);
            final String proxyPW = config.readSettingAsString(PwmSetting.LDAP_PROXY_USER_PASSWORD);
            final int idleTimeoutMS = (int)config.readSettingAsLong(PwmSetting.IDLE_TIMEOUT_SECONDS) * 1000;
            chaiConfiguration = Helper.createChaiConfiguration(config, ldapURLs, proxyDN, proxyPW, idleTimeoutMS);
            chaiConfiguration.setSetting(ChaiSetting.PROVIDER_IMPLEMENTATION, JLDAPProviderImpl.class.getName());

            cycle();
        }

        private void cycle() throws Exception {
            if (ldapChallengeSession != null) {
                ldapChallengeSession.close();
                ldapChallengeSession = null;
            }
            final LDAPConnection ldapConnection = makeLdapConnection();
            ldapChallengeSession = new NMASResponseSession(userDN,ldapConnection);
            final List<String> questions = ldapChallengeSession.getQuestions();
            challengeSet = questionsToChallengeSet(questions);
        }

        private LDAPConnection makeLdapConnection() throws Exception {
            final ChaiProvider chaiProvider = ChaiProviderFactory.createProvider(chaiConfiguration);
            final ChaiUser theUser = ChaiFactory.createChaiUser(userDN, chaiProvider);
            try {
                if (theUser.isLocked()) {
                    LOGGER.trace("user " + theUser.getEntryDN() + " appears to be intruder locked, aborting nmas ResponseSet loading" );
                    throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_INTRUDER_USER,"nmas account is intruder locked-out"));
                } else if (!theUser.isAccountEnabled()) {
                    throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_RESPONSES_NORESPONSES,"nmas account is disabled"));
                }
            } catch (ChaiException e) {
                throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_RESPONSES_NORESPONSES,"unable to evaluate nmas account status attributes"));
            }
            return (LDAPConnection)((ChaiProviderImplementor)chaiProvider).getConnectionObject();
        }

        ChallengeSet questionsToChallengeSet(final List<String> questions) throws ChaiValidationException {
            if (questions == null || questions.isEmpty()) {
                return null;
            }
            final List<Challenge> challenges = new ArrayList<Challenge>();
            for (final String question : questions) {
                challenges.add(new ChaiChallenge(true, question, 1, 256, true));
            }
            return new ChaiChallengeSet(challenges,challenges.size(), PwmConstants.DEFAULT_LOCALE,"NMAS-LDAP ChallengeResponse Set");
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
            final List<String> answers = new ArrayList<String>(challengeStringMap == null ? Collections.<String>emptyList() : challengeStringMap.values());
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
                    pwmApplication.getIntruderManager().check(null,userDN,null);
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



    private class NMASSessionMonitor extends Thread
    {
        private int state = -1;
        private boolean loginResultReady = false;
        private NMASLoginResult loginResult = null;
        private CallbackHandler callbackHandler = null;
        private LDAPConnection ldapConn = null;
        private String loginDN = null;

        private final int threadID;

        public NMASSessionMonitor()
        {
            this.threadID = threadCounter++;
            setState(1);
        }

        private synchronized void setState(int paramInt)
        {
            this.state = paramInt;
        }

        public final synchronized int getLoginState()
        {
            return this.state;
        }

        private synchronized void setLoginResult(NMASLoginResult paramNMASLoginResult)
        {
            this.loginResult = paramNMASLoginResult;
            this.loginResultReady = true;
        }

        public final synchronized NMASLoginResult getLoginResult()
        {
            while (this.loginResultReady != true)
                try
                {
                    wait();
                }
                catch (Exception localException)
                {
                }
            return this.loginResult;
        }

        public void startLogin(String userDN, LDAPConnection ldapConnection, CallbackHandler paramCallbackHandler) throws PwmUnrecoverableException {
            if (sessionMonitorThreads.size() > PwmConstants.MAX_NMAS_THREAD_COUNT) {
                throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_TOO_MANY_THREADS,"NMASSessionMonitor thread count exceeded"));
            }
            this.loginDN = userDN;
            this.ldapConn = ldapConnection;
            this.callbackHandler = paramCallbackHandler;
            this.loginResultReady = false;
            setState(1);
            setDaemon(true);
            setName(PwmConstants.PWM_APP_NAME + "-NMASSessionMonitor thread id=" + threadID);
            start();
        }

        public void run()
        {
            try {
                sessionMonitorThreads.add(this);
                LOGGER.trace("starting NMASSessionMonitor thread id=" + threadID + " activeCount=" + sessionMonitorThreads.size());
                doLoginSequence();
            } finally {
                sessionMonitorThreads.remove(this);
            }

            LOGGER.trace("exiting NMASSessionMonitor thread id=" + threadID + " activeCount=" + sessionMonitorThreads.size());
        }

        private void doLoginSequence() {
            if (this.ldapConn == null)
            {
                setState(3);
                setLoginResult(new NMASLoginResult(-1681));
                return;
            }
            final HashMap<String,Object> localHashMap = new HashMap<String,Object>();
            localHashMap.put("com.novell.security.sasl.client.pkgs", "com.novell.sasl.client");
            localHashMap.put("javax.security.sasl.client.pkgs", "com.novell.sasl.client");
            localHashMap.put("LoginSequence", "Challenge Response");
            String[] arrayOfString = { "NMAS_LOGIN" };
            try
            {
                setState(2);
                this.ldapConn.bind(this.loginDN, "dn:" + this.loginDN, arrayOfString, localHashMap, this.callbackHandler);
                setState(3);
                setLoginResult(new NMASLoginResult(((NMASCallbackHandler)this.callbackHandler).getNmasRetCode(), this.ldapConn));
            }
            catch (LDAPException localLDAPException)
            {
                NMASTrace.out("NMASLoginMonitor: LDAPException " + localLDAPException.toString());
                String str;
                if ((str = localLDAPException.getLDAPErrorMessage()) != null)
                    NMASTrace.out("NMASLoginMonitor: LDAP error (" + str + ")");
                setState(3);
                NMASLoginResult localNMASLoginResult = new NMASLoginResult(((NMASCallbackHandler)this.callbackHandler).getNmasRetCode(), localLDAPException);
                setLoginResult(localNMASLoginResult);
            }
        }
    }

    private class NMASResponseSession implements SessionManager.CloseConnectionListener {

        private LDAPConnection   ldapConnection;

        private GenLcmUI lcmEnv;
        private NMASSessionMonitor nlm;

        public NMASResponseSession(String userDN, LDAPConnection ldapConnection) throws LCMRegistryException, PwmUnrecoverableException {
            this.ldapConnection = ldapConnection;

            Security.addProvider(new com.novell.sasl.client.NovellSaslProvider());

            lcmEnv = new GenLcmUI();
            final GenLCMRegistry lcmRegistry = new GenLCMRegistry();
            lcmRegistry.registerLcm("com.novell.security.nmas.lcm.chalresp.XmlChalRespLCM");

            // You *must* use this object else the login method and callback handler will lock the
            // current thread preventing anything from working.
            nlm = new NMASSessionMonitor();
            int loginState = nlm.getLoginState();
            if (loginState == 1) {
                final ChalRespCallbackHandler cbh = new ChalRespCallbackHandler(lcmEnv, lcmRegistry);
                nlm.startLogin(userDN, ldapConnection, cbh);
            }
        }

        public List<String> getQuestions() throws XPathExpressionException, LCMRegistryException {

            final LCMUserPrompt prompt = lcmEnv.getNextUserPrompt();
            if (prompt == null) {
                return null;
            }
            final Document doc = prompt.getLcmXmlDataDoc();
            return documentToQuestions(doc);
        }

        public boolean testAnswers(List<String> answers)
                throws SAXException, IOException, ParserConfigurationException
        {
            final Document doc = answersToDocument(answers);
            lcmEnv.setUserResponse(new LCMUserResponse(doc));
            final NMASLoginResult loginResult = nlm.getLoginResult();
            final boolean result = loginResult.getNmasRetCode() == 0;
            if (result) {
                ldapConnection = loginResult.getLdapConnection();
            }
            return result;
        }

        public LDAPConnection getLDAPConnection() {
            return ldapConnection;
        }

        private List<String> documentToQuestions(Document doc) throws XPathExpressionException {
            XPath xpath = XPathFactory.newInstance().newXPath();
            XPathExpression challengesExpr = xpath.compile("/Challenges/Challenge/text()");
            NodeList challenges = (NodeList)challengesExpr.evaluate(doc, XPathConstants.NODESET);
            final List<String> res = new ArrayList<String>();
            for (int i = 0; i < challenges.getLength(); ++i) {
                String question = challenges.item(i).getTextContent();
                res.add(question);
            }
            return Collections.unmodifiableList(res);
        }

        private Document answersToDocument(final List<String> answers)
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

        public void close() {
            if (this.ldapConnection != null) {
                try {
                    this.ldapConnection.disconnect();
                } catch (LDAPException e) {
                    e.printStackTrace();
                }
                this.ldapConnection = null;
            }
        }

        @Override
        public void connectionsClosed() {
            close();
        }

        private class ChalRespCallbackHandler extends NMASCallbackHandler
        {
            public ChalRespCallbackHandler(LCMEnvironment lcmenvironment, LCMRegistry lcmregistry)
            {
                super(lcmenvironment, lcmregistry);
            }

            public void handle(Callback acallback[]) throws UnsupportedCallbackException
            {
                for (Callback anAcallback : acallback) {
                    if (anAcallback instanceof NMASCompletionCallback) {
                    /*noop*/
                    } else if (anAcallback instanceof NMASCallback) {
                        try {
                            handleNMASCallback((NMASCallback) anAcallback);
                        } catch (InvalidNMASCallbackException invalidnmascallbackexception) {/*noop*/}
                    } else if (anAcallback instanceof LCMUserPromptCallback) {
                        try {
                            handleLCMUserPromptCallback((LCMUserPromptCallback) anAcallback);
                        } catch (LCMUserPromptException lcmuserpromptexception) {/*noop*/}
                    } else {
                        throw new UnsupportedCallbackException(anAcallback);
                    }
                }
            }
        }
    }

}
