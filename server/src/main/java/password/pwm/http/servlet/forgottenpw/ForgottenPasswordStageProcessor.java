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

package password.pwm.http.servlet.forgottenpw;

import password.pwm.PwmApplication;
import password.pwm.bean.PasswordStatus;
import password.pwm.bean.SessionLabel;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.IdentityVerificationMethod;
import password.pwm.config.option.RecoveryAction;
import password.pwm.config.option.RecoveryMinLifetimeOption;
import password.pwm.config.profile.ForgottenPasswordProfile;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequestContext;
import password.pwm.http.PwmRequestAttribute;
import password.pwm.http.bean.ForgottenPasswordBean;
import password.pwm.http.bean.ForgottenPasswordStage;
import password.pwm.ldap.UserInfo;
import password.pwm.svc.stats.Statistic;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.password.PasswordUtility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

class ForgottenPasswordStageProcessor
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( ForgottenPasswordStateMachine.class );
    private static final List<NextStageProcessor> NEXT_STAGE_PROCESSORS;

    static ForgottenPasswordStage nextStage( final ForgottenPasswordStateMachine stateMachine )
            throws PwmUnrecoverableException
    {
        for ( final NextStageProcessor nextStageProcessor : NEXT_STAGE_PROCESSORS )
        {
            final Optional<ForgottenPasswordStage> nextStage = nextStageProcessor.nextStage( stateMachine );
            if ( nextStage.isPresent() )
            {
                return nextStage.get();
            }
        }
        return ForgottenPasswordStage.IDENTIFICATION;
    }

    static
    {
        final List<NextStageProcessor> list = new ArrayList<>();
        list.add( new StageProcessor1() );
        list.add( new StageProcessor2() );
        list.add( new StageProcessor3() );
        list.add( new StageProcessor4() );
        list.add( new StageProcessor5() );
        list.add( new StageProcessor6() );
        list.add( new StageProcessor7() );
        NEXT_STAGE_PROCESSORS = Collections.unmodifiableList( list );
    }

    private interface NextStageProcessor
    {
        Optional<ForgottenPasswordStage> nextStage( ForgottenPasswordStateMachine stateMachine )
                throws PwmUnrecoverableException;
    }

    private static class StageProcessor1 implements  NextStageProcessor
    {
        @Override
        public Optional<ForgottenPasswordStage> nextStage( final ForgottenPasswordStateMachine stateMachine )
        {
            final PwmRequestContext pwmRequestContext = stateMachine.getCommonValues();

            final ForgottenPasswordBean forgottenPasswordBean = stateMachine.getForgottenPasswordBean();
            stateMachine.getRequestFlags().clear();

            // check if completed
            if ( forgottenPasswordBean.getProgress().getExecutedRecoveryAction() != null )
            {
                return Optional.of( ForgottenPasswordStage.COMPLETE );
            }

            // check locale
            if ( forgottenPasswordBean.getUserLocale() == null )
            {
                forgottenPasswordBean.setUserLocale( pwmRequestContext.getLocale() );
            }

            if ( !Objects.equals( forgottenPasswordBean.getUserLocale(), pwmRequestContext.getLocale() ) )
            {
                LOGGER.debug( pwmRequestContext.getSessionLabel(), () -> "user locale has changed, resetting forgotten password state" );
                stateMachine.clear();
                return Optional.of( ForgottenPasswordStage.IDENTIFICATION );
            }

            // check for identified user;
            if ( forgottenPasswordBean.getUserIdentity() == null && !forgottenPasswordBean.isBogusUser() )
            {
                return Optional.of( ForgottenPasswordStage.IDENTIFICATION );
            }

            return Optional.empty();
        }
    }

    private static class StageProcessor2 implements NextStageProcessor
    {

        @Override
        public Optional<ForgottenPasswordStage> nextStage( final ForgottenPasswordStateMachine stateMachine )
                throws PwmUnrecoverableException
        {
            final ForgottenPasswordBean forgottenPasswordBean = stateMachine.getForgottenPasswordBean();
            final PwmRequestContext pwmRequestContext = stateMachine.getCommonValues();
            final PwmApplication pwmApplication = pwmRequestContext.getPwmApplication();
            final SessionLabel sessionLabel = pwmRequestContext.getSessionLabel();
            final Configuration config = pwmApplication.getConfig();

            final ForgottenPasswordBean.RecoveryFlags recoveryFlags = forgottenPasswordBean.getRecoveryFlags();
            final ForgottenPasswordBean.Progress progress = forgottenPasswordBean.getProgress();

            if ( forgottenPasswordBean.isBogusUser() )
            {
                return Optional.of( ForgottenPasswordStage.VERIFICATION );
            }

            final ForgottenPasswordProfile forgottenPasswordProfile = ForgottenPasswordUtil.forgottenPasswordProfile( pwmApplication, forgottenPasswordBean );
            {
                final Map<String, ForgottenPasswordProfile> profileIDList = config.getForgottenPasswordProfiles();
                final String profileDebugMsg = forgottenPasswordProfile != null && profileIDList != null && profileIDList.size() > 1
                        ? " profile=" + forgottenPasswordProfile.getIdentifier() + ", "
                        : "";
                LOGGER.trace( sessionLabel, () -> "entering forgotten password progress engine: "
                        + profileDebugMsg
                        + "flags=" + JsonUtil.serialize( recoveryFlags ) + ", "
                        + "progress=" + JsonUtil.serialize( progress ) );
            }

            if ( forgottenPasswordProfile == null )
            {
                throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_NO_PROFILE_ASSIGNED ) );
            }

            // dispatch required auth methods.
            for ( final IdentityVerificationMethod method : recoveryFlags.getRequiredAuthMethods() )
            {
                if ( !progress.getSatisfiedMethods().contains( method ) )
                {
                    return Optional.of( ForgottenPasswordStage.VERIFICATION );
                }
            }

            return Optional.empty();
        }
    }

    static class StageProcessor3 implements NextStageProcessor
    {
        @Override
        public Optional<ForgottenPasswordStage> nextStage( final ForgottenPasswordStateMachine stateMachine )
        {
            final ForgottenPasswordBean forgottenPasswordBean = stateMachine.getForgottenPasswordBean();
            final ForgottenPasswordBean.Progress progress = forgottenPasswordBean.getProgress();

            if ( Objects.equals( progress.getInProgressVerificationMethod(), IdentityVerificationMethod.TOKEN ) )
            {
                if ( progress.getTokenDestination() == null )
                {
                    return Optional.of( ForgottenPasswordStage.TOKEN_CHOICE );
                }
            }

            // redirect if an verification method is in progress
            if ( progress.getInProgressVerificationMethod() != null )
            {
                if ( progress.getSatisfiedMethods().contains( progress.getInProgressVerificationMethod() ) )
                {
                    progress.setInProgressVerificationMethod( null );
                }
                else
                {
                    stateMachine.getRequestFlags().put( PwmRequestAttribute.ForgottenPasswordOptionalPageView, "true" );
                    return Optional.of( ForgottenPasswordStage.VERIFICATION );
                }
            }

            return Optional.empty();
        }
    }

    static class StageProcessor4 implements NextStageProcessor
    {
        @Override
        public Optional<ForgottenPasswordStage> nextStage( final ForgottenPasswordStateMachine stateMachine )
                throws PwmUnrecoverableException
        {
            final ForgottenPasswordBean forgottenPasswordBean = stateMachine.getForgottenPasswordBean();
            final PwmRequestContext pwmRequestContext = stateMachine.getCommonValues();
            final SessionLabel sessionLabel = pwmRequestContext.getSessionLabel();

            final ForgottenPasswordBean.RecoveryFlags recoveryFlags = forgottenPasswordBean.getRecoveryFlags();
            final ForgottenPasswordBean.Progress progress = forgottenPasswordBean.getProgress();

            // check if more optional methods required
            if ( recoveryFlags.getMinimumOptionalAuthMethods() > 0 )
            {
                final Set<IdentityVerificationMethod> satisfiedOptionalMethods = ForgottenPasswordUtil.figureSatisfiedOptionalAuthMethods( recoveryFlags, progress );
                if ( satisfiedOptionalMethods.size() < recoveryFlags.getMinimumOptionalAuthMethods() )
                {
                    final Set<IdentityVerificationMethod> remainingAvailableOptionalMethods = ForgottenPasswordUtil.figureRemainingAvailableOptionalAuthMethods(
                            pwmRequestContext,
                            forgottenPasswordBean
                    );

                    // for rest method, fail if any required methods are not supported
                    {
                        final Set<IdentityVerificationMethod> tempSet = JavaHelper.copiedEnumSet( remainingAvailableOptionalMethods, IdentityVerificationMethod.class );
                        tempSet.removeAll( ForgottenPasswordStateMachine.supportedVerificationMethods() );
                        if ( !tempSet.isEmpty() )
                        {
                            final IdentityVerificationMethod unsupportedMethod = tempSet.iterator().next();
                            final String msg = "verification method " + unsupportedMethod + " is configured but is not available for use by REST service";
                            throw new PwmUnrecoverableException( PwmError.CONFIG_FORMAT_ERROR, msg );
                        }
                    }

                    if ( remainingAvailableOptionalMethods.isEmpty() )
                    {
                        final String errorMsg = "additional optional verification methods are needed"
                                + ", however all available optional verification methods have been satisfied by user";
                        final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_RECOVERY_SEQUENCE_INCOMPLETE, errorMsg );
                        LOGGER.error( sessionLabel, errorInformation );
                        throw new PwmUnrecoverableException( errorInformation );
                    }
                    else
                    {
                        if ( remainingAvailableOptionalMethods.size() == 1 )
                        {
                            final IdentityVerificationMethod remainingMethod = remainingAvailableOptionalMethods.iterator().next();
                            LOGGER.debug( sessionLabel, () -> "only 1 remaining available optional verification method, will redirect to " + remainingMethod.toString() );
                            progress.setInProgressVerificationMethod( remainingMethod );
                            return Optional.of( ForgottenPasswordStage.VERIFICATION );
                        }
                    }
                    return Optional.of( ForgottenPasswordStage.METHOD_CHOICE );
                }
            }

            return Optional.empty();
        }
    }

    static class StageProcessor5 implements NextStageProcessor
    {
        @Override
        public Optional<ForgottenPasswordStage> nextStage( final ForgottenPasswordStateMachine stateMachine )
                throws PwmUnrecoverableException
        {
            final ForgottenPasswordBean forgottenPasswordBean = stateMachine.getForgottenPasswordBean();
            final PwmRequestContext pwmRequestContext = stateMachine.getCommonValues();
            final SessionLabel sessionLabel = pwmRequestContext.getSessionLabel();

            final ForgottenPasswordBean.RecoveryFlags recoveryFlags = forgottenPasswordBean.getRecoveryFlags();
            final ForgottenPasswordBean.Progress progress = forgottenPasswordBean.getProgress();

            if ( progress.getSatisfiedMethods().isEmpty() )
            {
                final String errorMsg = "forgotten password recovery sequence completed, but user has not actually satisfied any verification methods";
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_RECOVERY_SEQUENCE_INCOMPLETE, errorMsg );
                LOGGER.error( sessionLabel, errorInformation );
                stateMachine.clear();
                throw new PwmUnrecoverableException( errorInformation );
            }

            {
                final int satisfiedMethods = progress.getSatisfiedMethods().size();
                final int totalMethodsNeeded = recoveryFlags.getRequiredAuthMethods().size() + recoveryFlags.getMinimumOptionalAuthMethods();
                if ( satisfiedMethods < totalMethodsNeeded )
                {
                    final String errorMsg = "forgotten password recovery sequence completed " + satisfiedMethods + " methods, "
                            + " but policy requires a total of " + totalMethodsNeeded + " methods";
                    final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_RECOVERY_SEQUENCE_INCOMPLETE, errorMsg );
                    LOGGER.error( sessionLabel, errorInformation );
                    stateMachine.clear();
                    throw new PwmUnrecoverableException( errorInformation );
                }
            }

            return Optional.empty();
        }
    }

    static class StageProcessor6 implements NextStageProcessor
    {
        @Override
        public Optional<ForgottenPasswordStage> nextStage( final ForgottenPasswordStateMachine stateMachine )
                throws PwmUnrecoverableException
        {
            final ForgottenPasswordBean forgottenPasswordBean = stateMachine.getForgottenPasswordBean();
            final PwmRequestContext pwmRequestContext = stateMachine.getCommonValues();
            final PwmApplication pwmApplication = pwmRequestContext.getPwmApplication();
            final SessionLabel sessionLabel = pwmRequestContext.getSessionLabel();

            if ( !forgottenPasswordBean.getProgress().isAllPassed() )
            {
                forgottenPasswordBean.getProgress().setAllPassed( true );
                pwmApplication.getStatisticsManager().incrementValue( Statistic.RECOVERY_SUCCESSES );
            }

            final UserInfo userInfo = ForgottenPasswordUtil.readUserInfo( pwmRequestContext, forgottenPasswordBean );
            if ( userInfo == null )
            {
                throw PwmUnrecoverableException.newException( PwmError.ERROR_INTERNAL, "unable to load userInfo while processing forgotten password controller" );
            }

            // check if user's pw is within min lifetime window
            final ForgottenPasswordProfile forgottenPasswordProfile = ForgottenPasswordUtil.forgottenPasswordProfile( pwmApplication, forgottenPasswordBean );
            final RecoveryMinLifetimeOption minLifetimeOption = forgottenPasswordProfile.readSettingAsEnum(
                    PwmSetting.RECOVERY_MINIMUM_PASSWORD_LIFETIME_OPTIONS,
                    RecoveryMinLifetimeOption.class
            );
            if ( minLifetimeOption == RecoveryMinLifetimeOption.NONE
                    || (
                    !userInfo.isPasswordLocked()
                            &&  minLifetimeOption == RecoveryMinLifetimeOption.UNLOCKONLY )
            )
            {
                if ( userInfo.isWithinPasswordMinimumLifetime() )
                {
                    PasswordUtility.throwPasswordTooSoonException( userInfo, sessionLabel );
                }
            }

            return Optional.empty();
        }
    }

    static class StageProcessor7 implements NextStageProcessor
    {
        @Override
        public Optional<ForgottenPasswordStage> nextStage( final ForgottenPasswordStateMachine stateMachine )
                throws PwmUnrecoverableException
        {
            final ForgottenPasswordBean forgottenPasswordBean = stateMachine.getForgottenPasswordBean();
            final PwmRequestContext pwmRequestContext = stateMachine.getCommonValues();
            final PwmApplication pwmApplication = pwmRequestContext.getPwmApplication();
            final SessionLabel sessionLabel = pwmRequestContext.getSessionLabel();
            final Configuration config = pwmApplication.getConfig();

            final UserInfo userInfo = ForgottenPasswordUtil.readUserInfo( pwmRequestContext, forgottenPasswordBean );

            final ForgottenPasswordProfile forgottenPasswordProfile = ForgottenPasswordUtil.forgottenPasswordProfile( pwmApplication, forgottenPasswordBean );

            final RecoveryMinLifetimeOption minLifetimeOption = forgottenPasswordProfile.readSettingAsEnum(
                    PwmSetting.RECOVERY_MINIMUM_PASSWORD_LIFETIME_OPTIONS,
                    RecoveryMinLifetimeOption.class
            );
            final boolean disallowAllButUnlock = minLifetimeOption == RecoveryMinLifetimeOption.UNLOCKONLY
                    && userInfo.isPasswordLocked();

            LOGGER.trace( sessionLabel, () -> "all recovery checks passed, proceeding to configured recovery action" );

            final RecoveryAction recoveryAction = ForgottenPasswordUtil.getRecoveryAction( config, forgottenPasswordBean );
            if ( recoveryAction == RecoveryAction.SENDNEWPW || recoveryAction == RecoveryAction.SENDNEWPW_AND_EXPIRE )
            {
                if ( disallowAllButUnlock )
                {
                    PasswordUtility.throwPasswordTooSoonException( userInfo, sessionLabel );
                }
                return Optional.of( ForgottenPasswordStage.COMPLETE );
            }

            if ( forgottenPasswordProfile.readSettingAsBoolean( PwmSetting.RECOVERY_ALLOW_UNLOCK ) )
            {
                final PasswordStatus passwordStatus = userInfo.getPasswordStatus();

                if ( !passwordStatus.isExpired() && !passwordStatus.isPreExpired() )
                {
                    if ( userInfo.isPasswordLocked() )
                    {
                        final boolean inhibitReset = minLifetimeOption != RecoveryMinLifetimeOption.ALLOW
                                && userInfo.isWithinPasswordMinimumLifetime();

                        stateMachine.getRequestFlags().put( PwmRequestAttribute.ForgottenPasswordInhibitPasswordReset, String.valueOf( inhibitReset ) );
                        return Optional.of( ForgottenPasswordStage.ACTION_CHOICE );
                    }
                }
            }

            return Optional.of( ForgottenPasswordStage.NEW_PASSWORD );
        }
    }

}
