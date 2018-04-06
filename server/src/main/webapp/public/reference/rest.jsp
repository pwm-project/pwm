<%--
 ~ Password Management Servlets (PWM)
 ~ http://www.pwm-project.org
 ~
 ~ Copyright (c) 2006-2009 Novell, Inc.
 ~ Copyright (c) 2009-2018 The PWM Project
 ~
 ~ This program is free software; you can redistribute it and/or modify
 ~ it under the terms of the GNU General Public License as published by
 ~ the Free Software Foundation; either version 2 of the License, or
 ~ (at your option) any later version.
 ~
 ~ This program is distributed in the hope that it will be useful,
 ~ but WITHOUT ANY WARRANTY; without even the implied warranty of
 ~ MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 ~ GNU General Public License for more details.
 ~
 ~ You should have received a copy of the GNU General Public License
 ~ along with this program; if not, write to the Free Software
 ~ Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
--%>

<%@ page import="password.pwm.http.JspUtility" %>

<!DOCTYPE html>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.HIDE_HEADER_WARNINGS); %>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.NO_REQ_COUNTER); %>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.HIDE_HEADER_BUTTONS); %>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.HIDE_FOOTER_TEXT); %>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.NO_IDLE_TIMEOUT); %>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.HIDE_FOOTER_TEXT); %>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.INCLUDE_CONFIG_CSS);%>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<body class="nihilo">
<style nonce="<pwm:value name="<%=PwmValue.cspNonce%>"/>" type="text/css">
    .exampleTD {
        overflow: auto;
        display: block;
        max-width:400px;
        max-height:400px;
        background-color: black;
        color: white;
    }
</style>
<div id="wrapper">
<jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
    <jsp:param name="pwm.PageName" value="REST Services Reference"/>
</jsp:include>
<div id="centerbody" class="wide">
    <%@ include file="reference-nav.jsp"%>

    <ol>
        <li><a href="#introduction">Introduction</a></li>
        <li><a href="#rest-challenges">Rest Service - challenges</a></li>
        <li><a href="#rest-checkpassword">Rest Service - checkpassword</a></li>
        <li><a href="#rest-health">Rest Service - health</a></li>
        <li><a href="#rest-profile">Rest Service - profile</a></li>
        <li><a href="#rest-randompassword">Rest Service - randompassword</a></li>
        <li><a href="#rest-setpassword">Rest Service - setpassword</a></li>
        <li><a href="#rest-signing-form">Rest Service - signing/form</a></li>
        <li><a href="#rest-statistics">Rest Service - statistics</a></li>
        <li><a href="#rest-status">Rest Service - status</a></li>
        <li><a href="#rest-verifyotp">Rest Service - verifyotp</a></li>
        <li><a href="#rest-verifyresponses">Rest Service - verifyresponses</a></li>
    </ol>

    <br/><br/>

    <br/>
    <h1><a id="introduction">Introduction</a></h1>
    <p>This system has a set of <a href="https://en.wikipedia.org/wiki/Representational_state_transfer#RESTful_web_APIs">RESTful web APIs</a> to facilitate 3rd party application development.</p>
    <h2>Authentication</h2>
    <p>All web services are authenticated using <a href="http://en.wikipedia.org/wiki/Basic_access_authentication">basic access authentication</a> utilizing the standard <i>Authorization</i> header.</p>
    <p>The username portion of the authentication can either be a fully qualified LDAP DN of the user, or a username string value which the application will search for the user</p>
    <p>Additionally, the application must be configured in such away to allow web service calls.  Not all functions may be enabled.  Some operations which involve a third party (other then the authenticated user) may require additional permissions configured within the application.</p>
    <h2>Standard JSON Response</h2>
    All JSON encoded responses are presented using a standard JSON object:
    Example:
<pre>
{
   "error": true,
   "errorCode": 5004,
   "errorMessage": "Authentication required.",
   "errorDetail": "5004 ERROR_AUTHENTICATION_REQUIRED",
   "data": {}
}
</pre>
    <table>
        <tr>
            <td class="title" style="font-size: smaller">field</td>
            <td class="title" style="font-size: smaller">type</td>
            <td class="title" style="font-size: smaller">description</td>
        </tr>
        <tr><td>error</td><td>boolean</td><td>true if the operation was successfull</td></tr>
        <tr><td>errorCode</td><td>four-digit number</td><td>application error code</td></tr>
        <tr><td>errorMessage</td><td>string</td><td>Localized error message string</td></tr>
        <tr><td>errorDetail</td><td>string</td><td>Error Number, Error ID and debugging detail message if any, English only</td></tr>
        <tr><td>successMessage</td><td>string</td><td>Localized success message string</td></tr>
        <tr><td>data</td><td>object</td><td>Requested data</td></tr>
    </table>
    <br/>
    <h1><a id="rest-challenges">Rest Service - challenges</a></h1>
<table >
<tr>
    <td class="key" style="width:50px">url</td>
    <td><a href="<pwm:context/>/public/rest/challenges"><pwm:context/>/public/rest/challenges</a></td>
</tr>
<tr>
<td class="key" style="width:50px">GET Method</td>
<td>
<table>
    <tr>
        <td class="key">Description</td>
        <td>Retrieve users stored challenges.  Location of read responses is determined by the application configuration.
            This interface cannot be used to read NMAS stored responses.</td>

    </tr>
    <tr>
        <td class="key">Authentication</td>
        <td>Required</td>
    </tr>
    <tr>
        <td class="key">Accept-Language Header</td>
        <td>en
            <br/>
            <i>The request will be processed in the context of the specified language</i>
        </td>
    </tr>
    <tr>
        <td class="key">Accept Header</td>
        <td>application/json</td>
    </tr>
    <tr>
        <td class="key">Parameter answers</td>
        <td>
            <table>
                <tr><td>Name</td><td>answers</td></tr>
                <tr><td>Required</td><td>Optional</td></tr>
                <tr><td>Location</td><td>query string</td></tr>
                <tr><td>Value</td><td>Boolean indicating if answers (in whatever format stored) should be returned in the result.</td></tr>
                <tr><td>Default</td><td>false</td></tr>
            </table>
        </td>
    </tr>
    <tr>
        <td class="key">Parameter helpdesk</td>
        <td>
            <table>
                <tr><td>Name</td><td>helpdesk</td></tr>
                <tr><td>Required</td><td>Optional</td></tr>
                <tr><td>Location</td><td>query string</td></tr>
                <tr><td>Value</td><td>Boolean indicating if helpdesk answers should be returned in the result.</td></tr>
                <tr><td>Default</td><td>false</td></tr>
            </table>
        </td>
    </tr>
    <tr>
        <td class="key">Parameter username</td>
        <td>
            <table>
                <tr><td>Name</td><td>username</td></tr>
                <tr><td>Required</td><td>Optional</td></tr>
                <tr><td>Location</td><td>query string</td></tr>
                <tr><td>Value</td><td>Optional username or ldap DN of a user on which to perform the operation</td></tr>
                <tr><td>Default</td><td>Authenticating user (if LDAP)</td></tr>
            </table>
        </td>
    </tr>
