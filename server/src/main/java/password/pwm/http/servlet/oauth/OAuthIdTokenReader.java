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

import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.StringUtil;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads and validates the OpenID Connect <code>id_token</code> returned by an authorization
 * server's token endpoint, so that the user name can be taken from a token claim instead of
 * from a second call to a profile/userinfo web service.  Written against PingFederate but
 * applicable to any spec-conformant OIDC provider.
 *
 * <p>The token signature is deliberately not verified.  PWM receives the token directly from
 * the token endpoint over a server-to-server TLS connection, whose certificate may be pinned
 * via the OAuth server certificate setting.  OpenID Connect Core 1.0 section 3.1.3.7 item 6
 * explicitly permits TLS server validation in place of signature validation when the token is
 * obtained this way.  Because the token never passes through the browser, a caller that could
 * forge one would already have to control the token endpoint connection.</p>
 *
 * <p>The registered <code>aud</code>, <code>azp</code>, <code>exp</code> and <code>nbf</code>
 * claims are always validated.  The <code>iss</code> claim is not checked, because PWM does not
 * hold a configured issuer value to compare it against.</p>
 */
class OAuthIdTokenReader
{
    private static final String CLAIM_AUDIENCE = "aud";
    private static final String CLAIM_AUTHORIZED_PARTY = "azp";
    private static final String CLAIM_EXPIRATION = "exp";
    private static final String CLAIM_NOT_BEFORE = "nbf";

    private static final int JWS_SEGMENT_COUNT = 3;
    private static final int JWE_SEGMENT_COUNT = 5;

    private OAuthIdTokenReader()
    {
    }

    /**
     * Decodes the payload of the supplied <code>id_token</code> and validates its registered
     * claims.  Returns the payload as a JSON document, suitable for reading individual claims
     * with {@link OAuthMachine#readAttributeFromBodyMap(String, String)}.
     *
     * @param idToken             the raw compact-serialization JWT from the token endpoint
     * @param expectedAudience    the configured OAuth client id, which must appear in <code>aud</code>
     * @param maxClockSkewSeconds tolerance applied to the <code>exp</code> and <code>nbf</code> checks
     * @return the decoded, validated payload as a JSON document
     * @throws PwmUnrecoverableException if the token is malformed, expired, or issued for another audience
     */
    static String readValidatedPayload(
            final String idToken,
            final String expectedAudience,
            final long maxClockSkewSeconds
    )
            throws PwmUnrecoverableException
    {
        if ( StringUtil.isEmpty( idToken ) )
        {
            throw newError( "oauth server response did not include an id_token; verify the 'openid' scope is being"
                    + " requested and that the oauth client is configured to issue an id_token" );
        }

        if ( StringUtil.isEmpty( expectedAudience ) )
        {
            throw newError( "unable to validate id_token audience because no oauth client id is configured" );
        }

        final String payloadJson = decodePayload( idToken );

        final Map<String, Object> claims = parseClaims( payloadJson );

        validateAudience( claims, expectedAudience );
        validateTimestamps( claims, maxClockSkewSeconds );

        return payloadJson;
    }

    private static String decodePayload( final String idToken )
            throws PwmUnrecoverableException
    {
        final String[] segments = idToken.split( "\\.", -1 );

        if ( segments.length == JWE_SEGMENT_COUNT )
        {
            throw newError( "oauth server returned an encrypted (JWE) id_token, which is not supported;"
                    + " configure the oauth client to issue a signed (JWS) id_token" );
        }

        if ( segments.length != JWS_SEGMENT_COUNT )
        {
            throw newError( "oauth server returned an id_token that is not a well-formed JWT; expected "
                    + JWS_SEGMENT_COUNT + " segments but found " + segments.length );
        }

        try
        {
            final byte[] decoded = Base64.getUrlDecoder().decode( segments[ 1 ] );
            return new String( decoded, StandardCharsets.UTF_8 );
        }
        catch ( final IllegalArgumentException e )
        {
            throw newError( "id_token payload is not valid base64url data: " + e.getMessage() );
        }
    }

