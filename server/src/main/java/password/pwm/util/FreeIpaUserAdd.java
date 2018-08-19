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

import org.apache.http.Header
import org.apache.http.HttpResponse
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.HttpPost

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
  
  private static final String createUserJsonHead = "{\"method\":\"user_add\",\"params\":[[\"";
  private static final String createUserJsonMid = "\"],{";
  private static final String createUserJsonTail = " }],\"id\":0}";
  private static final String createUserJsonAttribFront = " \"";
  private static final String createUserJsonAttribMid = "\": \"";
  private static final String createUserJsonAttribEnd = "\",";
  
  private String username;
  private String password;
  private String hostname;
  private String json_request;
  private CookieStore cookieStore;
  private Map<String, String> createAttributes;
  private CloseableHttpClient httpclient;
  
  public FreeIpaUserAdd( final String username, final String password, final String hostname, final Map<String, String> createAttributes )
  {
    this.username = username;
    this.password = password;
    this.hostname = hostname;
    this.createAttributes = createAttributes;
    
    SSLContextBuilder builder = new SSLContextBuilder();
    builder.loadTrustMaterial(null, new TrustSelfSignedStrategy());
    SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(
            builder.build());
    httpclient = HttpClients.custom().setSSLSocketFactory(
            sslsf).build();

  }
  
  private String getURL( final String urlSuffix )
  {
    return "https://" + hostname + urlSuffix;
  }
  
  private void makeJSON()
  {
    private boolean hasUID = false;
    private boolean hasSN = false;
    private boolean hasGivenName = false;
    private String jsonTempFront = "";
    private String jsonTempEnd = "";

    for ( Map<String, String> attribute : createAttributes.entrySet() )
    {
      String key = attribute.getKey();
      String value = attribute.getValue();

      if ( key.equalsIgnoreCase( IPA_REQUIRED_UID ) )
      {
        hasUID = true;
        jsonTempFront = createUserJsonHead + value + createUserJsonMid;
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
        jsonTempEnd += createUserJsonAttribFront + key + createUserJsonAttribMid + value + createUserJsonAttribEnd;
      }
    }
    if ( hasUID && hasSN && hasGivenName )
    {
      if (jsonTempEnd != null && jsonTempEnd.length() > 0 && jsonTempEnd.charAt(jsonTempEnd.length() - 1) == ',')
      {
        json_request = jsonTempFront + jsonTempEnd.substring(0, jsonTempEnd.length() - 1) + createUserJsonTail;
        return RESULT_ATTRIBUTES_PARSE;
      }
    }
    return RESULT_INSUFFICIENT_ATTRIBUTES;
  }
  
  private int authUser()
  {
    cookieStore = new BasicCookieStore();
    
    HttpPost authPost = getPost( getURL( IPA_SESSION_AUTH_PAGE ) );
    authPost.setEntity(new UrlEncodedFormEntity( [
      new BasicNameValuePair( "user", username ),
      new BasicNameValuePair( "password", password )
    ] ) );
    authPost.setHeader("Referer", getURL( IPA_REFERRAL_PAGE ) );
    authPost.setHeader("Content-Type", "application/x-www-form-urlencoded" );
    authPost.setHeader("Accept", "text/plain" );
    
    CloseableHttpResponse authResponse = httpclient.execute( authPost, cookieStore );
    
    if ( authResponse.getStatusLine().statusCode == 200 ) 
    {
      Cookie[] cookies = authResponse.getCookies();
      authResponse.close();
      
      if ( cookies != null )
      {
        for ( Cookie cookie : cookies )
        {
          if ( cookie.getName().equals( "ipa_session" ) )
          {
            return RESULT_AUTH_SUCCESS;
          }
        }
      }
    }
    
    if ( authResponse.getStatusLine().statusCode == 401 )
    {
      Header[] failReason = response.getHeaders( "X-IPA-Rejection-Reason" );
      authResponse.close();
      if ( failReason.size() > 0)
      {
        if ( failReason[0].value == "password-expired" )
        {
          return RESULT_AUTH_EXPIRED;
        }
        
        if ( failReason[0].value == "invalid-password" )
        {
          return RESULT_AUTH_INVALID;
        }
        
        if ( failReason[0].value == "denied" )
        {
          return RESULT_AUTH_DENIED;
        }
      }
    }
    
    return RESULT_AUTH_UNKNOWN_ERROR;
  }
  
  public int createUser()
  {
    if ( makeJSON() == RESULT_INSUFFICIENT_ATTRIBUTES )
    {
      return RESULT_INSUFFICIENT_ATTRIBUTES;
    }
    
    if ( cookieStore == null )
    {
      private int authResult = authUser();
      if ( authResult != RESULT_AUTH_SUCCESS )
      {
        return authResult;
      }
    }
    
    StringEntity userRequest = new StringEntity(
    jsonRequest,
    ContentType.APPLICATION_JSON );

    HttpPost userPost = getPost( getURL( IPA_SESSION_JSON_PAGE ) );
    userPost.setEntity( userRequest);
    userPost.setHeader( "Referer", getURL( IPA_REFERRAL_PAGE ) );
    userPost.setHeader( "Content-Type", "application/json" );
    userPost.setHeader( "Accept", "applicaton/json" );

    CloseableHttpResponse userResponse = httpclient.execute( userPost, cookieStore );
    
    if ( userResponse.getStatusLine().statusCode == 200 )
    {
      userResponse.close();
      return RESULT_USER_CREATED;
    }
    userResponse.close();
    return RESULT_CREATE_FAILED;
  }
}
 
   