</table>
<table style="max-width: 100%">
    <tr>
        <td class="title" style="font-size: smaller" colspan="2">Example 1</td>
    </tr>
    <tr>
        <td class="key">Request</td>
<td class="exampleTD">
<pre>
GET <pwm:context/>/public/rest/challenges HTTP/1.1
Accept: application/json
Location: en
Authorization: Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ==
</pre>
        </td>
    </tr>
    <tr>
        <td class="key">Response</td>
<td class="exampleTD">
<pre>
{
  "error": false,
  "errorCode": 0,
  "data": {
    "challenges": [
      {
        "challengeText": "What is the name of the main character in your favorite book?",
        "minLength": 4,
        "maxLength": 200,
        "adminDefined": true,
        "required": false
      },
      {
        "challengeText": "What is the name of your favorite teacher?",
        "minLength": 4,
        "maxLength": 200,
        "adminDefined": true,
        "required": false
      },
      {
        "challengeText": "Who is your favorite author?",
        "minLength": 4,
        "maxLength": 200,
        "adminDefined": true,
        "required": false
      },
      {
        "challengeText": "What street did you grow up on?",
        "minLength": 4,
        "maxLength": 200,
        "adminDefined": true,
        "required": false
      }
    ],
    "minimumRandoms": 2
  }
}
</pre>
        </td>
    </tr>
</table>
<table style="max-width: 100%">
    <tr>
        <td class="title" style="font-size: smaller" colspan="2">Example 2</td>
    </tr>
    <tr>
        <td class="key">Request</td>
<td class="exampleTD">
<pre>
GET <pwm:context/>/public/rest/challenges?answers=true&helpdesk=true HTTP/1.1
Accept: application/json
Accept-Language: en
Authorization: Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ==
</pre>
        </td>
    </tr>
    <tr>
        <td class="key">Response</td>
<td class="exampleTD">
<pre>
{
  "error": false,
  "errorCode": 0,
  "data": {
    "challenges": [
      {
        "challengeText": "What is the name of the main character in your favorite book?",
        "minLength": 4,
        "maxLength": 200,
        "adminDefined": true,
        "required": false,
        "answer": {
          "type": "SHA1_SALT",
          "answerHash": "Q8zzP3Tyo5i4IKDaT1stkbh/m80=",
          "salt": "3ZxfxbmlF4yp2KfDOAkDvMP9EgGOgkPL",
          "hashCount": 100000,
          "caseInsensitive": true
        }
      },
      {
        "challengeText": "What is your least favorite film of all time?",
        "minLength": 4,
        "maxLength": 200,
        "adminDefined": true,
        "required": false,
        "answer": {
          "type": "SHA1_SALT",
          "answerHash": "rzBiYIvZvdbbCrXgoUDtqKdwrh0=",
          "salt": "RoaMiuCNBXZjK9vqeV7xYdRsKdL0D1wi",
          "hashCount": 100000,
          "caseInsensitive": true
        }
      },
      {
        "challengeText": "What street did you grow up on?",
        "minLength": 4,
        "maxLength": 200,
        "adminDefined": true,
        "required": false,
        "answer": {
          "type": "SHA1_SALT",
          "answerHash": "3KhvS0YLV3qAso1QmtZzGiv36s0=",
          "salt": "a27P1ke4Z4qchdcjepIjSikF8JKNa50U",
          "hashCount": 100000,
          "caseInsensitive": true
        }
      },
      {
        "challengeText": "Who is your favorite author?",
        "minLength": 4,
        "maxLength": 200,
        "adminDefined": true,
        "required": false,
        "answer": {
          "type": "SHA1_SALT",
          "answerHash": "N8VLF4UN6+7IvPN/LVwSfZhCjm4=",
          "salt": "oBulI5Y6u7JgrItFPbu8vMEJqhe3lq8o",
          "hashCount": 100000,
          "caseInsensitive": true
        }
      }
    ],
    "helpdeskChallenges": [
      {
        "challengeText": "Helpdesk Question 1",
        "minLength": 2,
        "maxLength": 100,
        "adminDefined": true,
        "required": true,
        "answer": {
          "type": "HELPDESK",
          "answerText": "Answer 1",
          "hashCount": 0,
          "caseInsensitive": false
        }
      },
      {
        "challengeText": "Helpdesk Question 2",
        "minLength": 2,
        "maxLength": 100,
        "adminDefined": true,
        "required": true,
        "answer": {
          "type": "HELPDESK",
          "answerText": "Answer 2",
          "hashCount": 0,
          "caseInsensitive": false
        }
      }
    ],
    "minimumRandoms": 2
  }
}
</pre>
        </td>
    </tr>
</table>
</td>
</tr>
<tr>
    <td class="key" style="width:50px">POST Method</td>
    <td>
        <table>
            <tr>
                <td class="key">Description</td>
                <td>Set users stored challenge/response set</td>
            </tr>
            <tr>
                <td class="key">Authentication</td>
                <td>Required</td>
            </tr>
            <tr>
                <td class="key">Accept-Language Header</td>
                <td>en
                    <br/>
                    <i>The request will be processed in the context of the specified language</i>
                </td>
            </tr>
            <tr>
                <td class="key">Accept Header</td>
                <td>application/json</td>
            </tr>
            <tr>
                <td class="key">Content-Type Header</td>
                <td>application/json</td>
            </tr>
            <tr>
                <td class="key">Parameter username</td>
                <td>
                    <table>
                        <tr><td>Name</td><td>username</td></tr>
                        <tr><td>Required</td><td>Optional</td></tr>
                        <tr><td>Location</td><td>query string or json body</td></tr>
                        <tr><td>Value</td><td>Optional username or ldap DN of a user on which to perform the operation</td></tr>
                        <tr><td>Default</td><td>Authenticating user (if LDAP)</td></tr>
                    </table>
                </td>
            </tr>
            <tr>
                <td class="key">Parameter challenges</td>
                <td>
                    <table>
                        <tr><td>Name</td><td>challenges</td></tr>
                        <tr><td>Required</td><td>Required</td></tr>
                        <tr><td>Location</td><td>json body</td></tr>
                        <tr><td>Value</td><td>List of challenge objects including answers with an answerText property.  Retrieve challenge objects using
                            the challenges service to discover the proper object formatting.  The question object data must match
                            precisely the question object received from the challenges service so that the answer can be applied to
                            the correct corresponding question.  This includes each parameter of the question object.</td></tr>
                        <tr><td>Default</td><td>n/a</td></tr>
                    </table>
                </td>
            </tr>
        </table>
        <table style="max-width: 100%">
            <tr>
                <td class="title" style="font-size: smaller" colspan="2">Example 1</td>
            </tr>
            <tr>
                <td class="key">Request</td>
        <td class="exampleTD">
<pre>
Accept-Language: en
POST <pwm:context/>/public/rest/challenges HTTP/1.1
Accept: application/json
Content-Type: application/json
Authorization: Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ==

