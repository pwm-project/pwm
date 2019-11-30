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

package password.pwm.svc.wordlist;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.stored.StoredConfigurationFactory;
import password.pwm.util.localdb.TestHelper;

import java.net.URL;

public class WordlistServiceTest
{

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void testTypicalWordlist()
            throws Exception
    {
        final WordlistService wordlistService = makeWordlistService( null );

        Assert.assertTrue( wordlistService.containsWord( "password-test" ) );
        Assert.assertFalse( wordlistService.containsWord( "password-false-test" ) );

        Assert.assertFalse( wordlistService.containsWord( "0" ) );
        Assert.assertFalse( wordlistService.containsWord( "01" ) );
        Assert.assertFalse( wordlistService.containsWord( "012" ) );
        Assert.assertFalse( wordlistService.containsWord( "0123" ) );
        Assert.assertFalse( wordlistService.containsWord( "01234" ) );
        Assert.assertFalse( wordlistService.containsWord( "012345" ) );
        Assert.assertTrue( wordlistService.containsWord( "0123456" ) );
        Assert.assertTrue( wordlistService.containsWord( "01234567" ) );
        Assert.assertTrue( wordlistService.containsWord( "012345678" ) );
        Assert.assertTrue( wordlistService.containsWord( "0123456789" ) );

        Assert.assertTrue( wordlistService.containsWord( "abcdefghijklmnopqrstuvwxyz" ) );
        Assert.assertTrue( wordlistService.containsWord( "AbcdefghijklmnopqrstuvwxyZ" ) );
        Assert.assertTrue( wordlistService.containsWord( "ABCDEFGHIJKLMNOPQRSTUVWXYZ" ) );

        // make
        Assert.assertTrue( wordlistService.containsWord( "md5-Password-Test" ) );
        Assert.assertFalse( wordlistService.containsWord( "md5-Password-Test-false" ) );
        Assert.assertTrue( wordlistService.containsWord( "md5-Password-Test-Reverse" ) );
        Assert.assertFalse( wordlistService.containsWord( "md5-Password-Test-Reverse-false" ) );
        Assert.assertTrue( wordlistService.containsWord( "sha1-Password-Test" ) );
        Assert.assertFalse( wordlistService.containsWord( "sha1-Password-Test-false" ) );
        Assert.assertTrue( wordlistService.containsWord( "sha1-Password-Test-Reverse" ) );
        Assert.assertFalse( wordlistService.containsWord( "sha1-Password-Test-Reverse-false" ) );
        Assert.assertTrue( wordlistService.containsWord( "sha256-Password-Test" ) );
        Assert.assertFalse( wordlistService.containsWord( "sha256-Password-Test-false" ) );
        Assert.assertTrue( wordlistService.containsWord( "sha256-Password-Test-Reverse" ) );
        Assert.assertFalse( wordlistService.containsWord( "sha256-Password-Test-Reverse-false" ) );
        Assert.assertTrue( wordlistService.containsWord( "sha512-Password-Test" ) );
        Assert.assertFalse( wordlistService.containsWord( "sha512-Password-Test-false" ) );
        Assert.assertTrue( wordlistService.containsWord( "sha512-Password-Test-Reverse" ) );
        Assert.assertFalse( wordlistService.containsWord( "sha512-Password-Test-Reverse-false" ) );

        // make sure single line comment isn't imported as workd
        Assert.assertFalse( wordlistService.containsWord( "!#comment!" ) );

        // make sure raw hashes aren't imported
        Assert.assertFalse( wordlistService.containsWord( "6D3A08CFF825AA07DCEC94801D9B7647" ) );
        Assert.assertFalse( wordlistService.containsWord( "BB231388547E063CCFFDD0282C37184A" ) );
        Assert.assertFalse( wordlistService.containsWord( "4B0ABDCB3430D57D0581A9D617B8ABCD3202D992" ) );
        Assert.assertFalse( wordlistService.containsWord( "056DA0B59D7C1622B8F60726DE8E25BC771D5E89" ) );
        Assert.assertFalse( wordlistService.containsWord( "970FE1C94E532597BB8EF9BE7F397C7C8052127B6C21F443608322B3EF01176C" ) );
        Assert.assertFalse( wordlistService.containsWord( "A96A0E4DB996D5A4B35558BDDB54BBF389FF853E349700F6FE9F96DD4441BD48" ) );
        Assert.assertFalse( wordlistService.containsWord(
                "F910C640F9E720EE4E9D785101CED049B9C9A385D610E46BF8026FA9D3BC169637C0538A8361ADCB5C641079604F7C9CBAD6ED07F646D85DF83BB69E713739C4" ) );
        Assert.assertFalse( wordlistService.containsWord(
                "3FA6580F55AA7F5337031895239E6C2B022A9A87A7FFC72041F8E080DC9F19CFA43EE862471829E9B556A4D9AF201476E508E6A312204641F604DFBE4240907F" ) );
        Assert.assertFalse( wordlistService.containsWord( "md5:6D3A08CFF825AA07DCEC94801D9B7647" ) );
        Assert.assertFalse( wordlistService.containsWord( "BB231388547E063CCFFDD0282C37184A:md5" ) );
        Assert.assertFalse( wordlistService.containsWord( "sha1:4B0ABDCB3430D57D0581A9D617B8ABCD3202D992" ) );
        Assert.assertFalse( wordlistService.containsWord( "056DA0B59D7C1622B8F60726DE8E25BC771D5E89:sha1" ) );
        Assert.assertFalse( wordlistService.containsWord( "sha256:970FE1C94E532597BB8EF9BE7F397C7C8052127B6C21F443608322B3EF01176C" ) );
        Assert.assertFalse( wordlistService.containsWord( "A96A0E4DB996D5A4B35558BDDB54BBF389FF853E349700F6FE9F96DD4441BD48:sha256" ) );
        Assert.assertFalse( wordlistService.containsWord(
                "sha512:F910C640F9E720EE4E9D785101CED049B9C9A385D610E46BF8026FA9D3BC169637C0538A8361ADCB5C641079604F7C9CBAD6ED07F646D85DF83BB69E713739C4" ) );
        Assert.assertFalse( wordlistService.containsWord(
                "3FA6580F55AA7F5337031895239E6C2B022A9A87A7FFC72041F8E080DC9F19CFA43EE862471829E9B556A4D9AF201476E508E6A312204641F604DFBE4240907F:Sha512" ) );

    }

