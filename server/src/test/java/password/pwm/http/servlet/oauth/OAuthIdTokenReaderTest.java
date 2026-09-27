/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2023 The PWM Project
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

package password.pwm.http.servlet.oauth;

import org.junit.Assert;
import org.junit.Test;
import password.pwm.error.PwmUnrecoverableException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

public class OAuthIdTokenReaderTest
{
    private static final String CLIENT_ID = "pwm-client";
    private static final long SKEW_SECONDS = 60;

    /**
     * Header of a token as PingFederate emits it.  The reader does not inspect the header, but a
     * realistic value keeps the fixtures honest.
     */
    private static final String HEADER = "{\"alg\":\"RS256\",\"kid\":\"pf1\"}";

    @Test
    public void readsPayloadFromWellFormedToken()
            throws PwmUnrecoverableException
    {
        final String payload = "{\"sub\":\"jdoe\",\"aud\":\"" + CLIENT_ID + "\",\"exp\":" + futureEpoch() + "}";

        final String result = OAuthIdTokenReader.readValidatedPayload( token( payload ), CLIENT_ID, SKEW_SECONDS );

        Assert.assertEquals( payload, result );
    }

    /**
     * JWT segments use the base64url alphabet (RFC 4648 section 5), not standard base64.  A decoder
     * that assumes the standard alphabet mis-handles any payload whose encoding contains '-' or '_'.
     * The claim value below is chosen so the encoded segment contains both.
     */
    @Test
    public void decodesBase64UrlAlphabet()
            throws PwmUnrecoverableException
    {
        final String payload = "{\"sub\":\"?>?>\",\"aud\":\"" + CLIENT_ID + "\",\"exp\":" + futureEpoch() + "}";
        final String encodedPayload = encode( payload );

        Assert.assertTrue( "fixture no longer exercises the base64url alphabet", encodedPayload.contains( "-" ) );
        Assert.assertTrue( "fixture no longer exercises the base64url alphabet", encodedPayload.contains( "_" ) );

        final String result = OAuthIdTokenReader.readValidatedPayload( token( payload ), CLIENT_ID, SKEW_SECONDS );

        Assert.assertEquals( payload, result );
    }

    @Test
    public void acceptsAudienceArrayWithMatchingAzp()
            throws PwmUnrecoverableException
    {
        final String payload = "{\"sub\":\"jdoe\",\"aud\":[\"" + CLIENT_ID + "\",\"other-client\"],"
                + "\"azp\":\"" + CLIENT_ID + "\",\"exp\":" + futureEpoch() + "}";

        final String result = OAuthIdTokenReader.readValidatedPayload( token( payload ), CLIENT_ID, SKEW_SECONDS );

        Assert.assertEquals( payload, result );
    }

    @Test
    public void acceptsSingleEntryAudienceArrayWithoutAzp()
            throws PwmUnrecoverableException
    {
        final String payload = "{\"sub\":\"jdoe\",\"aud\":[\"" + CLIENT_ID + "\"],\"exp\":" + futureEpoch() + "}";

        final String result = OAuthIdTokenReader.readValidatedPayload( token( payload ), CLIENT_ID, SKEW_SECONDS );

        Assert.assertEquals( payload, result );
    }

    @Test
    public void acceptsTokenExpiredWithinClockSkew()
            throws PwmUnrecoverableException
    {
        final long expiredButWithinSkew = Instant.now().getEpochSecond() - ( SKEW_SECONDS / 2 );
        final String payload = "{\"sub\":\"jdoe\",\"aud\":\"" + CLIENT_ID + "\",\"exp\":" + expiredButWithinSkew + "}";

        final String result = OAuthIdTokenReader.readValidatedPayload( token( payload ), CLIENT_ID, SKEW_SECONDS );

        Assert.assertEquals( payload, result );
    }

    @Test
    public void acceptsNotBeforeWithinClockSkew()
            throws PwmUnrecoverableException
    {
        final long notYetButWithinSkew = Instant.now().getEpochSecond() + ( SKEW_SECONDS / 2 );
        final String payload = "{\"sub\":\"jdoe\",\"aud\":\"" + CLIENT_ID + "\",\"nbf\":" + notYetButWithinSkew
                + ",\"exp\":" + futureEpoch() + "}";

        final String result = OAuthIdTokenReader.readValidatedPayload( token( payload ), CLIENT_ID, SKEW_SECONDS );

        Assert.assertEquals( payload, result );
    }

    @Test
    public void rejectsEmptyToken()
    {
        assertRejected( null, "did not include an id_token" );
        assertRejected( "", "did not include an id_token" );
    }