{
   "challenges":[
      {
         "challengeText":"What is the name of the main character in your favorite book?",
         "minLength":4,
         "maxLength":200,
         "adminDefined":true,
         "required":false,
         "answer":{
            "answerText":"Answer 1"
         }
      },
      {
         "challengeText":"What is your least favorite film of all time?",
         "minLength":4,
         "maxLength":200,
         "adminDefined":true,
         "required":false,
         "answer":{
            "answerText":"Answer 2"
         }
      },
      {
         "challengeText":"What street did you grow up on?",
         "minLength":4,
         "maxLength":200,
         "adminDefined":true,
         "required":false,
         "answer":{
            "answerText":"Answer 3"
         }
      },
      {
         "challengeText":"Who is your favorite author?",
         "minLength":4,
         "maxLength":200,
         "adminDefined":true,
         "required":false,
         "answer":{
            "answerText":"Answer 4"
         }
      }
   ],
   "helpdeskChallenges":[
      {
         "challengeText":"Helpdesk Question 1",
         "minLength":2,
         "maxLength":100,
         "adminDefined":true,
         "required":true,
         "answer":{
            "answerText":"Answer 5"
         }
      },
      {
         "challengeText":"Helpdesk Question 2",
         "minLength":2,
         "maxLength":100,
         "adminDefined":true,
         "required":true,
         "answer":{
            "answerText":"Answer 6"
         }
      }
   ],
   "minimumRandoms":2
}
</pre>
                </td>
            </tr>
            <tr>
                <td class="key">Response</td>
        <td class="exampleTD">
<pre>
{
  "error": false,
  "errorCode": 0,
  "successMessage": "Your secret questions and answers have been successfully saved.  If you ever forget your password, you can use the answers to these questions to reset your password."
}
</pre>
                </td>
            </tr>
        </table>
        <table style="max-width: 100%">
            <tr>
                <td class="title" style="font-size: smaller" colspan="2">Example 2</td>
            </tr>
            <tr>
                <td class="key">Request</td>
        <td class="exampleTD">
<pre>
POST <pwm:context/>/public/rest/challenges HTTP/1.1
Accept-Language: en
Accept: application/json
Content-Type: application/json
Authorization: Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ==

{
   "challenges":[
      {
         "challengeText":"Who is your favorite author?",
         "minLength":4,
         "maxLength":200,
         "adminDefined":true,
         "required":false,
         "answer":{
            "answerText":"Answer 1"
         }
      }
   ],
   "minimumRandoms":1,
   "username":"otherUser",
}
</pre>
                </td>
            </tr>
            <tr>
                <td class="key">Response</td>
        <td class="exampleTD">
<pre>
{
  "error": false,
  "errorCode": 0,
  "successMessage": "Your secret questions and answers have been successfully saved.  If you ever forget your password, you can use the answers to these questions to reset your password."
}
</pre>
                </td>
            </tr>
        </table>
    </td>
</tr>
<tr>
    <td class="key" style="width:50px">DELETE Method</td>
    <td>
        <table>
            <tr>
                <td class="key">Description</td>
                <td>Clear users saved responses</td>
            </tr>
            <tr>
                <td class="key">Authentication</td>
                <td>Required</td>
            </tr>
            <tr>
                <td class="key">Accept Header</td>
                <td>application/json</td>
            </tr>
            <tr>
                <td class="key">Parameter username</td>
                <td>
                    <table>
                        <tr><td>Name</td><td>username</td></tr>
                        <tr><td>Required</td><td>Optional</td></tr>
                        <tr><td>Location</td><td>query string</td></tr>
                        <tr><td>Value</td><td>Optional username or ldap DN of a user on which to perform the operation</td></tr>
                        <tr><td>Default</td><td>Authenticating user (if LDAP)</td></tr>
                    </table>
                </td>
            </tr>
        </table>
        <table style="max-width: 100%">
            <tr>
                <td class="title" style="font-size: smaller" colspan="2">Example 1</td>
            </tr>
            <tr>
                <td class="key">Request</td>
        <td class="exampleTD">
<pre>
DELETE <pwm:context/>/public/rest/challenges HTTP/1.1
Accept-Language: en
Accept: application/json
Content-Type: application/json
Authorization: Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ==
</pre>
                </td>
            </tr>
            <tr>
                <td class="key">Response</td>
        <td class="exampleTD">
 <pre>
 {
   "error": false,
   "errorCode": 0,
   "successMessage": "Your secret questions and answers have been successfully saved.  If you ever forget your password, you can use the answers to these questions to reset your password."
 }
 </pre>
                </td>
            </tr>
        </table>
    </td>
</tr>
</table>
    <br/>
    <h1><a id="rest-checkpassword">Rest Service - checkpassword</a></h1>
    <table >
        <tr>
            <td class="key" style="width:50px">url</td>
            <td><a href="<pwm:context/>/public/rest/checkpassword"><pwm:context/>/public/rest/checkpassword</a></td>
        </tr>
        <tr>
        </tr>
        <tr>
            <td class="key" style="width:50px">POST Method</td>
            <td>
                <table>
                    <tr>
                        <td class="key">Description</td>
                        <td>Check a password value(s) against user policy</td>
                    </tr>
                    <tr>
                        <td class="key">Authentication</td>
                        <td>Required</td>
                    </tr>
                    <tr>
                        <td class="key">Accept-Language Header</td>
                        <td>en
                            <br/>
                            <i>The request will be processed in the context of the specified language</i>
                        </td>
                    </tr>
                    <tr>
                        <td class="key">Accept Header</td>
                        <td>application/json</td>
                    </tr>
                    <tr>
                        <td class="key">Content-Type Header</td>
                        <td>
                            application/json
                            <br/>
                            application/x-www-form-urlencoded
                        </td>

                    </tr>
                    <tr>
                        <td class="key">Parameter password1</td>
                        <td>
                            <table>
                                <tr><td>Name</td><td>password1</td></tr>
                                <tr><td>Required</td><td>Required</td></tr>
                                <tr><td>Location</td><td>query string, json body, or form body</td></tr>
                                <tr><td>Value</td><td>Password value</td></tr>
                                <tr><td>Default</td><td>n/a</td></tr>
                            </table>
                        </td>
                    </tr>
                    <tr>
                        <td class="key">Parameter password2</td>
                        <td>
                            <table>
                                <tr><td>Name</td><td>password2</td></tr>
                                <tr><td>Required</td><td>Required</td></tr>
                                <tr><td>Location</td><td>query string, json body, or form body</td></tr>
                                <tr><td>Value</td><td>Password confirmation value</td></tr>
                                <tr><td>Default</td><td>n/a</td></tr>
                            </table>
                        </td>
                    </tr>
                    <tr>
                        <td class="key">Parameter username</td>
                        <td>
                            <table>
                                <tr><td>Name</td><td>username</td></tr>
                                <tr><td>Required</td><td>Optional</td></tr>
                                <tr><td>Location</td><td>query string, json body, or form body</td></tr>
                                <tr><td>Value</td><td>Optional username or ldap DN of a user on which to perform the operation</td></tr>
                                <tr><td>Default</td><td>Authenticating user (if LDAP)</td></tr>
                            </table>
                        </td>
                    </tr>
                </table>
                <table style="max-width: 100%">
                    <tr>
                        <td class="title" style="font-size: smaller" colspan="2">Example 1</td>
                    </tr>
                    <tr>
                        <td class="key">Request</td>
                <td class="exampleTD">
