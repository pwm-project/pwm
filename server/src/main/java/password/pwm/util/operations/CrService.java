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

package password.pwm.util.operations;

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.cr.ChaiChallenge;
import com.novell.ldapchai.cr.ChaiChallengeSet;
import com.novell.ldapchai.cr.Challenge;
import com.novell.ldapchai.cr.ChallengeSet;
import com.novell.ldapchai.cr.ResponseSet;
import com.novell.ldapchai.exception.ChaiException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.exception.ChaiValidationException;
import com.novell.ldapchai.impl.edir.NmasCrFactory;
import com.novell.ldapchai.provider.DirectoryVendor;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.bean.ResponseInfoBean;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.config.profile.ChallengeProfile;
import password.pwm.config.profile.PwmPasswordPolicy;
import password.pwm.config.value.data.UserPermission;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmDataValidationException;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthRecord;
import password.pwm.ldap.LdapOperationsHelper;
import password.pwm.ldap.LdapPermissionTester;
import password.pwm.svc.PwmService;
import password.pwm.svc.wordlist.WordlistService;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.operations.cr.CrOperator;
import password.pwm.util.operations.cr.DbCrOperator;
import password.pwm.util.operations.cr.LdapCrOperator;
import password.pwm.util.operations.cr.LocalDbCrOperator;
import password.pwm.util.operations.cr.NMASCrOperator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * @author Jason D. Rivard
 */
