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

package password.pwm.svc.cr;

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
import password.pwm.PwmDomain;
import password.pwm.bean.DomainID;
import password.pwm.bean.ResponseInfoBean;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.DomainConfig;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.config.profile.ChallengeProfile;
import password.pwm.config.profile.ProfileDefinition;
import password.pwm.config.profile.ProfileUtility;
import password.pwm.config.profile.PwmPasswordPolicy;
import password.pwm.config.profile.SetupResponsesProfile;
import password.pwm.config.value.data.UserPermission;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmDataValidationException;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthRecord;
import password.pwm.ldap.LdapOperationsHelper;
import password.pwm.ldap.permission.UserPermissionUtility;
import password.pwm.svc.AbstractPwmService;
import password.pwm.svc.PwmService;
import password.pwm.svc.wordlist.WordlistService;
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Jason D. Rivard
 */
public class CrService extends AbstractPwmService implements PwmService
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( CrService.class );

    private final Map<DataStorageMethod, CrOperator> operatorMap = new HashMap<>();
    private PwmDomain pwmDomain;

    public CrService( )
    {
    }

    @Override
    public STATUS postAbstractInit( final PwmApplication pwmApplication, final DomainID domainID ) throws PwmException
    {
        this.pwmDomain = pwmApplication.domains().get( domainID );
        operatorMap.put( DataStorageMethod.DB, new DbCrOperator( pwmDomain ) );
        operatorMap.put( DataStorageMethod.LDAP, new LdapCrOperator( pwmDomain.getConfig() ) );
        operatorMap.put( DataStorageMethod.LOCALDB, new LocalDbCrOperator( pwmDomain.getPwmApplication().getLocalDB() ) );
        operatorMap.put( DataStorageMethod.NMAS, new NMASCrOperator( pwmDomain ) );
        return STATUS.OPEN;
    }

    @Override
    public void shutdownImpl( )
    {
        for ( final CrOperator operator : operatorMap.values() )
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

    public ChallengeProfile readUserChallengeProfile(
            final SessionLabel sessionLabel,
            final UserIdentity userIdentity,
            final ChaiUser theUser,
            final PwmPasswordPolicy policy,
            final Locale locale
    )
            throws PwmUnrecoverableException
    {
        final DomainConfig config = pwmDomain.getConfig();
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
                        final String challengeID = "nmasPolicy-" + userIdentity.toDelimitedKey();

                        final ChallengeProfile challengeProfile = ChallengeProfile.createChallengeProfile(
                                challengeID,
                                locale,
                                applyPwmPolicyToNmasChallenges( returnSet, config ),
                                null,
                                ( int ) config.readSettingAsLong( PwmSetting.EDIRECTORY_CR_MIN_RANDOM_DURING_SETUP ),
                                0
                        );

                        {
                            final Optional<ChallengeSet> challengeSet = challengeProfile.getChallengeSet();
                            if ( challengeSet.isPresent() )
                            {
                                LOGGER.debug( sessionLabel, () -> "using nmas ldap c/r policy for user " + theUser.getEntryDN() + ": "
                                        + JsonFactory.get().serialize( challengeSet.get().asChallengeSetBean() ) );
                            }
                            else
                            {
                                LOGGER.debug( sessionLabel, () -> "nmas ldap c/r policy for user is empty" );
                            }
                        }
                        {
                            final Optional<ChallengeSet> challengeSet = challengeProfile.getHelpdeskChallengeSet();
                            if ( challengeSet.isPresent() )
                            {
                                LOGGER.debug( sessionLabel, () -> "using nmas ldap c/r helpdesk policy for user " + theUser.getEntryDN() + ": "
                                        + JsonFactory.get().serialize( challengeSet.get().asChallengeSetBean() ) );
                            }
                            else
                            {
                                LOGGER.debug( sessionLabel, () -> "nmas ldap c/r helpdesk policy for user is empty" );
                            }
                        }

                        LOGGER.trace( sessionLabel, () -> "readUserChallengeProfile completed",
                                TimeDuration.fromCurrent( methodStartTime ) );

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
        final String challengeProfileID = determineChallengeProfileForUser( pwmDomain, sessionLabel, userIdentity, locale );
        final ChallengeProfile challengeProfile = config.getChallengeProfile( challengeProfileID, locale );

        LOGGER.trace( sessionLabel, () -> "readUserChallengeProfile completed in " + TimeDuration.fromCurrent( methodStartTime ).asCompactString() + " returned profile: "
                + ( challengeProfile == null ? "null" : challengeProfile.getIdentifier() ) );
        return challengeProfile;
    }

    private static ChallengeSet applyPwmPolicyToNmasChallenges( final ChallengeSet challengeSet, final DomainConfig domainConfig ) throws PwmUnrecoverableException
    {
        final boolean applyWordlist = domainConfig.readSettingAsBoolean( PwmSetting.EDIRECTORY_CR_APPLY_WORDLIST );
        final int questionsInAnswer = ( int ) domainConfig.readSettingAsLong( PwmSetting.EDIRECTORY_CR_MAX_QUESTION_CHARS_IN__ANSWER );

        final List<Challenge> newChallenges = new ArrayList<>( challengeSet.getChallenges().size() );
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
            final PwmDomain pwmDomain,
            final SessionLabel sessionLabel,
            final UserIdentity userIdentity,
            final Locale locale
    )
            throws PwmUnrecoverableException
    {
        final List<String> profiles = pwmDomain.getConfig().getChallengeProfileIDs();
        if ( profiles.isEmpty() )
        {
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_NO_PROFILE_ASSIGNED, "no challenge profile is configured" ) );
        }

        for ( final String profile : profiles )
        {
            final ChallengeProfile loopPolicy = pwmDomain.getConfig().getChallengeProfile( profile, locale );
            final List<UserPermission> queryMatch = loopPolicy.getUserPermissions();
            if ( queryMatch != null && !queryMatch.isEmpty() )
            {
                LOGGER.debug( sessionLabel, () -> "testing challenge profiles '" + profile + "'" );
                try
                {
                    final boolean match = UserPermissionUtility.testUserPermission( pwmDomain, sessionLabel, userIdentity, queryMatch );
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

        checkResponsesForMissingQuestionsText( responseMap );

        checkResponsesAgainstWordlist( responseMap );

        checkForDuplicateQuestions( responseMap );

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

        if ( CollectionUtil.isEmpty( responseMap ) )
        {
            final String errorMsg = "empty response set";
            final ErrorInformation errorInfo = new ErrorInformation( PwmError.ERROR_MISSING_PARAMETER, errorMsg );
            throw new PwmDataValidationException( errorInfo );
        }
    }

    private void checkResponsesForMissingQuestionsText( final Map<Challenge, String> responseMap )
            throws PwmDataValidationException
    {
        for ( final Challenge challenge : responseMap.keySet() )
        {
            if ( !challenge.isAdminDefined() )
            {
                if ( StringUtil.isEmpty( challenge.getChallengeText() ) )
                {
                    final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_MISSING_CHALLENGE_TEXT );
                    throw new PwmDataValidationException( errorInformation );
                }
            }
        }
    }

    private void checkResponsesAgainstWordlist( final Map<Challenge, String> responseMap )
            throws PwmDataValidationException, PwmUnrecoverableException
    {
        final WordlistService wordlistManager = pwmDomain.getPwmApplication().getWordlistService();
        if ( wordlistManager == null || wordlistManager.status() != PwmService.STATUS.OPEN )
        {
            return;
        }

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

    private void checkForDuplicateQuestions( final Map<Challenge, String> responseMap )
            throws PwmDataValidationException
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

    public Optional<ResponseInfoBean> readUserResponseInfo(
            final SessionLabel sessionLabel,
            final UserIdentity userIdentity,
            final ChaiUser theUser
    )
            throws ChaiUnavailableException, PwmUnrecoverableException
    {
        final DomainConfig config = pwmDomain.getConfig();

        LOGGER.trace( sessionLabel, () -> "beginning read of user response sequence" );

        final List<DataStorageMethod> readPreferences = config.getCrReadPreference();

        final String debugMsg = "will attempt to read the following storage methods: "
                + JsonFactory.get().serializeCollection( readPreferences ) + " for response info for user " + theUser.getEntryDN();
        LOGGER.debug( sessionLabel, () -> debugMsg );

        final String userGUID;
        if ( readPreferences.contains( DataStorageMethod.DB ) || readPreferences.contains( DataStorageMethod.LOCALDB ) )
        {
            userGUID = LdapOperationsHelper.readLdapGuidValue( pwmDomain, sessionLabel, userIdentity, false );
        }
        else
        {
            userGUID = null;
        }

        for ( final DataStorageMethod storageMethod : readPreferences )
        {
            final Optional<ResponseInfoBean> readResponses;

            LOGGER.trace( sessionLabel, () -> "attempting read of response info via storage method: " + storageMethod );
            readResponses = operatorMap.get( storageMethod ).readResponseInfo( sessionLabel, theUser, userIdentity, userGUID );

            if ( readResponses.isPresent() )
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
        return Optional.empty();
    }


    public Optional<ResponseSet> readUserResponseSet(
            final SessionLabel sessionLabel,
            final UserIdentity userIdentity,
            final ChaiUser theUser
    )
            throws PwmUnrecoverableException
    {
        final DomainConfig config = pwmDomain.getConfig();

        LOGGER.trace( sessionLabel, () -> "beginning read of user response sequence" );

        final List<DataStorageMethod> readPreferences = config.getCrReadPreference();

        LOGGER.debug( sessionLabel, () -> "will attempt to read the following storage methods: "
                + JsonFactory.get().serializeCollection( readPreferences ) + " for user " + theUser.getEntryDN() );

        final String userGUID;
        if ( readPreferences.contains( DataStorageMethod.DB ) || readPreferences.contains( DataStorageMethod.LOCALDB ) )
        {
            userGUID = LdapOperationsHelper.readLdapGuidValue( pwmDomain, sessionLabel, userIdentity, false );
        }
        else
        {
            userGUID = null;
        }

        for ( final DataStorageMethod storageMethod : readPreferences )
        {
            final Optional<ResponseSet> readResponses;

            LOGGER.trace( sessionLabel, () -> "attempting read of responses via storage method: " + storageMethod );
            readResponses = operatorMap.get( storageMethod ).readResponseSet( sessionLabel, theUser, userIdentity, userGUID );

            if ( readResponses.isPresent() )
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
        return Optional.empty();
    }


    public void writeResponses(
            final SessionLabel sessionLabel,
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

        final List<DataStorageMethod> writeMethods = pwmDomain.getConfig().getCrWritePreference( );

        LOGGER.debug( sessionLabel, () -> "will attempt to write the following storage methods: "
                + JsonFactory.get().serializeCollection( writeMethods ) + " for user " + theUser.getEntryDN() );


        for ( final DataStorageMethod loopWriteMethod : writeMethods )
        {
            try
            {
                attempts++;
                operatorMap.get( loopWriteMethod ).writeResponses( sessionLabel, userIdentity, theUser, userGUID, responseInfoBean );
                LOGGER.debug( sessionLabel, () -> "saved responses using storage method " + loopWriteMethod + " for user " + theUser.getEntryDN() );
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

        if ( successes == 0 )
        {
            final String errorMsg = "response storage unsuccessful; attempts=" + attempts + ", successes=" + successes
                    + ", detail=" + JsonFactory.get().serializeMap( errorMessages, DataStorageMethod.class, String.class );
            final ErrorInformation errorInfo = new ErrorInformation( PwmError.ERROR_WRITING_RESPONSES, errorMsg );
            throw new PwmOperationalException( errorInfo );
        }

        if ( attempts != successes )
        {
            final String errorMsg = "response storage only partially successful; attempts=" + attempts + ", successes=" + successes
                    + ", detail=" + JsonFactory.get().serializeMap( errorMessages, DataStorageMethod.class, String.class );
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
        int attempts = 0;
        int successes = 0;

        final List<DataStorageMethod> writeMethods = pwmDomain.getConfig().getCrWritePreference();

        LOGGER.debug( sessionLabel, () -> "will attempt to clear the following storage methods: "
                + JsonFactory.get().serializeCollection( writeMethods ) + " for user " + theUser.getEntryDN()
                + theUser.getEntryDN() + " guid=" + userGUID );

        for ( final DataStorageMethod loopWriteMethod : writeMethods )
        {
            try
            {
                attempts++;
                operatorMap.get( loopWriteMethod ).clearResponses( sessionLabel, userIdentity, theUser, userGUID );
                LOGGER.debug( sessionLabel, () -> "cleared responses using storage method " + loopWriteMethod + " for user " + theUser.getEntryDN() );
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
            final PwmDomain pwmDomain,
            final SessionLabel sessionLabel,
            final UserIdentity userIdentity,
            final ChallengeSet challengeSet,
            final ResponseInfoBean responseInfoBean
    )
            throws ChaiUnavailableException, PwmUnrecoverableException
    {
        LOGGER.trace( sessionLabel, () -> "beginning check to determine if responses need to be configured for user" );

        final DomainConfig config = pwmDomain.getConfig();

        if ( !config.readSettingAsBoolean( PwmSetting.SETUP_RESPONSE_ENABLE ) )
        {
            LOGGER.debug( sessionLabel, () -> "checkIfResponseConfigNeeded: response setup is disabled, so user is not required to setup responses" );
            return false;
        }

        {
            final Optional<String> profileId = ProfileUtility.discoverProfileIDForUser( pwmDomain, sessionLabel, userIdentity, ProfileDefinition.SetupResponsesProfile );
            if ( profileId.isPresent() )
            {
                final SetupResponsesProfile setupResponsesProfile = pwmDomain.getConfig().getSetupResponseProfiles().get( profileId.get() );
                if ( setupResponsesProfile != null )
                {
                    if ( !setupResponsesProfile.readSettingAsBoolean( PwmSetting.SETUP_RESPONSES_FORCE_SETUP ) )
                    {
                        LOGGER.debug( sessionLabel, () -> "checkIfResponseConfigNeeded: force response setup is disabled, so user is not required to setup responses" );
                        return false;
                    }

                    if ( !UserPermissionUtility.testUserPermission( pwmDomain, sessionLabel, userIdentity,
                            setupResponsesProfile.readSettingAsUserPermission( PwmSetting.QUERY_MATCH_CHECK_RESPONSES ) ) )
                    {
                        LOGGER.debug( sessionLabel, () -> "checkIfResponseConfigNeeded: " + userIdentity + " is not eligible for checkIfResponseConfigNeeded due to query match" );
                        return false;
                    }
                }
            }
            else
            {
                LOGGER.debug( sessionLabel, () -> "checkIfResponseConfigNeeded: " + userIdentity + " does not have permission to setup responses" );
                return false;
            }
        }

        // check to be sure there are actually challenges in the challenge set
        if ( challengeSet == null || challengeSet.getChallenges().isEmpty() )
        {
            LOGGER.debug( sessionLabel, () -> "checkIfResponseConfigNeeded: no challenge sets configured for user " + userIdentity );
            return false;
        }

        // ignore NMAS based CR set if so configured
        if ( responseInfoBean != null && ( responseInfoBean.getDataStorageMethod() == DataStorageMethod.NMAS ) )
        {
            final boolean ignoreNmasCr = Boolean.parseBoolean( pwmDomain.getConfig().readAppProperty( AppProperty.NMAS_IGNORE_NMASCR_DURING_FORCECHECK ) );
            if ( ignoreNmasCr )
            {
                LOGGER.debug( sessionLabel, () -> "checkIfResponseConfigNeeded: app property " + AppProperty.NMAS_IGNORE_NMASCR_DURING_FORCECHECK.getKey()
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
            LOGGER.debug( sessionLabel, () -> "checkIfResponseConfigNeeded: " + userIdentity + " has good responses" );
            return false;
        }
        catch ( final Exception e )
        {
            LOGGER.debug( sessionLabel, () -> "checkIfResponseConfigNeeded: " + userIdentity + " does not have good responses: " + e.getMessage() );
            return true;
        }
    }

    @Override
    public ServiceInfoBean serviceInfo( )
    {
        final DomainConfig config = pwmDomain.getConfig();
        final Set<DataStorageMethod> usedStorageMethods = Stream.concat( config.getCrReadPreference().stream(), config.getCrWritePreference().stream() )
                .collect( Collectors.toSet() );

        return ServiceInfoBean.builder().storageMethods( usedStorageMethods ).build();
    }
}