<pre>
POST <pwm:context/>/public/rest/checkpassword HTTP/1.1
Accept-Language: en
Accept: application/json
Content-Type: application/json
Authorization: Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ==

{
   "password1":"newPassword",
   "password2":"newPasswOrd"
}
</pre>
                        </td>
                    </tr>
                    <tr>
                        <td class="key">Response</td>
                <td class="exampleTD">
<pre>
{
  "error": false,
  "errorCode": 0,
  "data": {
    "version": 2,
    "strength": 37,
    "match": "NO_MATCH",
    "message": "New password is using a value that is not allowed",
    "passed": false,
    "errorCode": 4034
  }
}
</pre>
                        </td>
                    </tr>
                </table>
                <table style="max-width: 100%">
                    <tr>
                        <td class="title" style="font-size: smaller" colspan="2">Example 2</td>
                    </tr>
                    <tr>
                        <td class="key">Request</td>
                <td class="exampleTD">
<pre>
POST <pwm:context/>/public/rest/checkpassword HTTP/1.1
Accept-Language: en
Accept: application/json
Content-Type: application/x-www-form-urlencoded
Authorization: Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ==

password1=dsa32!dabed&password2=dsa32!dabed&username=user1234
</pre>
                        </td>
                    </tr>
                    <tr>
                        <td class="key">Response</td>
                <td class="exampleTD">
<pre>
{
   "error":false,
   "errorCode":0,
   "data":{
      "version":2,
      "strength":21,
      "match":"MATCH",
      "message":"New password accepted, please click change password",
      "passed":true,
      "errorCode":0
   }
}
</pre>
                        </td>
                    </tr>
                </table>
            </td>
        </tr>
    </table>
    <br/>
    <h1><a id="rest-health">Rest Service - health</a></h1>
    <table >
        <tr>
            <td class="key" style="width:50px">url</td>
            <td><a href="<pwm:context/>/public/rest/health"><pwm:context/>/public/rest/health</a></td>
        </tr>
        <tr>
        </tr>
        <tr>
            <td class="key" style="width:50px">GET Method</td>
            <td>
                <table>
                    <tr>
                        <td class="key">Description</td>
                        <td>Read the health of the application</td>
                    </tr>
                    <tr>
                        <td class="key">Authentication</td>
                        <td>Not Required</td>
                    </tr>
                    <tr>
                        <td class="key">Accept-Language Header</td>
                        <td>en
                            <br/>
                            <i>The request will be processed in the context of the specified language</i>
                        </td>
                    </tr>
                    <tr>
                        <td class="key">Accept Header</td>
                        <td>
                            application/json
                            <br/>
                            text/plain
                        </td>
                    </tr>
                    <tr>
                        <td class="key">Parameter refreshImmediate</td>
                        <td>
                            refreshImmediate=true
                            <br/>
                            <i>Indicates if the server should refresh the health status before calling this service.  Only available if logged in with administrative rights.</i>
                        </td>
                    </tr>
                </table>
                <table style="max-width: 100%">
                    <tr>
                        <td class="title" style="font-size: smaller" colspan="2">Example 1</td>
                    </tr>
                    <tr>
                        <td class="key">Request</td>
                <td class="exampleTD">
<pre>
GET <pwm:context/>/public/rest/health HTTP/1.1
Accept-Language: en
Accept: application/json
</pre>
                        </td>
                    </tr>
                    <tr>
                        <td class="key">Response</td>
                <td class="exampleTD">
<pre>
{
   "error":false,
   "errorCode":0,
   "data":{
      "timestamp":"Mar 27, 2013 6:15:04 PM",
      "overall":"CONFIG",
      "records":[
         {
            "status":"CONFIG",
            "topic":"Configuration",
            "detail":"LDAP Directory -> LDAP Proxy Password strength of password is weak (21/100); increase password length/complexity for proper security"
         },
         {
            "status":"CONFIG",
            "topic":"Configuration",
            "detail":"PWM is currently in <b>configuration</b> mode. Anyone accessing this site can modify the configuration without authenticating. When ready, restrict the configuration to secure this installation."
         },
         {
            "status":"CONFIG",
            "topic":"Configuration",
            "detail":"Security -> Require HTTPS setting should be set to true for proper security"
         },
         {
            "status":"GOOD",
            "topic":"Java Platform",
            "detail":"Java platform is operating normally"
         },
         {
            "status":"GOOD",
            "topic":"LDAP",
            "detail":"All configured LDAP servers are reachable"
         },
         {
            "status":"GOOD",
            "topic":"LDAP",
            "detail":"LDAP test user account is functioning normally"
         },
         {
            "status":"GOOD",
            "topic":"LocalDB",
            "detail":"LocalDB and related services are operating correctly"
         }
      ]
   }
}
</pre>
                        </td>
                    </tr>
                </table>
                <table style="max-width: 100%">
                    <tr>
                        <td class="title" style="font-size: smaller" colspan="2">Example 2</td>
                    </tr>
                    <tr>
                        <td class="key">Request</td>
                <td class="exampleTD">
<pre>
GET <pwm:context/>/public/rest/health&refreshImmediate=true HTTP/1.1
Accept-Language: en
Accept: text/plain
</pre>
                        </td>
                    </tr>
                    <tr>
                        <td class="key">Response</td>
                <td class="exampleTD">
<pre>
GOOD
</pre>
                        </td>
                    </tr>
                </table>
            </td>
        </tr>
    </table>
    <br/>
    <h1><a id="rest-profile">Rest Service - profile</a></h1>
    <table >
        <tr>
            <td class="key" style="width:50px">url</td>
            <td><a href="<pwm:context/>/public/rest/profile"><pwm:context/>/public/rest/profile</a></td>
        </tr>
        <tr>
            <td class="key" style="width:50px">GET Method</td>
            <td>
                <table>
                    <tr>
                        <td class="key">Description</td>
                        <td>Retrieve users profile data</td>
                    </tr>
                    <tr>
                        <td class="key">Authentication</td>
                        <td>Required</td>
                    </tr>
                    <tr>
                        <td class="key">Accept-Language Header</td>
                        <td>en
                            <br/>
                            <i>The request will be processed in the context of the specified language</i>
                        </td>
                    </tr>
                    <tr>
                        <td class="key">Accept Header</td>
                        <td>application/json</td>
                    </tr>
                </table>
                <table style="max-width: 100%">
                    <tr>
                        <td class="title" style="font-size: smaller" colspan="2">Example 1</td>
                    </tr>
                    <tr>
                        <td class="key">Request</td>
                <td class="exampleTD">
