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

package password.pwm.config;

import password.pwm.AppProperty;
import password.pwm.bean.DomainID;
import password.pwm.bean.EmailItemBean;
import password.pwm.bean.PrivateKeyCertificate;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.config.option.TokenStorageMethod;
import password.pwm.config.profile.ActivateUserProfile;
import password.pwm.config.profile.ChallengeProfile;
import password.pwm.config.profile.ChangePasswordProfile;
import password.pwm.config.profile.ForgottenPasswordProfile;
import password.pwm.config.profile.HelpdeskProfile;
import password.pwm.config.profile.LdapProfile;
import password.pwm.config.profile.NewUserProfile;
import password.pwm.config.profile.PeopleSearchProfile;
import password.pwm.config.profile.Profile;
import password.pwm.config.profile.ProfileDefinition;
import password.pwm.config.profile.PwmPasswordPolicy;
import password.pwm.config.profile.SetupOtpProfile;
import password.pwm.config.profile.SetupResponsesProfile;
import password.pwm.config.profile.UpdateProfileProfile;
import password.pwm.config.stored.StoredConfiguration;
import password.pwm.config.stored.StoredConfigurationUtil;
import password.pwm.config.value.FileValue;
import password.pwm.config.value.data.ActionConfiguration;
import password.pwm.config.value.data.FormConfiguration;
import password.pwm.config.value.data.NamedSecretData;
import password.pwm.config.value.data.RemoteWebServiceConfiguration;
import password.pwm.config.value.data.UserPermission;
import password.pwm.error.PwmInternalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.i18n.PwmLocaleBundle;
import password.pwm.util.PasswordData;
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmHashAlgorithm;
import password.pwm.util.secure.PwmSecurityKey;
import password.pwm.util.secure.SecureEngine;
import password.pwm.http.HttpMethod;

import java.io.StringWriter;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author Jason D. Rivard
 */
