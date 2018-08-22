/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package password.pwm.util;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.ArrayList;
import org.apache.http.Header;
import org.apache.http.NameValuePair;
import org.apache.http.client.CookieStore;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.cookie.Cookie;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.TrustAllStrategy;

public class FreeIpaUserAdd
{

    public static final int RESULT_ATTRIBUTES_PARSE = -1;
    public static final int RESULT_INSUFFICIENT_ATTRIBUTES = 0;
    public static final int RESULT_USER_CREATED = 1;
    public static final int RESULT_CREATE_FAILED = 2;
    public static final int RESULT_AUTH_SUCCESS = 11;
    public static final int RESULT_AUTH_EXPIRED = 12;
    public static final int RESULT_AUTH_INVALID = 13;
    public static final int RESULT_AUTH_DENIED = 14;
    public static final int RESULT_AUTH_UNKNOWN_ERROR = 15;

    private static final String IPA_REQUIRED_UID = "uid";
    private static final String IPA_REQUIRED_SN = "sn";
    private static final String IPA_REQUIRED_GIVENNAME = "givenname";

    private static final String IPA_REFERRAL_PAGE = "/ipa";
    private static final String IPA_SESSION_AUTH_PAGE = "/ipa/session/login_password";
    private static final String IPA_SESSION_JSON_PAGE = "/ipa/session/json";

    private static final String CREATE_USER_JSON_HEAD = "{\"method\":\"user_add\",\"params\":[[\"";
    private static final String CREATE_USER_JSON_MID = "\"],{";
    private static final String CREATE_USER_JSON_TAIL = " }],\"id\":0}";
    private static final String CREATE_USER_JSON_ATTRIB_FRONT = " \"";
    private static final String CREATE_USER_JSON_ATTRIB_MID = "\": \"";
    private static final String CREATE_USER_JSON_ATTRIB_END = "\",";

    private final String username;
    private final String password;
    private final String hostname;
    private String jsonRequest;
    private final CookieStore cookieStore;
    private final Map<String, String> createAttributes;
    private CloseableHttpClient httpClient = null;

    public FreeIpaUserAdd( final String username, final String password,
            final String hostname, final Map<String, String> createAttributes )
            throws KeyManagementException, NoSuchAlgorithmException,
            KeyStoreException
    {
        this.username = username;
        this.password = password;
        this.hostname = hostname;
        this.createAttributes = createAttributes;
        cookieStore = new BasicCookieStore();
        jsonRequest = "";

        try
        {
            httpClient = HttpClients.custom().setSSLContext(
                    new SSLContextBuilder().loadTrustMaterial( null,
                            TrustAllStrategy.INSTANCE ).build() ).
                    setSSLHostnameVerifier( NoopHostnameVerifier.INSTANCE ).
                    build();
            authUser();

        }
        catch ( KeyManagementException | KeyStoreException | NoSuchAlgorithmException e )
        {

        }
    }

    private String getURL( final String urlSuffix )
    {
        return ( "https://" + hostname + urlSuffix );
    }

    private int makeJSON()
    {
        boolean hasUID = false;
        boolean hasSN = false;
        boolean hasGivenName = false;
        String jsonTempFront = "";
        String jsonTempEnd = "";

        for ( Map.Entry<String, String> attribute
                : createAttributes.entrySet() )
        {
            final String key = attribute.getKey().toLowerCase();
            final String value = attribute.getValue();

            if ( key.equalsIgnoreCase( IPA_REQUIRED_UID ) )
            {
                hasUID = true;
                jsonTempFront = CREATE_USER_JSON_HEAD + value + CREATE_USER_JSON_MID;
            }
            else
            {
                if ( key.equalsIgnoreCase( IPA_REQUIRED_SN ) )
                {
                    hasSN = true;
                }
                if ( key.equalsIgnoreCase( IPA_REQUIRED_GIVENNAME ) )
                {
                    hasGivenName = true;
                }

                final String tempString = new StringBuffer().
                        append( jsonTempEnd ).append(
                        CREATE_USER_JSON_ATTRIB_FRONT ).append( key ).append(
                        CREATE_USER_JSON_ATTRIB_MID ).append( value ).append(
                        CREATE_USER_JSON_ATTRIB_END ).toString();
                jsonTempEnd = tempString;
            }
        }
        if ( hasUID && hasSN && hasGivenName )
        {
            if ( jsonTempEnd.length() > 0 )
            {
                if ( jsonTempEnd.charAt( jsonTempEnd.
                        length() - 1 ) == ',' )
                {
                    jsonRequest = jsonTempFront + jsonTempEnd.substring( 0,
                            jsonTempEnd.length() - 1 ) + CREATE_USER_JSON_TAIL;
                    return RESULT_ATTRIBUTES_PARSE;
                }
            }
        }
        return RESULT_INSUFFICIENT_ATTRIBUTES;
    }