    private static Map<String, Object> parseClaims( final String payloadJson )
            throws PwmUnrecoverableException
    {
        final Map<String, Object> claims;
        try
        {
            claims = JsonUtil.deserializeMap( payloadJson );
        }
        catch ( final Exception e )
        {
            throw newError( "id_token payload is not valid json: " + e.getMessage() );
        }

        if ( claims == null || claims.isEmpty() )
        {
            throw newError( "id_token payload does not contain any claims" );
        }

        return claims;
    }

    private static void validateAudience(
            final Map<String, Object> claims,
            final String expectedAudience
    )
            throws PwmUnrecoverableException
    {
        final Object audienceClaim = claims.get( CLAIM_AUDIENCE );
        if ( audienceClaim == null )
        {
            throw newError( "id_token is missing the required 'aud' claim" );
        }

        final List<String> audiences = asStringList( audienceClaim );
        if ( !audiences.contains( expectedAudience ) )
        {
            throw newError( "id_token 'aud' claim " + audiences + " does not contain the configured oauth client id" );
        }

        if ( audiences.size() > 1 )
        {
            final Object authorizedParty = claims.get( CLAIM_AUTHORIZED_PARTY );
            if ( authorizedParty == null )
            {
                throw newError( "id_token contains multiple 'aud' values but no 'azp' claim" );
            }

            if ( !expectedAudience.equals( authorizedParty.toString() ) )
            {
                throw newError( "id_token 'azp' claim does not match the configured oauth client id" );
            }
        }
    }

    private static void validateTimestamps(
            final Map<String, Object> claims,
            final long maxClockSkewSeconds
    )
            throws PwmUnrecoverableException
    {
        final Instant now = Instant.now();

        final Optional<Long> expiration = readNumericClaim( claims, CLAIM_EXPIRATION );
        if ( !expiration.isPresent() )
        {
            throw newError( "id_token is missing the required 'exp' claim" );
        }

        final Instant expirationInstant = Instant.ofEpochSecond( expiration.get() );
        if ( now.isAfter( expirationInstant.plusSeconds( maxClockSkewSeconds ) ) )
        {
            throw newError( "id_token expired at " + JavaHelper.toIsoDate( expirationInstant ) );
        }

        final Optional<Long> notBefore = readNumericClaim( claims, CLAIM_NOT_BEFORE );
        if ( notBefore.isPresent() )
        {
            final Instant notBeforeInstant = Instant.ofEpochSecond( notBefore.get() );
            if ( now.isBefore( notBeforeInstant.minusSeconds( maxClockSkewSeconds ) ) )
            {
                throw newError( "id_token is not valid until " + JavaHelper.toIsoDate( notBeforeInstant ) );
            }
        }
    }

    private static Optional<Long> readNumericClaim( final Map<String, Object> claims, final String claimName )
    {
        final Object value = claims.get( claimName );

        if ( value instanceof Number )
        {
            return Optional.of( ( ( Number ) value ).longValue() );
        }

        if ( value instanceof String )
        {
            try
            {
                return Optional.of( Long.parseLong( ( ( String ) value ).trim() ) );
            }
            catch ( final NumberFormatException e )
            {
                return Optional.empty();
            }
        }

        return Optional.empty();
    }

    private static List<String> asStringList( final Object value )
    {
        if ( value instanceof Collection )
        {
            final List<String> values = new ArrayList<>();
            for ( final Object item : ( Collection<?> ) value )
            {
                if ( item != null )
                {
                    values.add( item.toString() );
                }
            }
            return values;
        }

        return Collections.singletonList( value.toString() );
    }

    private static PwmUnrecoverableException newError( final String message )
    {
        return new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_OAUTH_ERROR, message ) );
    }
}