public class CrService implements PwmService
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( CrService.class );

    private final Map<DataStorageMethod, CrOperator> operatorMap = new HashMap<>();
    private PwmApplication pwmApplication;

    public CrService( )
    {
    }

    @Override
    public STATUS status( )
    {
        return STATUS.OPEN;
    }

    @Override
    public void init( final PwmApplication pwmApplication ) throws PwmException
    {
        this.pwmApplication = pwmApplication;
        operatorMap.put( DataStorageMethod.DB, new DbCrOperator( pwmApplication ) );
        operatorMap.put( DataStorageMethod.LDAP, new LdapCrOperator( pwmApplication.getConfig() ) );
        operatorMap.put( DataStorageMethod.LOCALDB, new LocalDbCrOperator( pwmApplication.getLocalDB() ) );
        operatorMap.put( DataStorageMethod.NMAS, new NMASCrOperator( pwmApplication ) );
    }

    @Override
    public void close( )
    {
        for ( final CrOperator operator : operatorMap.values() )
        {
            operator.close();
        }
        operatorMap.clear();
    }

    @Override
    public List<HealthRecord> healthCheck( )
    {
        return Collections.emptyList();
    }

    public ChallengeProfile readUserChallengeProfile(
            final SessionLabel sessionLabel,
            final UserIdentity userIdentity,
            final ChaiUser theUser,
            final PwmPasswordPolicy policy,
            final Locale locale
    )
            throws PwmUnrecoverableException
    {
        final Configuration config = pwmApplication.getConfig();
        final long methodStartTime = System.currentTimeMillis();

        ChallengeSet returnSet = null;

        if ( config.readSettingAsBoolean( PwmSetting.EDIRECTORY_READ_CHALLENGE_SET ) )
        {
            try
            {
                if ( theUser.getChaiProvider().getDirectoryVendor() == DirectoryVendor.EDIRECTORY )
                {
                    if ( policy != null && policy.getChaiPasswordPolicy() != null )
                    {
                        returnSet = NmasCrFactory.readAssignedChallengeSet( theUser.getChaiProvider(), policy.getChaiPasswordPolicy(), locale );
                    }

                    if ( returnSet == null )
                    {
                        returnSet = NmasCrFactory.readAssignedChallengeSet( theUser, locale );
                    }

                    if ( returnSet == null )
                    {
                        LOGGER.debug( sessionLabel, () -> "no nmas c/r policy found for user " + theUser.getEntryDN() );
                    }
                    else
                    {
                        final ChallengeSet finalReturnSet = returnSet;
                        LOGGER.debug( sessionLabel, () -> "using nmas c/r policy for user " + theUser.getEntryDN() + ": " + finalReturnSet.toString() );

                        final String challengeID = "nmasPolicy-" + userIdentity.toDelimitedKey();

                        final ChallengeProfile challengeProfile = ChallengeProfile.createChallengeProfile(
                                challengeID,
                                locale,
                                applyPwmPolicyToNmasChallenges( returnSet, config ),
                                null,
                                ( int ) config.readSettingAsLong( PwmSetting.EDIRECTORY_CR_MIN_RANDOM_DURING_SETUP ),
                                0
                        );

                        LOGGER.debug( sessionLabel, () -> "using ldap c/r policy for user " + theUser.getEntryDN() + ": "
                                + finalReturnSet.toString() );
                        LOGGER.trace( sessionLabel, () -> "readUserChallengeProfile completed in "
                                + TimeDuration.fromCurrent( methodStartTime ).asCompactString() + ", result="
                                + JsonUtil.serialize( challengeProfile ) );

                        return challengeProfile;
                    }
                }
            }
            catch ( final ChaiException e )
            {
                LOGGER.error( sessionLabel, () -> "error reading nmas c/r policy for user " + theUser.getEntryDN() + ": " + e.getMessage() );
            }
            LOGGER.debug( sessionLabel, () -> "no detected c/r policy for user " + theUser.getEntryDN() + " in nmas" );
        }

        // use PWM policies if PWM is configured and either its all that is configured OR the NMAS policy read was not successful
        final String challengeProfileID = determineChallengeProfileForUser( pwmApplication, sessionLabel, userIdentity, locale );
        final ChallengeProfile challengeProfile = config.getChallengeProfile( challengeProfileID, locale );

        LOGGER.trace( sessionLabel, () -> "readUserChallengeProfile completed in " + TimeDuration.fromCurrent( methodStartTime ).asCompactString() + " returned profile: "
                + ( challengeProfile == null ? "null" : challengeProfile.getIdentifier() ) );
        return challengeProfile;
    }

    private static ChallengeSet applyPwmPolicyToNmasChallenges( final ChallengeSet challengeSet, final Configuration configuration ) throws PwmUnrecoverableException
    {
        final List<Challenge> newChallenges = new ArrayList<>();
        final boolean applyWordlist = configuration.readSettingAsBoolean( PwmSetting.EDIRECTORY_CR_APPLY_WORDLIST );
        final int questionsInAnswer = ( int ) configuration.readSettingAsLong( PwmSetting.EDIRECTORY_CR_MAX_QUESTION_CHARS_IN__ANSWER );
        for ( final Challenge challenge : challengeSet.getChallenges() )
        {
            newChallenges.add( new ChaiChallenge(
                    challenge.isRequired(),
                    challenge.getChallengeText(),
                    challenge.getMinLength(),
                    challenge.getMaxLength(),
                    challenge.isAdminDefined(),
                    questionsInAnswer,
                    applyWordlist
            ) );
        }

        try
        {
            return new ChaiChallengeSet(
                    newChallenges,
                    challengeSet.getMinRandomRequired(),
                    challengeSet.getLocale(),
                    challengeSet.getIdentifier()
            );
        }
        catch ( final ChaiValidationException e )
        {
            final String errorMsg = "unexpected error applying policies to nmas challengeset: " + e.getMessage();
            LOGGER.error( () -> errorMsg, e );
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_INTERNAL, errorMsg ) );
        }
    }


    protected static String determineChallengeProfileForUser(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final UserIdentity userIdentity,
            final Locale locale
    )
            throws PwmUnrecoverableException
    {
        final List<String> profiles = pwmApplication.getConfig().getChallengeProfileIDs();
        if ( profiles.isEmpty() )
        {
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_NO_PROFILE_ASSIGNED, "no challenge profile is configured" ) );
        }

        for ( final String profile : profiles )
        {
            final ChallengeProfile loopPolicy = pwmApplication.getConfig().getChallengeProfile( profile, locale );
            final List<UserPermission> queryMatch = loopPolicy.getUserPermissions();
            if ( queryMatch != null && !queryMatch.isEmpty() )
            {
                LOGGER.debug( sessionLabel, () -> "testing challenge profiles '" + profile + "'" );
                try
                {
                    final boolean match = LdapPermissionTester.testUserPermissions( pwmApplication, sessionLabel, userIdentity, queryMatch );
                    if ( match )
                    {
                        return profile;
                    }
                }
                catch ( final PwmUnrecoverableException e )
                {
                    LOGGER.error( sessionLabel, () -> "unexpected error while testing password policy profile '" + profile + "', error: " + e.getMessage() );
                }
            }
        }

        throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_NO_PROFILE_ASSIGNED, "no challenge profile is assigned" ) );
    }

    public void validateResponses(
            final ChallengeSet challengeSet,
            final Map<Challenge, String> responseMap,
            final int minRandomRequiredSetup

    )
            throws PwmDataValidationException, PwmUnrecoverableException
    {
        //strip null keys from responseMap;
        responseMap.keySet().removeIf( Objects::isNull );

        {
            // check for missing question texts
            for ( final Challenge challenge : responseMap.keySet() )
            {
                if ( !challenge.isAdminDefined() )
                {
                    final String text = challenge.getChallengeText();
                    if ( text == null || text.length() < 1 )
                    {
                        final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_MISSING_CHALLENGE_TEXT );
                        throw new PwmDataValidationException( errorInformation );
                    }
                }
            }
        }

        {
            // check responses against wordlist
            final WordlistService wordlistManager = pwmApplication.getWordlistService();
            if ( wordlistManager.status() == PwmService.STATUS.OPEN )
            {
                for ( final Map.Entry<Challenge, String> entry : responseMap.entrySet() )
                {
                    final Challenge loopChallenge = entry.getKey();
                    if ( loopChallenge.isEnforceWordlist() )
                    {
                        final String answer = entry.getValue();
                        if ( wordlistManager.containsWord( answer ) )
                        {
                            final ErrorInformation errorInfo = new ErrorInformation(
                                    PwmError.ERROR_RESPONSE_WORDLIST,
                                    null,
                                    new String[]
                                            {
                                                    loopChallenge.getChallengeText(),
                                            }
                            );
                            throw new PwmDataValidationException( errorInfo );
                        }
                    }
                }
            }
        }

        {
            // check for duplicate questions.  need to check the actual req params because the following dupes wont populate duplicates
            final Set<String> userQuestionTexts = new HashSet<>();
            for ( final Challenge challenge : responseMap.keySet() )
            {
                final String text = challenge.getChallengeText();
                if ( text != null )
                {
                    if ( userQuestionTexts.contains( text.toLowerCase() ) )
                    {
                        final String errorMsg = "duplicate challenge text: " + text;
                        final ErrorInformation errorInformation = new ErrorInformation(
                                PwmError.ERROR_CHALLENGE_DUPLICATE,
                                errorMsg,
                                new String[]
                                        {
                                                text,
                                        }
                        );
                        throw new PwmDataValidationException( errorInformation );
                    }
                    else
                    {
                        userQuestionTexts.add( text.toLowerCase() );
                    }
                }
            }
        }

        int randomCount = 0;
        for ( final Challenge loopChallenge : responseMap.keySet() )
        {
            if ( !loopChallenge.isRequired() )
            {
                randomCount++;
            }
        }

        if ( minRandomRequiredSetup == 0 )
        {
            // if using recover style, then all readResponseSet must be supplied at this point.
            if ( randomCount < challengeSet.getRandomChallenges().size() )
            {
                final String errorMsg = "all randoms required, but not all randoms are completed";
                final ErrorInformation errorInfo = new ErrorInformation( PwmError.ERROR_MISSING_RANDOM_RESPONSE, errorMsg );
                throw new PwmDataValidationException( errorInfo );
            }
        }

        if ( randomCount < minRandomRequiredSetup )
        {
            final String errorMsg = minRandomRequiredSetup + " randoms required, but not only " + randomCount + " randoms are completed";
            final ErrorInformation errorInfo = new ErrorInformation( PwmError.ERROR_MISSING_RANDOM_RESPONSE, errorMsg );
            throw new PwmDataValidationException( errorInfo );
        }

        if ( JavaHelper.isEmpty( responseMap ) )
        {
            final String errorMsg = "empty response set";
            final ErrorInformation errorInfo = new ErrorInformation( PwmError.ERROR_MISSING_PARAMETER, errorMsg );
            throw new PwmDataValidationException( errorInfo );
        }
    }

    public ResponseInfoBean readUserResponseInfo(
            final SessionLabel sessionLabel,
            final UserIdentity userIdentity,
            final ChaiUser theUser
    )
            throws ChaiUnavailableException, PwmUnrecoverableException
    {
        final Configuration config = pwmApplication.getConfig();

        LOGGER.trace( sessionLabel, () -> "beginning read of user response sequence" );

        final List<DataStorageMethod> readPreferences = config.helper().getCrReadPreference();

        final String debugMsg = "will attempt to read the following storage methods: "
                + JsonUtil.serializeCollection( readPreferences ) + " for response info for user " + theUser.getEntryDN();
        LOGGER.debug( sessionLabel, () -> debugMsg );

        final String userGUID;
        if ( readPreferences.contains( DataStorageMethod.DB ) || readPreferences.contains( DataStorageMethod.LOCALDB ) )
        {
            userGUID = LdapOperationsHelper.readLdapGuidValue( pwmApplication, sessionLabel, userIdentity, false );
        }
        else
        {
            userGUID = null;
        }

        for ( final DataStorageMethod storageMethod : readPreferences )
        {
            final ResponseInfoBean readResponses;

            LOGGER.trace( sessionLabel, () -> "attempting read of response info via storage method: " + storageMethod );
            readResponses = operatorMap.get( storageMethod ).readResponseInfo( theUser, userIdentity, userGUID );

            if ( readResponses != null )
            {
                LOGGER.debug( sessionLabel, () -> "returning response info read via method " + storageMethod + " for user " + theUser.getEntryDN() );
                return readResponses;
            }
            else
            {
                LOGGER.trace( sessionLabel, () -> "no responses info read using method " + storageMethod );
            }
        }
        LOGGER.debug( sessionLabel, () -> "no response info found for user " + theUser.getEntryDN() );
        return null;
    }


    public ResponseSet readUserResponseSet(
            final SessionLabel sessionLabel,
            final UserIdentity userIdentity,
            final ChaiUser theUser
    )
            throws ChaiUnavailableException, PwmUnrecoverableException
    {
        final Configuration config = pwmApplication.getConfig();

        LOGGER.trace( sessionLabel, () -> "beginning read of user response sequence" );

        final List<DataStorageMethod> readPreferences = config.helper().getCrReadPreference();

        LOGGER.debug( sessionLabel, () -> "will attempt to read the following storage methods: "
                + JsonUtil.serializeCollection( readPreferences ) + " for user " + theUser.getEntryDN() );

        final String userGUID;
        if ( readPreferences.contains( DataStorageMethod.DB ) || readPreferences.contains( DataStorageMethod.LOCALDB ) )
        {
            userGUID = LdapOperationsHelper.readLdapGuidValue( pwmApplication, sessionLabel, userIdentity, false );
        }
        else
        {
            userGUID = null;
        }

        for ( final DataStorageMethod storageMethod : readPreferences )
        {
            final ResponseSet readResponses;

            LOGGER.trace( sessionLabel, () -> "attempting read of responses via storage method: " + storageMethod );
            readResponses = operatorMap.get( storageMethod ).readResponseSet( theUser, userIdentity, userGUID );

            if ( readResponses != null )
            {
                LOGGER.debug( sessionLabel, () -> "returning responses read via method " + storageMethod + " for user " + theUser.getEntryDN() );
                return readResponses;
            }
            else
            {
                LOGGER.trace( sessionLabel, () -> "no responses read using method " + storageMethod );
            }
        }
        LOGGER.debug( sessionLabel, () -> "no responses found for user " + theUser.getEntryDN() );
        return null;
    }


    public void writeResponses(
            final UserIdentity userIdentity,
            final ChaiUser theUser,
            final String userGUID,
            final ResponseInfoBean responseInfoBean
    )
            throws PwmOperationalException, ChaiUnavailableException, ChaiValidationException
    {

        int attempts = 0;
        int successes = 0;
        final Map<DataStorageMethod, String> errorMessages = new LinkedHashMap<>();
        final Configuration config = pwmApplication.getConfig();

        final List<DataStorageMethod> writeMethods = config.helper().getCrWritePreference();

        for ( final DataStorageMethod loopWriteMethod : writeMethods )
        {
            try
            {
                attempts++;
                operatorMap.get( loopWriteMethod ).writeResponses( userIdentity, theUser, userGUID, responseInfoBean );
                LOGGER.debug( () -> "saved responses using storage method " + loopWriteMethod + " for user " + theUser.getEntryDN() );
                errorMessages.put( loopWriteMethod, "Success" );
                successes++;
            }
            catch ( final PwmUnrecoverableException e )
            {
                final String errorMsg = "error saving responses via " + loopWriteMethod + ", error: " + e.getMessage();
                errorMessages.put( loopWriteMethod, errorMsg );
                LOGGER.error( () -> errorMsg );
            }
        }

        if ( attempts == 0 )
        {
            final String errorMsg = "no response save methods are available or configured";
            final ErrorInformation errorInfo = new ErrorInformation( PwmError.ERROR_WRITING_RESPONSES, errorMsg );
            throw new PwmOperationalException( errorInfo );
        }

        if ( attempts != successes )
        {
            final String errorMsg = "response storage only partially successful; attempts=" + attempts + ", successes=" + successes
                    + ", detail=" + JsonUtil.serializeMap( errorMessages );
            final ErrorInformation errorInfo = new ErrorInformation( PwmError.ERROR_WRITING_RESPONSES, errorMsg );
            throw new PwmOperationalException( errorInfo );
        }
    }


    public void clearResponses(
            final SessionLabel sessionLabel,
            final UserIdentity userIdentity,
            final ChaiUser theUser,
            final String userGUID

    )
            throws PwmOperationalException, ChaiUnavailableException
    {
        final Configuration config = pwmApplication.getConfig();
        int attempts = 0;
        int successes = 0;

        LOGGER.trace( sessionLabel, () -> "beginning clear response operation for user " + theUser.getEntryDN() + " guid=" + userGUID );

        final List<DataStorageMethod> writeMethods = config.helper().getCrWritePreference();

        for ( final DataStorageMethod loopWriteMethod : writeMethods )
        {
            try
            {
                attempts++;
                operatorMap.get( loopWriteMethod ).clearResponses( userIdentity, theUser, userGUID );
                successes++;
            }
            catch ( final PwmUnrecoverableException e )
            {
                LOGGER.error( sessionLabel, () -> "error clearing responses via " + loopWriteMethod + ", error: " + e.getMessage() );
            }
        }

        if ( attempts == 0 )
        {
            final String errorMsg = "no response save methods are available or configured";
            final ErrorInformation errorInfo = new ErrorInformation( PwmError.ERROR_CLEARING_RESPONSES, errorMsg );
            throw new PwmOperationalException( errorInfo );
        }

        if ( attempts != successes )
        {
            // should be impossible to read here, but just in case.
            final String errorMsg = "response clear partially successful; attempts=" + attempts + ", successes=" + successes;
            final ErrorInformation errorInfo = new ErrorInformation( PwmError.ERROR_CLEARING_RESPONSES, errorMsg );
            throw new PwmOperationalException( errorInfo );
        }
    }

    public boolean checkIfResponseConfigNeeded(
            final PwmApplication pwmApplication,
            final SessionLabel pwmSession,
            final UserIdentity userIdentity,
            final ChallengeSet challengeSet,
            final ResponseInfoBean responseInfoBean
    )
            throws ChaiUnavailableException, PwmUnrecoverableException
    {
        LOGGER.trace( pwmSession, () -> "beginning check to determine if responses need to be configured for user" );

        final Configuration config = pwmApplication.getConfig();

        if ( !config.readSettingAsBoolean( PwmSetting.CHALLENGE_ENABLE ) )
        {
            LOGGER.debug( pwmSession, () -> "checkIfResponseConfigNeeded: response setup is disabled, so user is not required to setup responses" );
            return false;
        }

        if ( !config.readSettingAsBoolean( PwmSetting.CHALLENGE_FORCE_SETUP ) )
        {
            LOGGER.debug( pwmSession, () -> "checkIfResponseConfigNeeded: force response setup is disabled, so user is not required to setup responses" );
            return false;
        }

        if ( !LdapPermissionTester.testUserPermissions( pwmApplication, pwmSession, userIdentity, config.readSettingAsUserPermission( PwmSetting.QUERY_MATCH_SETUP_RESPONSE ) ) )
        {
            LOGGER.debug( pwmSession, () -> "checkIfResponseConfigNeeded: " + userIdentity + " does not have permission to setup responses" );
            return false;
        }

        if ( !LdapPermissionTester.testUserPermissions( pwmApplication, pwmSession, userIdentity, config.readSettingAsUserPermission( PwmSetting.QUERY_MATCH_CHECK_RESPONSES ) ) )
        {
            LOGGER.debug( pwmSession, () -> "checkIfResponseConfigNeeded: " + userIdentity + " is not eligible for checkIfResponseConfigNeeded due to query match" );
            return false;
        }

        // check to be sure there are actually challenges in the challenge set
        if ( challengeSet == null || challengeSet.getChallenges().isEmpty() )
        {
            LOGGER.debug( pwmSession, () -> "checkIfResponseConfigNeeded: no challenge sets configured for user " + userIdentity );
            return false;
        }

        // ignore NMAS based CR set if so configured
        if ( responseInfoBean != null && ( responseInfoBean.getDataStorageMethod() == DataStorageMethod.NMAS ) )
        {
            final boolean ignoreNmasCr = Boolean.parseBoolean( pwmApplication.getConfig().readAppProperty( AppProperty.NMAS_IGNORE_NMASCR_DURING_FORCECHECK ) );
            if ( ignoreNmasCr )
            {
                LOGGER.debug( pwmSession, () -> "checkIfResponseConfigNeeded: app property " + AppProperty.NMAS_IGNORE_NMASCR_DURING_FORCECHECK.getKey()
                        + "=true and user's responses are in " + responseInfoBean.getDataStorageMethod() + " format, so forcing setup of new responses." );
                return true;
            }
        }

        try
        {
            // check if responses exist
            if ( responseInfoBean == null )
            {
                throw new Exception( "no responses configured" );
            }

            // check if responses meet the challenge set policy for the user
            //usersResponses.meetsChallengeSetRequirements(challengeSet);

            LOGGER.debug( pwmSession, () -> "checkIfResponseConfigNeeded: " + userIdentity + " has good responses" );
            return false;
        }
        catch ( final Exception e )
        {
            LOGGER.debug( pwmSession, () -> "checkIfResponseConfigNeeded: " + userIdentity + " does not have good responses: " + e.getMessage() );
            return true;
        }
    }

    @Override
    public ServiceInfoBean serviceInfo( )
    {
        final LinkedHashSet<DataStorageMethod> usedStorageMethods = new LinkedHashSet<>();
        usedStorageMethods.addAll( pwmApplication.getConfig().helper().getCrReadPreference() );
        usedStorageMethods.addAll( pwmApplication.getConfig().helper().getCrWritePreference() );
        return new ServiceInfoBean( Collections.unmodifiableList( new ArrayList( usedStorageMethods ) ) );
    }
}