<pre>
GET <pwm:context/>/public/rest/profile HTTP/1.1
Accept: application/json
Accept-Language: en
Authorization: Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ==
</pre>
                        </td>
                    </tr>
                    <tr>
                        <td class="key">Response</td>
                <td class="exampleTD">
<pre>
{
  "error": false,
  "errorCode": 0,
  "data": {
    "profile": {
      "title": "Genious",
      "description": "Genious User",
      "telephoneNumber": "555-1212"
    },
    "formDefinition": [
      {
        "name": "telephoneNumber",
        "minimumLength": 3,
        "maximumLength": 15,
        "type": "text",
        "required": true,
        "confirmationRequired": false,
        "readonly": false,
        "labels": {
          "": "Telephone Number"
        },
        "regexErrors": {
          "": ""
        },
        "description": {
          "": ""
        },
        "selectOptions": {

        }
      },
      {
        "name": "title",
        "minimumLength": 2,
        "maximumLength": 15,
        "type": "text",
        "required": true,
        "confirmationRequired": false,
        "readonly": false,
        "labels": {
          "": "Title"
        },
        "regexErrors": {
          "": ""
        },
        "description": {
          "": ""
        },
        "selectOptions": {

        }
      },
      {
        "name": "description",
        "minimumLength": 1,
        "maximumLength": 50,
        "type": "hidden",
        "required": false,
        "confirmationRequired": false,
        "readonly": false,
        "labels": {
          "": "Descr"
        },
        "regexErrors": {
          "": ""
        },
        "description": {
          "": ""
        },
        "selectOptions": {

        }
      }
    ]
  }
}
</pre>
                        </td>
                    </tr>
                </table>
            </td>
        </tr>
        <tr>
            <td class="key" style="width:50px">POST Method</td>
            <td>
                <table>
                    <tr>
                        <td class="key">Description</td>
                        <td>Set profile data</td>
                    </tr>
                    <tr>
                        <td class="key">Authentication</td>
                        <td>Required</td>
                    </tr>
                    <tr>
                        <td class="key">Accept-Language Header</td>
                        <td>en
                            <br/>
                            <i>The request will be processed in the context of the specified language</i>
                        </td>
                    </tr>
                    <tr>
                        <td class="key">Accept Header</td>
                        <td>application/json</td>
                    </tr>
                    <tr>
                        <td class="key">Content-Type Header</td>
                        <td>application/json</td>
                    </tr>
                </table>
                <table style="max-width: 100%">
                    <tr>
                        <td class="title" style="font-size: smaller" colspan="2">Example 1</td>
                    </tr>
                    <tr>
                        <td class="key">Request</td>
                <td class="exampleTD">
<pre>
POST <pwm:context/>/public/rest/profile HTTP/1.1
Accept-Language: en
Accept: application/json
Content-Type: application/json
Authorization: Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ==

{
   "profile":{
      "title":"Genious",
      "description":"Genious User",
      "telephoneNumber":"555-1212"
   }
}
</pre>
                        </td>
                    </tr>
                    <tr>
                        <td class="key">Response</td>
                <td class="exampleTD">
<pre>
{
   "error":false,
   "errorCode":0,
   "successMessage":"Your user information has been successfully updated."
}
</pre>
                        </td>
                    </tr>
                </table>
            </td>
        </tr>
    </table>
    <br/>
    <h1><a id="rest-randompassword">Rest Service - randompassword</a></h1>
    <table>
        <tr>
            <td class="key" style="width:50px">url</td>
            <td><a href="<pwm:context/>/public/rest/randompassword"><pwm:context/>/public/rest/randompassword</a></td>
        </tr>
        <tr>
        </tr>
        <tr>
            <td class="key" style="width:50px">GET Method</td>
            <td>
                <table>
                    <tr>
                        <td class="key">Description</td>
                        <td>Read a single random password value</td>
                    </tr>
                    <tr>
                        <td class="key">Authentication</td>
                        <td>Optional</td>
                    </tr>
                    <tr>
                        <td class="key">Accept-Language Header</td>
                        <td>en
                            <br/>
                            <i>The request will be processed in the context of the specified language</i>
                        </td>
                    </tr>
                    <tr>
                        <td class="key">Accept Header</td>
                        <td>
                            text/plain
                        </td>
                    </tr>
                    <tr>
                        <td class="key">Parameter username</td>
                        <td>
                            username=user1234
                            <br/>
                            <i>Optional username or ldap DN of a user on which to base the random password generation on.  The user's policies will be applied to the random generation.</i>
                        </td>
                    </tr>
                    <tr>
                        <td class="key">Parameter strength</td>
                        <td>
                            strength=50
                            <br/>
                            <i>Optional number (0-100) specifying the minimum strength of the generated password.</i>
                        </td>
                    </tr>
                    <tr>
                        <td class="key">Parameter minLength</td>
                        <td>
                            minLength=5
                            <br/>
                            <i>Optional number specifying the minimum length of the generated password.</i>
                        </td>
                    </tr>
                    <tr>
                        <td class="key">Parameter chars</td>
                        <td>
                            chars=ABCDEFG12345690
                            <br/>
                            <i>Optional list of charachters to use for generating the password.</i>
                        </td>
                    </tr>
                </table>
                <table style="max-width: 100%">
                    <tr>
                        <td class="title" style="font-size: smaller" colspan="2">Example 1</td>
                    </tr>
                    <tr>
                        <td class="key">Request</td>
                <td class="exampleTD">
<pre>
GET <pwm:context/>/public/rest/randompassword HTTP/1.1
Accept-Language: en
Accept: text/plain
</pre>
                        </td>
                    </tr>
                    <tr>
                        <td class="key">Response</td>
                <td class="exampleTD">
<pre>
cLi2mbers
</pre>
                        </td>
                    </tr>
                </table>
            </td>
        </tr>
        <tr>
            <td class="key" style="width:50px">POST Method</td>
            <td>
                <table>
                    <tr>
                        <td class="key">Description</td>
                        <td>Read a single random password value</td>
                    </tr>
                    <tr>
                        <td class="key">Authentication</td>
                        <td>Optional</td>
                    </tr>
                    <tr>
                        <td class="key">Accept-Language Header</td>
                        <td>en
                            <br/>
                            <i>The request will be processed in the context of the specified language</i>
                        </td>
                    </tr>
                    <tr>
                        <td class="key">Accept Header</td>
                        <td>
                            application/json
                        </td>
                    </tr>
                    <tr>
                        <td class="key">Content-Type Header</td>
                        <td>
                            application/json
                        </td>
                    </tr>
                    <tr>
                        <td class="key">Parameter username</td>
                        <td>
                            username=user1234
                            <br/>
                            <i>Optional username or ldap DN of a user on which to base the random password generation on.  The user's policies will be applied to the random generation.</i>
                        </td>
                    </tr>
                    <tr>
                        <td class="key">Parameter strength</td>
                        <td>
                            strength=50
                            <br/>
                            <i>Optional number (0-100) specifying the minimum strength of the generated password.</i>
                        </td>
                    </tr>
                    <tr>
                        <td class="key">Parameter minLength</td>
                        <td>
                            minLength=5
                            <br/>
                            <i>Optional number specifying the minimum length of the generated password.</i>
                        </td>
                    </tr>
                    <tr>
                        <td class="key">Parameter chars</td>
                        <td>
                            chars=ABCDEFG12345690
                            <br/>
                            <i>Optional list of charachters to use for generating the password.</i>
                        </td>
                    </tr>
                </table>
                <table style="max-width: 100%">
                    <tr>
                        <td class="title" style="font-size: smaller" colspan="2">Example 1</td>
                    </tr>
                    <tr>
                        <td class="key">Request</td>
                <td class="exampleTD">
