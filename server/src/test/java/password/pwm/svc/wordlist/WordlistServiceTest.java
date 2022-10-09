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

package password.pwm.svc.wordlist;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.config.AppConfig;
import password.pwm.config.PwmSetting;
import password.pwm.config.stored.StoredConfigurationFactory;
import password.pwm.util.java.FileSystemUtility;
import password.pwm.util.localdb.TestHelper;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;

public class WordlistServiceTest
{

    @TempDir
    public Path temporaryFolder;

    @Test
    public void testTypicalWordlist()
            throws Exception
    {
        final WordlistService wordlistService = makeWordlistService( null );

        Assertions.assertTrue( wordlistService.containsWord( "password-test" ) );
        Assertions.assertFalse( wordlistService.containsWord( "password-false-test" ) );

        Assertions.assertFalse( wordlistService.containsWord( "0" ) );
        Assertions.assertFalse( wordlistService.containsWord( "01" ) );
        Assertions.assertFalse( wordlistService.containsWord( "012" ) );
        Assertions.assertFalse( wordlistService.containsWord( "0123" ) );
        Assertions.assertFalse( wordlistService.containsWord( "01234" ) );
        Assertions.assertFalse( wordlistService.containsWord( "012345" ) );
        Assertions.assertTrue( wordlistService.containsWord( "0123456" ) );
        Assertions.assertTrue( wordlistService.containsWord( "01234567" ) );
        Assertions.assertTrue( wordlistService.containsWord( "012345678" ) );
        Assertions.assertTrue( wordlistService.containsWord( "0123456789" ) );

        Assertions.assertTrue( wordlistService.containsWord( "abcdefghijklmnopqrstuvwxyz" ) );
        Assertions.assertTrue( wordlistService.containsWord( "AbcdefghijklmnopqrstuvwxyZ" ) );
        Assertions.assertTrue( wordlistService.containsWord( "ABCDEFGHIJKLMNOPQRSTUVWXYZ" ) );

        // make
        Assertions.assertTrue( wordlistService.containsWord( "md5-Password-Test" ) );
        Assertions.assertFalse( wordlistService.containsWord( "md5-Password-Test-false" ) );
        Assertions.assertTrue( wordlistService.containsWord( "md5-Password-Test-Reverse" ) );
        Assertions.assertFalse( wordlistService.containsWord( "md5-Password-Test-Reverse-false" ) );
        Assertions.assertTrue( wordlistService.containsWord( "sha1-Password-Test" ) );
        Assertions.assertFalse( wordlistService.containsWord( "sha1-Password-Test-false" ) );
        Assertions.assertTrue( wordlistService.containsWord( "sha1-Password-Test-Reverse" ) );
        Assertions.assertFalse( wordlistService.containsWord( "sha1-Password-Test-Reverse-false" ) );
        Assertions.assertTrue( wordlistService.containsWord( "sha256-Password-Test" ) );
        Assertions.assertFalse( wordlistService.containsWord( "sha256-Password-Test-false" ) );
        Assertions.assertTrue( wordlistService.containsWord( "sha256-Password-Test-Reverse" ) );
        Assertions.assertFalse( wordlistService.containsWord( "sha256-Password-Test-Reverse-false" ) );
        Assertions.assertTrue( wordlistService.containsWord( "sha512-Password-Test" ) );
        Assertions.assertFalse( wordlistService.containsWord( "sha512-Password-Test-false" ) );
        Assertions.assertTrue( wordlistService.containsWord( "sha512-Password-Test-Reverse" ) );
        Assertions.assertFalse( wordlistService.containsWord( "sha512-Password-Test-Reverse-false" ) );

        // make sure single line comment isn't imported as workd
        Assertions.assertFalse( wordlistService.containsWord( "!#comment!" ) );

        // make sure raw hashes aren't imported
        Assertions.assertFalse( wordlistService.containsWord( "6D3A08CFF825AA07DCEC94801D9B7647" ) );
        Assertions.assertFalse( wordlistService.containsWord( "BB231388547E063CCFFDD0282C37184A" ) );
        Assertions.assertFalse( wordlistService.containsWord( "4B0ABDCB3430D57D0581A9D617B8ABCD3202D992" ) );
        Assertions.assertFalse( wordlistService.containsWord( "056DA0B59D7C1622B8F60726DE8E25BC771D5E89" ) );
        Assertions.assertFalse( wordlistService.containsWord( "970FE1C94E532597BB8EF9BE7F397C7C8052127B6C21F443608322B3EF01176C" ) );
        Assertions.assertFalse( wordlistService.containsWord( "A96A0E4DB996D5A4B35558BDDB54BBF389FF853E349700F6FE9F96DD4441BD48" ) );
        Assertions.assertFalse( wordlistService.containsWord(
                "F910C640F9E720EE4E9D785101CED049B9C9A385D610E46BF8026FA9D3BC169637C0538A8361ADCB5C641079604F7C9CBAD6ED07F646D85DF83BB69E713739C4" ) );
        Assertions.assertFalse( wordlistService.containsWord(
                "3FA6580F55AA7F5337031895239E6C2B022A9A87A7FFC72041F8E080DC9F19CFA43EE862471829E9B556A4D9AF201476E508E6A312204641F604DFBE4240907F" ) );
        Assertions.assertFalse( wordlistService.containsWord( "md5:6D3A08CFF825AA07DCEC94801D9B7647" ) );
        Assertions.assertFalse( wordlistService.containsWord( "BB231388547E063CCFFDD0282C37184A:md5" ) );
        Assertions.assertFalse( wordlistService.containsWord( "sha1:4B0ABDCB3430D57D0581A9D617B8ABCD3202D992" ) );
        Assertions.assertFalse( wordlistService.containsWord( "056DA0B59D7C1622B8F60726DE8E25BC771D5E89:sha1" ) );
        Assertions.assertFalse( wordlistService.containsWord( "sha256:970FE1C94E532597BB8EF9BE7F397C7C8052127B6C21F443608322B3EF01176C" ) );
        Assertions.assertFalse( wordlistService.containsWord( "A96A0E4DB996D5A4B35558BDDB54BBF389FF853E349700F6FE9F96DD4441BD48:sha256" ) );
        Assertions.assertFalse( wordlistService.containsWord(
                "sha512:F910C640F9E720EE4E9D785101CED049B9C9A385D610E46BF8026FA9D3BC169637C0538A8361ADCB5C641079604F7C9CBAD6ED07F646D85DF83BB69E713739C4" ) );
        Assertions.assertFalse( wordlistService.containsWord(
                "3FA6580F55AA7F5337031895239E6C2B022A9A87A7FFC72041F8E080DC9F19CFA43EE862471829E9B556A4D9AF201476E508E6A312204641F604DFBE4240907F:Sha512" ) );

    }

