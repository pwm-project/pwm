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
 * <p>The default is {@link #both}, which preserves PWM's long-standing behavior.  Deployments
 * against a strict authorization server select {@link #basic} or {@link #post} to match how the
 * client is registered.</p>
 */
public enum OAuthClientAuthMethod
{
    /** Send an HTTP Basic {@code Authorization} header only.  Equivalent to OIDC {@code client_secret_basic}. */
    basic,

    /** Send {@code client_id}/{@code client_secret} form parameters only.  Equivalent to OIDC {@code client_secret_post}. */
    post,

    /** Send both, PWM's legacy behavior. */
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
}