<pre>
GET <pwm:context/>/public/rest/randompassword HTTP/1.1
Accept-Language: en
Accept: application/json
Content-Type: application/json

{
    "chars":"abcdefg123456",
    "strength":5
}
</pre>
                        </td>
                    </tr>
                    <tr>
                        <td class="key">Response</td>
                <td class="exampleTD">
<pre>
{
   "error":false,
   "errorCode":0,
   "data":{
      "password":"bbf535"
   }
}
</pre>
                        </td>
                    </tr>
                </table>
            </td>
        </tr>
    </table>
    <br/>
    <h1><a id="rest-setpassword">Rest Service - setpassword</a></h1>
    <table>
        <tr>
            <td class="key" style="width:50px">url</td>
            <td><a href="<pwm:context/>/public/rest/setpassword"><pwm:context/>/public/rest/setpassword</a></td>
        </tr>
        <tr>
        </tr>
        <tr>
            <td class="key" style="width:50px">POST Method</td>
            <td>
                <table>
                    <tr>
                        <td class="key">Description</td>
                        <td>Set a user's password value</td>
                    </tr>
                    <tr>
                        <td class="key">Authentication</td>
                        <td>Required</td>
                    </tr>
                    <tr>
                        <td class="key">Accept-Language Header</td>
                        <td>en
                            <br/>
                            <i>The request will be processed in the context of the specified language</i>
                        </td>
                    </tr>
                    <tr>
                        <td class="key">Accept Header</td>
                        <td>
                            application/json
                        </td>
                    </tr>
                    <tr>
                        <td class="key">Content-Type Header</td>
                        <td>
                            application/json
                            <br/>
                            application/x-www-form-urlencoded
                        </td>
                    </tr>
                    <tr>
                        <td class="key">Parameter username</td>
                        <td>
                            username=user1234
                            <br/>
                            <i>Optional username or ldap DN of a user on which to set the password.</i>
                        </td>
                    </tr>
                    <tr>
                        <td class="key">Parameter password</td>
                        <td>
                            password=newPassword
                            <br/>
                            <i>Required value of new password.</i>
                        </td>
                    </tr>
                    <tr>
                        <td class="key">Parameter random</td>
                        <td>
                            random=true
                            <br/>
                            <i>Generate a random password (when random=true, no value for 'password' should be supplied.</i>
                        </td>
                    </tr>
                </table>
                <table style="max-width: 100%">
                    <tr>
                        <td class="title" style="font-size: smaller" colspan="2">Example 1</td>
                    </tr>
                    <tr>
                        <td class="key">Request</td>
                <td class="exampleTD">
<pre>
POST <pwm:context/>/public/rest/setpassword HTTP/1.1
Accept-Language: en
Accept: application/json
Content-Type: application/json
Authorization: Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ==

{
   "password": "newPassword"
}
</pre>
                        </td>
                    </tr>
                    <tr>
                        <td class="key">Response</td>
                <td class="exampleTD">
<pre>
cLi2mbers
</pre>
                        </td>
                    </tr>
                </table>
            </td>
        </tr>
    </table>
    <br/>
    <h1><a id="rest-signing-form">Rest Service - signing/form</a></h1>
        <table>
            <tr>
                <td class="key" style="width:50px">url</td>
                <td><a href="<pwm:context/>signing/form"><pwm:context/>/public/rest/signing/form</a></td>
            </tr>
            <tr>
            </tr>
            <tr>
                <td class="key" style="width:50px">POST Method</td>
                <td>
                    <table>
                        <tr>
                            <td class="key">Description</td>
                            <td>Pre-sign (and encrypt) form data for injection into an <pwm:macro value="@PwmAppName@"/> user form.  <pwm:macro value="@PwmAppName@"/> user
                                forms do not permit a remote application to POST data directly to them through a browser.  Instead this signing/form REST api can
                                be used to pre-sign and then submit data to the form.</td>
                        </tr>
                        <tr>
                            <td class="key">Usage</td>
                            <td>After the form data is signed, it can be submitted as part of a request to <pwm:macro value="@PwmAppName@"/> using the
                                <code>signedForm</code> parameter and the value is the encoded <code>data</code> value returned in the result.
                                Values expire after a period of time.
                                <br/><br/><b>Example:</b><br/> <code><pwm:context/>/sspr/public/newuser?signedForm=xxx</code>

                            </td>
                        </tr>
                        <tr>
                            <td class="key">Authentication</td>
                            <td>Required.  Use a named secret username:secret value defined at <code><pwm:macro value="@PwmSettingReference:webservices.external.secrets@"/></code>.</td>
                        </tr>
                        <tr>
                            <td class="key">Accept-Language Header</td>
                            <td>en
                                <br/>
                                <i>The request will be processed in the context of the specified language</i>
                            </td>
                        </tr>
                        <tr>
                            <td class="key">Accept Header</td>
                            <td>
                                application/json
                            </td>
                        </tr>
                        <tr>
                            <td class="key">Content-Type Header</td>
                            <td>
                                application/json
                                <br/>
                                application/x-www-form-urlencoded
                            </td>
                        </tr>
                    </table>
                    <table style="max-width: 100%">
                        <tr>
                            <td class="title" style="font-size: smaller" colspan="2">Example 1</td>
                        </tr>
                        <tr>
                            <td class="key">Request</td>
                            <td class="exampleTD">
<pre>
POST <pwm:context/>/public/rest/signing/form HTTP/1.1
Accept-Language: en
Accept: application/json
Content-Type: application/json
Authorization: Basic c2VjcmV0MTpwYXNzd29yZA==

{
  "givenName":"John",
  "sn":"Doe"
}
</pre>
                            </td>
                        </tr>
                        <tr>
                            <td class="key">Response</td>
                            <td class="exampleTD">
<pre>
{
  "error": false,
  "errorCode": 0,
  "data": "H4sIAAAAAAAAAAFxAI7_UFdNLkdDTTEQz1yn2zvAMXknwMu2NNpJLpkD4uwWmFQXq80VZH4cxAXYXLmWq05rNTaBJJ3D8PVLElZA8a_XSdzltDku0kwIkmwTW0D7EYXwFId0EA-mTGygsFuLF--BJLxcwyw5jKkAO2miy-w_f2rPiSaycQAAAA=="
}
</pre>
                            </td>
                        </tr>
                    </table>
                </td>
            </tr>
        </table>
    <br/>
    <h1><a id="rest-statistics">Rest Service - statistics</a></h1>
    <table>
        <tr>
            <td class="key" style="width:50px">url</td>
            <td><a href="<pwm:context/>/public/rest/statistics"><pwm:context/>/public/rest/statistics</a></td>
        </tr>
        <tr>
        </tr>
        <tr>
            <td class="key" style="width:50px">GET Method</td>
            <td>
                <table>
                    <tr>
                        <td class="key">Description</td>
                        <td>Read system statistics</td>
                    </tr>
                    <tr>
                        <td class="key">Authentication</td>
                        <td>Not Required</td>
                    </tr>
                    <tr>
                        <td class="key">Accept-Language Header</td>
                        <td>en
                            <br/>
                            <i>The request will be processed in the context of the specified language</i>
                        </td>
                    </tr>
                    <tr>
                        <td class="key">Accept Header</td>
                        <td>
                            application/json
                        </td>
                    </tr>
                    <tr>
                        <td class="key">Content-Type Header</td>
                        <td>
                            application/json
                            <br/>
                            application/x-www-form-urlencoded
                        </td>
                    </tr>
                    <tr>
                        <td class="key">Parameter statKey</td>
                        <td>
                            <br/>
                            <i>Statistic key to return</i>
                        </td>
                    </tr>
                    <tr>
                        <td class="key">Parameter statName</td>
                        <td>
                            <br/>
                            <i>Name of statistic to retrieve.</i>
                        </td>
                    </tr>
                    <tr>
                        <td class="key">Parameter days</td>
                        <td>
                            days=20
                            <br/>
                            <i>Number of days to return statistics for.</i>
                        </td>
                    </tr>
                </table>
                <table style="max-width: 100%">
                    <tr>
                        <td class="title" style="font-size: smaller" colspan="2">Example 1</td>
                    </tr>
                    <tr>
                        <td class="key">Request</td>
                <td class="exampleTD">
<pre>
GET <pwm:context/>/public/rest/statistics?days=14&statName=PASSWORD_CHANGES HTTP/1.1
Accept-Language: en
Accept: application/json
Authorization: Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ==
</pre>
                        </td>
                    </tr>
                    <tr>
                        <td class="key">Response</td>
                <td class="exampleTD">
<pre>
{
   "error":false,
   "errorCode":0,
   "data":{
      "EPS":{
         "AUTHENTICATION_DAY":"0.000",
         "AUTHENTICATION_HOUR":"0.000",
         "AUTHENTICATION_MINUTE":"0.000",
         "AUTHENTICATION_TOP":"100",
         "INTRUDER_ATTEMPTS_DAY":"0.000",
         "INTRUDER_ATTEMPTS_HOUR":"0.000",
         "INTRUDER_ATTEMPTS_MINUTE":"0.000",
         "INTRUDER_ATTEMPTS_TOP":"100",
         "PASSWORD_CHANGES_DAY":"0.000",
         "PASSWORD_CHANGES_HOUR":"0.000",
         "PASSWORD_CHANGES_MINUTE":"0.000",
         "PASSWORD_CHANGES_TOP":"100",
         "PWMDB_READS_DAY":"0.001",
         "PWMDB_READS_HOUR":"0.020",
         "PWMDB_READS_MINUTE":"0.492",
         "PWMDB_READS_TOP":"1800",
         "PWMDB_WRITES_DAY":"0.004",
         "PWMDB_WRITES_HOUR":"0.083",
         "PWMDB_WRITES_MINUTE":"2.152",
         "PWMDB_WRITES_TOP":"7800"
      },
      "nameData":{
         "Mar 19":"0",
         "Mar 17":"0",
         "Mar 18":"0",
         "Mar 20":"0",
         "Mar 21":"1",
         "Mar 22":"0",
         "Mar 23":"0",
         "Mar 15":"0",
         "Mar 24":"0",
         "Mar 16":"0",
         "Mar 25":"0",
         "Mar 26":"2",
         "Mar 14":"0",
         "Mar 27":"4"
      }
   }
}
</pre>
                        </td>
                    </tr>
                </table>
            </td>
        </tr>
    </table>
    <br/>
    <h1><a id="rest-status">Rest Service - status</a></h1>
    <table >
        <tr>
            <td class="key" style="width:50px">url</td>
            <td><a href="<pwm:context/>/public/rest/status"><pwm:context/>/public/rest/status</a></td>
        </tr>
        <tr>
            <td class="key" style="width:50px">GET Method</td>
            <td>
                <table>
                    <tr>
                        <td class="key">Description</td>
                        <td>Read users status data</td>
                    </tr>
                    <tr>
                        <td class="key">Authentication</td>
                        <td>Required</td>
                    </tr>
                    <tr>
                        <td class="key">Accept-Language Header</td>
                        <td>en
                            <br/>
                            <i>The request will be processed in the context of the specified language</i>
                        </td>
                    </tr>
                    <tr>
                        <td class="key">Accept Header</td>
                        <td>application/json</td>
                    </tr>
                    <tr>
                        <td class="key">Parameter username</td>
                        <td>
                            username=user1234
                            <br/>
                            <i>Optional username or ldap DN of a user of which to read the status.</i>
                        </td>
                    </tr>
                </table>
                <table style="max-width: 100%">
                    <tr>
                        <td class="title" style="font-size: smaller" colspan="2">Example 1</td>
                    </tr>
                    <tr>
                        <td class="key">Request</td>
                <td class="exampleTD">
<pre>
GET <pwm:context/>/public/rest/status HTTP/1.1
Accept: application/json
Accept-Language: fr
Authorization: Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ==
</pre>
                        </td>
                    </tr>
                    <tr>
                        <td class="key">Response</td>
                <td class="exampleTD">
<pre>
{
  "error": false,
  "errorCode": 0,
  "data": {
    "userDN": "cn=user,ou=users,o=data",
    "userID": "user",
    "userEmailAddress": "user@example.com",
    "passwordLastModifiedTime": "Apr 1, 1970 6:59:43 PM",
    "requiresNewPassword": false,
    "requiresResponseConfig": false,
    "requiresUpdateProfile": false,
    "requiresInteraction": true,
    "passwordStatus": {
      "expired": false,
      "preExpired": false,
      "violatesPolicy": false,
      "warnPeriod": false
    },
    "passwordPolicy": {
      "MaximumNumeric": "0",
      "MinimumSpecial": "0",
      "AllowLastCharSpecial": "true",
      "ADComplexity": "false",
      "RegExNoMatch": ".*%.*",
      "AllowSpecial": "true",
      "MaximumSpecial": "0",
      "MinimumLowerCase": "0",
      "MinimumUnique": "0",
      "MinimumNumeric": "0",
      "MinimumLength": "2",
      "DisallowedValues": "test\npassword",
      "CaseSensitive": "true",
      "RegExMatch": "",
      "DisallowCurrent": "true",
      "AllowFirstCharSpecial": "true",
      "MinimumLifetime": "0",
      "ExpirationInterval": "0",
      "UniqueRequired": "false",
      "MaximumSequentialRepeat": "0",
      "AllowNumeric": "true",
      "AllowFirstCharNumeric": "true",
      "EnableWordlist": "false",
      "MaximumLength": "64",
      "DisallowedAttributes": "sn\ncn\ngivenName",
      "AllowLastCharNumeric": "true",
      "PolicyEnabled": "true",
      "MaximumUpperCase": "0",
      "MinimumUpperCase": "0",
      "ChangeMessage": "sdsadasd\ndsadsadsa\nddsadsa\ndsadsad\nsadasda",
      "MaximumLowerCase": "0"
    },
    "passwordRules": [
      "Le mot de passe est sensible à la casse",
      "Doit comporter au moins 2 caractère",
      "Ne peut contenir l’une des valeurs suivantes:  test password",
      "Ne doit pas contenir une partie de votre nom ou  identifiant."
    ]
  }
}
</pre>
                        </td>
                    </tr>
                </table>
            </td>
        </tr>
    </table>
    <br/>
    <h1><a id="rest-verifyotp">Rest Service - verifyotp</a></h1>
    <table>
        <tr>
            <td class="key" style="width:50px">url</td>
            <td><a href="<pwm:context/>/public/rest/verifyotp"><pwm:context/>/public/rest/verifyotp</a></td>
        </tr>
        <tr>
        </tr>
        <tr>
            <td class="key" style="width:50px">POST Method</td>
            <td>
                <table>
                    <tr>
                        <td class="key">Description</td>
                        <td>Validate supplied one time password against a user's stored secret.</td>
                    </tr>
                    <tr>
                        <td class="key">Authentication</td>
                        <td>Required</td>
                    </tr>
                    <tr>
                        <td class="key">Accept Header</td>
                        <td>
                            application/json
                        </td>
                    </tr>
                    <tr>
                        <td class="key">Content-Type Header</td>
                        <td>
                            application/json
                        </td>
                    </tr>
                    <tr>
                        <td class="key">Parameter username</td>
                        <td>
                            username=user1234
                            <br/>
                            <i>Optional username or ldap DN of a user on which to verify the one time password.</i>
                        </td>
                    </tr>
                    <tr>
                        <td class="key">Parameter token</td>
                        <td>
                            token=123456
                            <br/>
                            <i>One time password to be verified.</i>
                        </td>
                    </tr>
                </table>
                <table style="max-width: 100%">
                    <tr>
                        <td class="title" style="font-size: smaller" colspan="2">Example 1</td>
                    </tr>
                    <tr>
                        <td class="key">Request</td>
                <td class="exampleTD">
<pre>
POST <pwm:context/>/public/rest/verifyotp HTTP/1.1
Accept-Language: en
Accept: application/json
Content-Type: application/json
Authorization: Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ==

{
   "token": 123456
}
</pre>
                        </td>
                    </tr>
                    <tr>
                        <td class="key">Response</td>
                <td class="exampleTD">
<pre>
{
    "error": false,
    "errorCode": 0,
    "successMessage": "The operation has been successfully completed.",
    "data": false
}
</pre>
                        </td>
                    </tr>
                </table>
            </td>
        </tr>
    </table>
    <br/>
    <h1><a id="rest-verifyresponses">Rest Service - verifyresponses</a></h1>
    <table>
        <tr>
            <td class="key" style="width:50px">url</td>
            <td><a href="<pwm:context/>/public/rest/verifyresponses"><pwm:context/>/public/rest/verifyresponses</a></td>
        </tr>
        <tr>
        </tr>
        <tr>
            <td class="key" style="width:50px">POST Method</td>
            <td>
                <table>
                    <tr>
                        <td class="key">Description</td>
                        <td>Validate supplied challenge response answers against a user's stored responses.  <i>Note this service will
                            not work properly if the user's responses are stored only in the NMAS repository.</i></td>
                    </tr>
                    <tr>
                        <td class="key">Authentication</td>
                        <td>Required</td>
                    </tr>
                    <tr>
                        <td class="key">Accept-Language Header</td>
                        <td>en
                            <br/>
                            <i>The request will be processed in the context of the specified language</i>
                        </td>
                    </tr>
                    <tr>
                        <td class="key">Accept Header</td>
                        <td>
                            application/json
                        </td>
                    </tr>
                    <tr>
                        <td class="key">Content-Type Header</td>
                        <td>
                            application/json
                        </td>
                    </tr>
                    <tr>
                        <td class="key">Parameter username</td>
                        <td>
                            <table>
                                <tr><td>Name</td><td>username</td></tr>
                                <tr><td>Required</td><td>Optional</td></tr>
                                <tr><td>Location</td><td>query string or json body</td></tr>
                                <tr><td>Value</td><td>Optional username or ldap DN of a user on which to verify the responses</td></tr>
                                <tr><td>Default</td><td>Authenticating user (if LDAP)</td></tr>
                            </table>
                        </td>
                    </tr>
                    <tr>
                        <td class="key">Parameter challenges</td>
                        <td>
                            <table>
                                <tr><td>Name</td><td>challenges</td></tr>
                                <tr><td>Required</td><td>Required</td></tr>
                                <tr><td>Location</td><td>json body</td></tr>
                                <tr><td>Value</td><td>List of challenge objects including answers with an answerText property.  Retrieve challenge objects using
                                    the challenges service to discover the proper object formatting.  The question object data must match
                                    precisely the question object received from the challenges service so that the answer can be applied to
                                    the correct corresponding question.  This includes each parameter of the question object.</td></tr>
                                <tr><td>Default</td><td>n/a</td></tr>
                            </table>
                        </td>
                    </tr>
                </table>
                <table style="max-width: 100%">
                    <tr>
                        <td class="title" style="font-size: smaller" colspan="2">Example 1</td>
                    </tr>
                    <tr>
                        <td class="key">Request</td>
                <td class="exampleTD">
<pre>
POST <pwm:context/>/public/rest/verifyresponses HTTP/1.1
Accept-Language: en
Accept: application/json
Content-Type: application/json
Authorization: Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ==

{
   "username":"user1234",
   "challenges":[
      {
         "challengeText":"What is the name of the main character in your favorite book?",
         "minLength":4,
         "maxLength":200,
         "adminDefined":true,
         "required":false,
         "answer":{
            "answerText":"Answer 1"
         }
      },
      {
         "challengeText":"What is your least favorite film of all time?",
         "minLength":4,
         "maxLength":200,
         "adminDefined":true,
         "required":false,
         "answer":{
            "answerText":"Answer 2"
         }
      }
   ]
}
</pre>
                        </td>
                    </tr>
                    <tr>
                        <td class="key">Response</td>
                <td class="exampleTD">
<pre>
{
    "error": false,
    "errorCode": 0,
    "successMessage": "The operation has been successfully completed.",
    "data": true
}
</pre>
                        </td>
                    </tr>
                </table>
            </td>
        </tr>
    </table>
</div>
</div>
<div class="push"></div>
</div>
<%@ include file="/WEB-INF/jsp/fragment/footer.jsp" %>
</body>
</html>