    @Test
    public void testCaseSensitiveWordlist()
            throws Exception
    {
        final AppConfig appConfig = Mockito.spy( AppConfig.forStoredConfig( StoredConfigurationFactory.newConfig() ) );
        Mockito.when( appConfig.readSettingAsBoolean( PwmSetting.WORDLIST_CASE_SENSITIVE ) ).thenReturn( true );
        final WordlistService wordlistService = makeWordlistService( appConfig );

        Assertions.assertTrue( wordlistService.containsWord( "password-test" ) );
        Assertions.assertFalse( wordlistService.containsWord( "PASSWORD-TEST" ) );

        Assertions.assertTrue( wordlistService.containsWord( "abcdefghijklmnopqrstuvwxyz" ) );
        Assertions.assertFalse( wordlistService.containsWord( "AbcdefghijklmnopqrstuvwxyZ" ) );
        Assertions.assertTrue( wordlistService.containsWord( "ABCDEFGHIJKLMNOPQRSTUVWXYZ" ) );
    }

    @Test
    public void testChunkedWords()
            throws Exception
    {
        final AppConfig appConfig = Mockito.spy( AppConfig.forStoredConfig( StoredConfigurationFactory.newConfig() ) );
        Mockito.when( appConfig.readSettingAsLong( PwmSetting.PASSWORD_WORDLIST_WORDSIZE ) ).thenReturn( 4L );
        final WordlistService wordlistService = makeWordlistService( appConfig );

        Assertions.assertTrue( wordlistService.containsWord( "abcdefghijklmnopqrstuvwxyz" ) );
        Assertions.assertTrue( wordlistService.containsWord( "ABCDEFGHIJKLMNOPQRSTUVWXYZ" ) );

        Assertions.assertFalse( wordlistService.containsWord( "A" ) );
        Assertions.assertFalse( wordlistService.containsWord( "AB" ) );
        Assertions.assertFalse( wordlistService.containsWord( "ABC" ) );
        Assertions.assertTrue( wordlistService.containsWord( "ABCD" ) );
        Assertions.assertTrue( wordlistService.containsWord( "ABCDE" ) );
        Assertions.assertTrue( wordlistService.containsWord( "ABCde" ) );
    }

    private WordlistService makeWordlistService( final AppConfig inputDomainConfig )
            throws Exception
    {

        final AppConfig appConfig = inputDomainConfig == null
                ? Mockito.spy( AppConfig.forStoredConfig( StoredConfigurationFactory.newConfig() ) )
                : inputDomainConfig;
        Mockito.when( appConfig.readAppProperty( AppProperty.WORDLIST_TEST_MODE ) ).thenReturn( "true" );
        Mockito.when( appConfig.readSettingAsString( PwmSetting.WORDLIST_FILENAME ) ).thenReturn( "" );

        final URL url = this.getClass().getResource( "test-wordlist.zip" );
        Mockito.when( appConfig.readAppProperty( AppProperty.WORDLIST_BUILTIN_PATH ) ).thenReturn( url.toString() );

        final File testFolder = FileSystemUtility.createDirectory( temporaryFolder, "test-makeWordlistService" );
        final PwmApplication pwmApplication = TestHelper.makeTestPwmApplication( testFolder, appConfig );
        return pwmApplication.getWordlistService();
    }
}
