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

package password.pwm;

import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;
import password.pwm.bean.DomainID;
import password.pwm.config.AppConfig;
import password.pwm.config.PwmSetting;
import password.pwm.config.stored.StoredConfigKey;
import password.pwm.config.stored.StoredConfigurationFactory;
import password.pwm.config.stored.StoredConfigurationModifier;
import password.pwm.config.value.StringArrayValue;
import password.pwm.config.value.StringValue;
import password.pwm.error.PwmUnrecoverableException;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class PwmDomainUtilTest
{
    private static final List<String> INITIAL_DOMAIN_LIST = List.of( "acme1", "acme2", "acme3" );

    @Test
    public void categorizeDomainModificationsUnchangedTest()
            throws PwmUnrecoverableException
    {
        final Map<PwmDomainUtil.DomainModifyCategory, Set<DomainID>> categorizations = doSimpleCategorizationWithNewDomainList( INITIAL_DOMAIN_LIST );

        Assert.assertEquals( 3, categorizations.get( PwmDomainUtil.DomainModifyCategory.unchanged ).size() );
        Assert.assertEquals( 0, categorizations.get( PwmDomainUtil.DomainModifyCategory.removed ).size() );
        Assert.assertEquals( 0, categorizations.get( PwmDomainUtil.DomainModifyCategory.created ).size() );
        Assert.assertEquals( 0, categorizations.get( PwmDomainUtil.DomainModifyCategory.modified ).size() );
    }

    @Test
    public void categorizeDomainModificationsRemovedTest()
            throws PwmUnrecoverableException
    {
        final Map<PwmDomainUtil.DomainModifyCategory, Set<DomainID>> categorizations = doSimpleCategorizationWithNewDomainList( List.of( "acme1", "acme2"  ) );

        Assert.assertEquals( 2, categorizations.get( PwmDomainUtil.DomainModifyCategory.unchanged ).size() );
        Assert.assertEquals( 1, categorizations.get( PwmDomainUtil.DomainModifyCategory.removed ).size() );
        Assert.assertEquals( 0, categorizations.get( PwmDomainUtil.DomainModifyCategory.created ).size() );
        Assert.assertEquals( 0, categorizations.get( PwmDomainUtil.DomainModifyCategory.modified ).size() );
    }

    @Test
    public void categorizeDomainModificationsCreatedTest()
            throws PwmUnrecoverableException
    {
        final Map<PwmDomainUtil.DomainModifyCategory, Set<DomainID>> categorizations = doSimpleCategorizationWithNewDomainList( List.of( "acme1", "acme2", "acme3", "acme4" ) );

        Assert.assertEquals( 3, categorizations.get( PwmDomainUtil.DomainModifyCategory.unchanged ).size() );
        Assert.assertEquals( 0, categorizations.get( PwmDomainUtil.DomainModifyCategory.removed ).size() );
        Assert.assertEquals( 1, categorizations.get( PwmDomainUtil.DomainModifyCategory.created ).size() );
        Assert.assertEquals( 0, categorizations.get( PwmDomainUtil.DomainModifyCategory.modified ).size() );
    }

    @Test
    public void categorizeDomainModificationsModifiedTest()
            throws PwmUnrecoverableException
    {
        final AppConfig initialConfig = initialAppConfig();
        final StoredConfigurationModifier modifier = StoredConfigurationModifier.newModifier( initialConfig.getStoredConfiguration() );
        modifier.writeSetting( StoredConfigKey.forSetting( PwmSetting.NOTES, null, DomainID.create( "acme1" ) ), new StringValue( "value!" ), null );
        final AppConfig modifiedConfig = AppConfig.forStoredConfig( modifier.newStoredConfiguration() );

        final Map<PwmDomainUtil.DomainModifyCategory, Set<DomainID>> categorizations = PwmDomainUtil.categorizeDomainModifications( modifiedConfig, initialConfig );

        Assert.assertEquals( 2, categorizations.get( PwmDomainUtil.DomainModifyCategory.unchanged ).size() );
        Assert.assertEquals( 0, categorizations.get( PwmDomainUtil.DomainModifyCategory.removed ).size() );
        Assert.assertEquals( 0, categorizations.get( PwmDomainUtil.DomainModifyCategory.created ).size() );
        Assert.assertEquals( 1, categorizations.get( PwmDomainUtil.DomainModifyCategory.modified ).size() );
    }

    @NotNull
    private static Map<PwmDomainUtil.DomainModifyCategory, Set<DomainID>> doSimpleCategorizationWithNewDomainList( final List<String> newDomainList )
            throws PwmUnrecoverableException
    {
        final AppConfig initialConfig = initialAppConfig();
        final StoredConfigurationModifier modifier = StoredConfigurationModifier.newModifier( initialConfig.getStoredConfiguration() );
        setDomainList( modifier, newDomainList );

        final AppConfig modifiedConfig = AppConfig.forStoredConfig( modifier.newStoredConfiguration() );

        return PwmDomainUtil.categorizeDomainModifications( modifiedConfig, initialConfig );
    }

    private static AppConfig initialAppConfig()
            throws PwmUnrecoverableException
    {
        final StoredConfigurationModifier modifier = StoredConfigurationFactory.newModifiableConfig();
        setDomainList( modifier, INITIAL_DOMAIN_LIST );
        return AppConfig.forStoredConfig( modifier.newStoredConfiguration() );
    }

    private static void setDomainList( final StoredConfigurationModifier modifier, final List<String> domainList )
            throws PwmUnrecoverableException
    {
        modifier.writeSetting( StoredConfigKey.forSetting( PwmSetting.DOMAIN_LIST, null, DomainID.systemId() ),
                StringArrayValue.create( domainList ), null );

    }


}
