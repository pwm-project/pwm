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

/**
 * How client credentials are presented to the authorization server's token endpoint.
 *
 * <p>RFC 6749 section 2.3.1 states that a client MUST NOT use more than one authentication method
 * per request.  Some authorization servers, PingFederate among them, enforce that rule and reject a
 * token request that carries an HTTP Basic <code>Authorization</code> header and
 * <code>client_id</code>/<code>client_secret</code> form parameters at the same time.  Others,
 * notably NetIQ OSP, have historically been sent both by PWM.</p>
 *
 * <p>The default is {@link #post}, which sends the credentials as form parameters only and is
 * accepted by both strict and permissive authorization servers.  Deployments whose client is
 * registered for {@code client_secret_basic} select {@link #basic}; {@link #both} remains
 * available for servers, such as NetIQ OSP, that expect PWM's legacy behavior.</p>
 *
 * <p>The same setting governs how the access token is presented on the profile/userinfo request.
 * {@link #basic} and {@link #post} send only an {@code Authorization: Bearer} header.  RFC 6750
 * section 2 allows only one method per request, section 2.1 requires servers to accept the header
 * (a form-body token is optional), and OIDC Core section 5.3.1 directs clients to the header, so
 * the {@link #post} default does not put the token in the body.  {@link #both} also sends an
 * {@code access_token} form parameter, preserving PWM's legacy behavior for NetIQ OSP.  Userinfo
 * never falls back to HTTP Basic client credentials; those still apply only to the token
 * endpoint.</p>
 */
public enum OAuthClientAuthMethod
{
    /** Send an HTTP Basic {@code Authorization} header only.  Equivalent to OIDC {@code client_secret_basic}. */
    basic,

    /** Send {@code client_id}/{@code client_secret} form parameters only.  Equivalent to OIDC {@code client_secret_post}. */
    post,

    /** Send both, PWM's legacy behavior; required by NetIQ OSP. */
    both,;

    /**
     * Whether the HTTP Basic {@code Authorization} header is sent on token endpoint requests.
     */
    boolean sendCredentialsInHeader()
    {
        return this != post;
    }

    /**
     * Whether credentials are included as form parameters on the authorization-code exchange.
     */
    boolean sendCredentialsInBody()
    {
        return this != basic;
    }

    /**
     * Whether credentials <em>must</em> be included as form parameters because they are not being
     * sent in a header.  Used for the refresh grant, which historically relied on the header alone.
     */
    boolean requiresCredentialsInBody()
    {
        return this == post;
    }

    /**
     * Whether the access token is sent as an RFC 6750 {@code Authorization: Bearer} header on
     * profile/userinfo requests.  Every method sends the header.  A form-body token is optional
     * (RFC 6750 section 2.1) and OIDC Core section 5.3.1 directs clients to the header, so
     * {@link #post} must not omit it.
     */
    boolean sendAccessTokenInHeader()
    {
        return this == basic || this == post || this == both;
    }

    /**
     * Whether the access token is also sent as an {@code access_token} form parameter on
     * profile/userinfo requests.  RFC 6750 section 2 forbids more than one method per request,
     * which PingFederate enforces with a 400 response.  Only {@link #both} keeps the parameter,
     * for NetIQ OSP compatibility.
     */
    boolean sendAccessTokenInBody()
    {
        return this == both;
    }
}