    private int authUser()
    {
        final HttpPost authPost = new HttpPost( getURL( IPA_SESSION_AUTH_PAGE ) );
        final ArrayList<NameValuePair> params = new ArrayList();
        params.add( new BasicNameValuePair( "user", username ) );
        params.add( new BasicNameValuePair( "password", password ) );
        try
        {
            authPost.setEntity( new UrlEncodedFormEntity( params ) );
            authPost.setHeader( "referer", getURL( IPA_REFERRAL_PAGE ) );
            authPost.setHeader( "Content-Type",
                    "application/x-www-form-urlencoded" );
            authPost.setHeader( "Accept", "text/plain" );

            final HttpContext httpContext = new BasicHttpContext();
            httpContext.setAttribute( HttpClientContext.COOKIE_STORE,
                    cookieStore );
            final CloseableHttpResponse authResponse = httpClient.execute(
                    authPost, httpContext );

            if ( authResponse.getStatusLine().getStatusCode() == 200 )
            {
                authResponse.close();

                if ( cookieStore != null )
                {
                    for ( Cookie cookie : cookieStore.getCookies() )
                    {
                        if ( cookie.getName().equals( "ipa_session" ) )
                        {
                            return RESULT_AUTH_SUCCESS;
                        }
                    }
                }
            }

            if ( authResponse.getStatusLine().getStatusCode() == 401 )
            {
                final Header[] failReason = authResponse.getHeaders(
                        "X-IPA-Rejection-Reason" );
                authResponse.close();
                if ( failReason.length > 0 )
                {
                    if ( failReason[ 0 ].getValue().equals( "password-expired" ) )
                    {
                        return RESULT_AUTH_EXPIRED;
                    }

                    if ( failReason[ 0 ].getValue().equals( "invalid-password" ) )
                    {
                        return RESULT_AUTH_INVALID;
                    }

                    if ( failReason[ 0 ].getValue().equals( "denied" ) )
                    {
                        return RESULT_AUTH_DENIED;
                    }
                }
            }
        }
        catch ( IOException e )
        {
            return RESULT_AUTH_UNKNOWN_ERROR;
        }
        return RESULT_AUTH_UNKNOWN_ERROR;
    }

    public int createUser()
    {
        if ( makeJSON() == RESULT_INSUFFICIENT_ATTRIBUTES )
        {
            return RESULT_INSUFFICIENT_ATTRIBUTES;
        }

//        final int authResult = authUser();
//        if ( authResult != RESULT_AUTH_SUCCESS )
//        {
//            return authResult;
//        }

        final StringEntity userRequest = new StringEntity(
                jsonRequest,
                ContentType.APPLICATION_JSON );

        final HttpPost userPost = new HttpPost( getURL( IPA_SESSION_JSON_PAGE ) );
        try
        {
            final HttpContext httpContext = new BasicHttpContext();
            httpContext.setAttribute( HttpClientContext.COOKIE_STORE,
                    cookieStore );
            userPost.setEntity( userRequest );
            userPost.setHeader( "Referer", getURL( IPA_REFERRAL_PAGE ) );
            userPost.setHeader( "Content-Type", "application/json" );
            userPost.setHeader( "Accept", "applicaton/json" );

            final CloseableHttpResponse userResponse = httpClient.execute(
                    userPost, httpContext );

            if ( userResponse.getStatusLine().getStatusCode() == 200 )
            {
                userResponse.close();
                return RESULT_USER_CREATED;
            }
            userResponse.close();
            return RESULT_CREATE_FAILED;
        }
        catch ( IOException e )
        {
            return RESULT_CREATE_FAILED;
        }
    }

}