public class DomainConfig implements SettingReader
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( DomainConfig.class );

    private final StoredConfiguration storedConfiguration;
    private final AppConfig appConfig;
    private final DomainID domainID;

    private final Map<String, PwmPasswordPolicy> cachedPasswordPolicy;
    private final Map<String, Map<Locale, ChallengeProfile>> cachedChallengeProfiles;
    private final Map<String, LdapProfile> ldapProfiles;
    private final StoredSettingReader settingReader;
    private final PwmSecurityKey domainSecurityKey;

    public DomainConfig( final AppConfig appConfig, final DomainID domainID )
    {
        this.appConfig = Objects.requireNonNull( appConfig );
        this.storedConfiguration = appConfig.getStoredConfiguration();
        this.domainID = Objects.requireNonNull( domainID );
        this.settingReader = new StoredSettingReader( storedConfiguration, null, domainID );

        this.cachedPasswordPolicy = Collections.unmodifiableMap( getPasswordProfileIDs().stream()
                .map( profile -> PwmPasswordPolicy.createPwmPasswordPolicy( this, profile ) )
                .collect( Collectors.toMap(
                        PwmPasswordPolicy::getIdentifier,
                        Function.identity()
                ) ) );

        this.cachedChallengeProfiles = Collections.unmodifiableMap( getChallengeProfileIDs().stream()
                .collect( Collectors.toMap(
                        Function.identity(),
                        profileId -> Collections.unmodifiableMap( appConfig.getKnownLocales().stream()
                                .collect( Collectors.toMap(
                                        Function.identity(),
                                        locale -> ChallengeProfile.readChallengeProfileFromConfig( domainID, profileId, locale, storedConfiguration )
                                ) ) )
                ) ) );

        this.ldapProfiles = makeLdapProfileMap( this );
        this.domainSecurityKey = makeDomainSecurityKey( this );
    }

    public AppConfig getAppConfig()
    {
        return appConfig;
    }

    public boolean isAdministrativeDomain()
    {
        final String adminDomainStr = getAppConfig().readSettingAsString( PwmSetting.DOMAIN_SYSTEM_ADMIN );
        return getDomainID().stringValue().equals( adminDomainStr );
    }

    public List<FormConfiguration> readSettingAsForm( final PwmSetting setting )
    {
        return settingReader.readSettingAsForm( setting );
    }

    public List<UserPermission> readSettingAsUserPermission( final PwmSetting setting )
    {
        return settingReader.readSettingAsUserPermission( setting );
    }

    public Map<String, LdapProfile> getLdapProfiles( )
    {
        return ldapProfiles;
    }

    public EmailItemBean readSettingAsEmail( final PwmSetting setting, final Locale locale )
    {
        return settingReader.readSettingAsEmail( setting, locale );
    }

    public <E extends Enum<E>> E readSettingAsEnum( final PwmSetting setting, final Class<E> enumClass )
    {
        return settingReader.readSettingAsEnum( setting, enumClass );
    }

    @Override
    public <E extends Enum<E>> Set<E> readSettingAsOptionList( final PwmSetting setting, final Class<E> enumClass )
    {
        return settingReader.readSettingAsOptionList( setting, enumClass );
    }

    public List<ActionConfiguration> readSettingAsAction( final PwmSetting setting )
    {
        return settingReader.readSettingAsAction( setting );
    }

    @Override
    public List<String> readSettingAsLocalizedStringArray( final PwmSetting setting, final Locale locale )
    {
        return settingReader.readSettingAsLocalizedStringArray( setting, locale );
    }

    public String readSettingAsString( final PwmSetting setting )
    {
        return settingReader.readSettingAsString( setting );
    }

    public List<RemoteWebServiceConfiguration> readSettingAsRemoteWebService( final PwmSetting setting )
    {
        return settingReader.readSettingAsRemoteWebService( setting );
    }

    public PasswordData readSettingAsPassword( final PwmSetting setting )
    {
        return settingReader.readSettingAsPassword( setting );
    }

    public Map<String, NamedSecretData> readSettingAsNamedPasswords( final PwmSetting setting )
    {
        return settingReader.readSettingAsNamedPasswords( setting );
    }

    public Optional<Map<Locale, String>> readLocalizedBundle( final PwmLocaleBundle className, final String keyName )
    {
        return settingReader.readLocalizedBundle( className, keyName );
    }

    public List<String> getChallengeProfileIDs( )
    {
        return StoredConfigurationUtil.profilesForSetting( this.getDomainID(), PwmSetting.CHALLENGE_PROFILE_LIST, storedConfiguration );
    }

    public ChallengeProfile getChallengeProfile( final String profile, final Locale locale )
    {
        final Map<Locale, ChallengeProfile> cachedLocaleMap = cachedChallengeProfiles.get( profile );

        if ( cachedLocaleMap == null )
        {
            throw new IllegalArgumentException( "unknown challenge profileID specified: " + profile );
        }

        return cachedLocaleMap.get( locale );
    }

    public long readSettingAsLong( final PwmSetting setting )
    {
        return settingReader.readSettingAsLong( setting );
    }

    public PwmPasswordPolicy getPasswordPolicy( final String profile )
    {
        return cachedPasswordPolicy.get( profile );
    }

    public List<String> getPasswordProfileIDs( )
    {
        return StoredConfigurationUtil.profilesForSetting( this.getDomainID(), PwmSetting.PASSWORD_PROFILE_LIST, storedConfiguration );
    }

    public List<String> readSettingAsStringArray( final PwmSetting setting )
    {
        return settingReader.readSettingAsStringArray( setting );
    }

    public String readSettingAsLocalizedString( final PwmSetting setting, final Locale locale )
    {
        return settingReader.readSettingAsLocalizedString( setting, locale );
    }

    public boolean readSettingAsBoolean( final PwmSetting setting )
    {
        return settingReader.readSettingAsBoolean( setting );
    }

    public Map<FileValue.FileInformation, FileValue.FileContent> readSettingAsFile( final PwmSetting setting )
    {
        return settingReader.readSettingAsFile( setting );
    }

    public List<X509Certificate> readSettingAsCertificate( final PwmSetting setting )
    {
        return settingReader.readSettingAsCertificate( setting );
    }

    public PrivateKeyCertificate readSettingAsPrivateKey( final PwmSetting setting )
    {
        return settingReader.readSettingAsPrivateKey( setting );
    }

    public HttpMethod readSettingAsHttpMethod( final PwmSetting setting )
    {
        return settingReader.readSettingAsEnum( setting,
                password.pwm.config.option.HttpMethod.class ) == password.pwm.config.option.HttpMethod.POST
                        ? HttpMethod.POST
                        : HttpMethod.GET;
    }

    public PwmSecurityKey getSecurityKey( ) throws PwmUnrecoverableException
    {
        return domainSecurityKey;
    }

    public List<DataStorageMethod> readGenericStorageLocations( final PwmSetting setting )
    {
        return settingReader.readGenericStorageLocations( setting );
    }

    public LdapProfile getDefaultLdapProfile( ) throws PwmUnrecoverableException
    {
        return getLdapProfiles().values().iterator().next();
    }

    public Optional<TokenStorageMethod> getTokenStorageMethod( )
    {
        return JavaHelper.readEnumFromString( TokenStorageMethod.class, readSettingAsString( PwmSetting.TOKEN_STORAGEMETHOD ) );
    }

    public PwmSettingTemplateSet getTemplate( )
    {
        return storedConfiguration.getTemplateSets().get( domainID );
    }

    public String readAppProperty( final AppProperty property )
    {
        return appConfig.readAppProperty( property );
    }

    public DomainID getDomainID()
    {
        return domainID;
    }

    /* generic profile stuff */
    public Map<String, NewUserProfile> getNewUserProfiles( )
    {
        return this.getProfileMap( ProfileDefinition.NewUser );
    }

    public Map<String, ActivateUserProfile> getUserActivationProfiles( )
    {
        return this.getProfileMap( ProfileDefinition.ActivateUser );
    }

    public Map<String, HelpdeskProfile> getHelpdeskProfiles( )
    {
        return this.getProfileMap( ProfileDefinition.Helpdesk );
    }

    public Map<String, PeopleSearchProfile> getPeopleSearchProfiles( )
    {
        return this.getProfileMap( ProfileDefinition.PeopleSearch );
    }

    public Map<String, SetupOtpProfile> getSetupOTPProfiles( )
    {
        return this.getProfileMap( ProfileDefinition.SetupOTPProfile );
    }

    public Map<String, SetupResponsesProfile> getSetupResponseProfiles( )
    {
        return this.getProfileMap( ProfileDefinition.SetupResponsesProfile );
    }

    public Map<String, UpdateProfileProfile> getUpdateAttributesProfile( )
    {
        return this.getProfileMap( ProfileDefinition.UpdateAttributes );
    }

    public Map<String, ChangePasswordProfile> getChangePasswordProfile( )
    {
        return this.getProfileMap( ProfileDefinition.ChangePassword );
    }

    public Map<String, ForgottenPasswordProfile> getForgottenPasswordProfiles( )
    {
        return this.getProfileMap( ProfileDefinition.ForgottenPassword );
    }

    public <T extends Profile> Map<String, T> getProfileMap( final ProfileDefinition profileDefinition )
    {
        return settingReader.getProfileMap( profileDefinition );
    }

    public StoredConfiguration getStoredConfiguration( )
    {
        return this.storedConfiguration;
    }

    public Optional<PeopleSearchProfile> getPublicPeopleSearchProfile()
    {
        if ( readSettingAsBoolean( PwmSetting.PEOPLE_SEARCH_ENABLE_PUBLIC ) )
        {
            final String profileID = readSettingAsString( PwmSetting.PEOPLE_SEARCH_PUBLIC_PROFILE );
            final Map<String, PeopleSearchProfile> profiles = settingReader.getProfileMap( ProfileDefinition.PeopleSearchPublic );
            return Optional.ofNullable( profiles.get( profileID ) );
        }
        return Optional.empty();
    }

    public String getDisplayName( final Locale locale )
    {
        return getDomainID().toString();
    }

    public List<DataStorageMethod> getCrReadPreference()
    {
        return calculateMethods( PwmSetting.FORGOTTEN_PASSWORD_READ_PREFERENCE, PwmSetting.EDIRECTORY_USE_NMAS_RESPONSES );
    }

    public List<DataStorageMethod> getCrWritePreference()
    {
        return calculateMethods( PwmSetting.FORGOTTEN_PASSWORD_WRITE_PREFERENCE, PwmSetting.EDIRECTORY_STORE_NMAS_RESPONSES );
    }

    private List<DataStorageMethod> calculateMethods(
            final PwmSetting setting,
            final PwmSetting addNmasSetting
    )
    {
        final List<DataStorageMethod> methods = new ArrayList<>( this.readGenericStorageLocations( setting ) );
        if ( methods.size() == 1 && methods.get( 0 ) == DataStorageMethod.AUTO )
        {
            methods.clear();
            if ( getAppConfig().hasDbConfigured() )
            {
                methods.add( DataStorageMethod.DB );
            }
            else
            {
                methods.add( DataStorageMethod.LDAP );
            }
        }
        if ( this.readSettingAsBoolean( addNmasSetting ) )
        {
            methods.add( DataStorageMethod.NMAS );
        }
        return Collections.unmodifiableList( methods );
    }


    private static Map<String, LdapProfile> makeLdapProfileMap( final DomainConfig domainConfig )
    {
        final Map<String, LdapProfile> sourceMap = domainConfig.getProfileMap( ProfileDefinition.LdapProfile );

        return Collections.unmodifiableMap( sourceMap.entrySet()
                .stream()
                .filter( entry -> entry.getValue().isEnabled() )
                .collect( CollectionUtil.collectorToLinkedMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue ) ) );
    }

    private static PwmSecurityKey makeDomainSecurityKey( final DomainConfig domainConfig )
            throws PwmInternalException
    {
        try
        {
            final StringWriter keyData = new StringWriter();
            keyData.append( domainConfig.getDomainID().stringValue() );
            CollectionUtil.iteratorToStream( domainConfig.getStoredConfiguration().keys() )
                    .filter( key -> Objects.equals( key.getDomainID(), domainConfig.getDomainID() ) )
                    .sorted()
                    .map( s -> domainConfig.getStoredConfiguration().readStoredValue( s ) )
                    .flatMap( Optional::stream )
                    .forEach( value -> keyData.append( value.valueHash() ) );

            final String hashedData = SecureEngine.hash( keyData.toString(), PwmHashAlgorithm.SHA512 );
            final PwmSecurityKey domainKey = new PwmSecurityKey( hashedData );
            return domainConfig.getAppConfig().getSecurityKey().add( domainKey );
        }
        catch ( final PwmUnrecoverableException e )
        {
            throw PwmInternalException.fromPwmException( "error while generating domain-specific security key", e );
        }
    }

}