    @Test
    public void rejectsEncryptedToken()
    {
        assertRejected( "a.b.c.d.e", "encrypted (JWE) id_token" );
    }

    @Test
    public void rejectsMalformedToken()
    {
        assertRejected( "header.payload", "not a well-formed JWT" );
    }

    @Test
    public void rejectsNonBase64Payload()
    {
        assertRejected( encode( HEADER ) + ".!!!not-base64!!!.signature", "not valid base64url data" );
    }

    @Test
    public void rejectsNonJsonPayload()
    {
        assertRejected( token( "this is not json" ), "not valid json" );
    }

    @Test
    public void rejectsTokenIssuedForAnotherAudience()
    {
        final String payload = "{\"sub\":\"jdoe\",\"aud\":\"some-other-client\",\"exp\":" + futureEpoch() + "}";

        assertRejected( token( payload ), "does not contain the configured oauth client id" );
    }

    @Test
    public void rejectsMissingAudience()
    {
        final String payload = "{\"sub\":\"jdoe\",\"exp\":" + futureEpoch() + "}";

        assertRejected( token( payload ), "missing the required 'aud' claim" );
    }

    @Test
    public void rejectsMultipleAudiencesWithoutAzp()
    {
        final String payload = "{\"sub\":\"jdoe\",\"aud\":[\"" + CLIENT_ID + "\",\"other-client\"],"
                + "\"exp\":" + futureEpoch() + "}";

        assertRejected( token( payload ), "multiple 'aud' values but no 'azp' claim" );
    }

    @Test
    public void rejectsMultipleAudiencesWithMismatchedAzp()
    {
        final String payload = "{\"sub\":\"jdoe\",\"aud\":[\"" + CLIENT_ID + "\",\"other-client\"],"
                + "\"azp\":\"other-client\",\"exp\":" + futureEpoch() + "}";

        assertRejected( token( payload ), "'azp' claim does not match" );
    }

    @Test
    public void rejectsExpiredToken()
    {
        final long wellExpired = Instant.now().getEpochSecond() - ( SKEW_SECONDS * 10 );
        final String payload = "{\"sub\":\"jdoe\",\"aud\":\"" + CLIENT_ID + "\",\"exp\":" + wellExpired + "}";

        assertRejected( token( payload ), "id_token expired at" );
    }

    @Test
    public void rejectsMissingExpiration()
    {
        final String payload = "{\"sub\":\"jdoe\",\"aud\":\"" + CLIENT_ID + "\"}";

        assertRejected( token( payload ), "missing the required 'exp' claim" );
    }

    @Test
    public void rejectsTokenNotYetValid()
    {
        final long wellInFuture = Instant.now().getEpochSecond() + ( SKEW_SECONDS * 10 );
        final String payload = "{\"sub\":\"jdoe\",\"aud\":\"" + CLIENT_ID + "\",\"nbf\":" + wellInFuture
                + ",\"exp\":" + futureEpoch() + "}";

        assertRejected( token( payload ), "not valid until" );
    }

    @Test
    public void rejectsMissingClientId()
    {
        final String payload = "{\"sub\":\"jdoe\",\"aud\":\"" + CLIENT_ID + "\",\"exp\":" + futureEpoch() + "}";

        try
        {
            OAuthIdTokenReader.readValidatedPayload( token( payload ), "", SKEW_SECONDS );
            Assert.fail( "expected PwmUnrecoverableException" );
        }
        catch ( final PwmUnrecoverableException e )
        {
            Assert.assertTrue( e.getMessage().contains( "no oauth client id is configured" ) );
        }
    }

    private static void assertRejected( final String idToken, final String expectedFragment )
    {
        try
        {
            OAuthIdTokenReader.readValidatedPayload( idToken, CLIENT_ID, SKEW_SECONDS );
            Assert.fail( "expected PwmUnrecoverableException containing '" + expectedFragment + "'" );
        }
        catch ( final PwmUnrecoverableException e )
        {
            Assert.assertTrue(
                    "expected error to contain '" + expectedFragment + "' but was: " + e.getMessage(),
                    e.getMessage().contains( expectedFragment ) );
        }
    }

    private static String token( final String payload )
    {
        return encode( HEADER ) + "." + encode( payload ) + ".fake-signature";
    }

    private static String encode( final String input )
    {
        return Base64.getUrlEncoder().withoutPadding().encodeToString( input.getBytes( StandardCharsets.UTF_8 ) );
    }

    private static long futureEpoch()
    {
        return Instant.now().getEpochSecond() + 3600;
    }
}