    @Test
    public void testCaseSensitiveWordlist()
            throws Exception
    {
        final Configuration configuration = Mockito.spy( new Configuration( StoredConfigurationFactory.newConfig() ) );
        Mockito.when( configuration.readSettingAsBoolean( PwmSetting.WORDLIST_CASE_SENSITIVE ) ).thenReturn( true );
        final WordlistService wordlistService = makeWordlistService( configuration );

        Assert.assertTrue( wordlistService.containsWord( "password-test" ) );
        Assert.assertFalse( wordlistService.containsWord( "PASSWORD-TEST" ) );

        Assert.assertTrue( wordlistService.containsWord( "abcdefghijklmnopqrstuvwxyz" ) );
        Assert.assertFalse( wordlistService.containsWord( "AbcdefghijklmnopqrstuvwxyZ" ) );
        Assert.assertTrue( wordlistService.containsWord( "ABCDEFGHIJKLMNOPQRSTUVWXYZ" ) );
    }

    @Test
    public void testChunkedWords()
            throws Exception
    {
        final Configuration configuration = Mockito.spy( new Configuration( StoredConfigurationFactory.newConfig() ) );
        Mockito.when( configuration.readSettingAsLong( PwmSetting.PASSWORD_WORDLIST_WORDSIZE ) ).thenReturn( 4L );
        final WordlistService wordlistService = makeWordlistService( configuration );

        Assert.assertTrue( wordlistService.containsWord( "abcdefghijklmnopqrstuvwxyz" ) );
        Assert.assertTrue( wordlistService.containsWord( "ABCDEFGHIJKLMNOPQRSTUVWXYZ" ) );

        Assert.assertFalse( wordlistService.containsWord( "A" ) );
        Assert.assertFalse( wordlistService.containsWord( "AB" ) );
        Assert.assertFalse( wordlistService.containsWord( "ABC" ) );
        Assert.assertTrue( wordlistService.containsWord( "ABCD" ) );
        Assert.assertTrue( wordlistService.containsWord( "ABCDE" ) );
        Assert.assertTrue( wordlistService.containsWord( "ABCde" ) );
    }

    private WordlistService makeWordlistService( final Configuration inputConfiguration )
            throws Exception
    {

        final Configuration configuration = inputConfiguration == null
                ? Mockito.spy( new Configuration( StoredConfigurationFactory.newConfig() ) )
                : inputConfiguration;
        Mockito.when( configuration.readAppProperty( AppProperty.WORDLIST_TEST_MODE ) ).thenReturn( "true" );
        Mockito.when( configuration.readSettingAsString( PwmSetting.WORDLIST_FILENAME ) ).thenReturn( "" );

        final URL url = this.getClass().getResource( "test-wordlist.zip" );
        Mockito.when( configuration.readAppProperty( AppProperty.WORDLIST_BUILTIN_PATH ) ).thenReturn( url.toString() );

        final PwmApplication pwmApplication = TestHelper.makeTestPwmApplication( temporaryFolder.newFolder(), configuration );
        return pwmApplication.getWordlistService();
    }
}
