<%--
  ~ Password Management Servlets (PWM)
  ~ http://code.google.com/p/pwm/
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2012 The PWM Project
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

<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<body onload="pwmPageLoadHandler()" class="nihilo">
<style type="text/css">
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
<div id="centerbody">
<% if ("true".equals(request.getParameter("forwardedFromRestServer"))) { %>
<div class="message message-info">
    It appears you have attempted to request a web service url, however the <i>Accept</i> header was not specified or included a value of <i>html</i>, so you are
    being shown this page instead.
</div>
<% } %>
<div data-dojo-type="dijit.layout.TabContainer" style="width: 100%; height: 100%;" data-dojo-props="doLayout: false, persist: true">
<div data-dojo-type="dijit.layout.ContentPane" title="Introduction">
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
</div>
<div data-dojo-type="dijit.layout.ContentPane" title="challenges">
<table >
<tr>
    <td class="key" style="width:50px">url</td>
    <td><a href="<%=request.getContextPath()%>/public/rest/challenges"><%=request.getContextPath()%>/public/rest/challenges</a></td>
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
        <td class="key">Content-Type Header</td>
        <td>application/x-www-form-urlencoded</td>
    </tr>
    <tr>
        <td class="key">Parameter answers</td>
        <td>
            answers=true
            <br/>
            <i>Boolean indicating if answers (in whatever format stored) should be returned in the result.
            </i>
        </td>
    </tr>
    <tr>
        <td class="key">Parameter helpdesk</td>
        <td>
            helpdesk=true
            <br/>
            <i>Boolean indicating if helpdesk answers should be returned in the result.</i>
        </td>
    </tr>
    <tr>
        <td class="key">Parameter username</td>
        <td>
            <i>Optional username or userDN of user to whom the responses will be read.</i>
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
GET <%=request.getContextPath()%>/public/rest/challenges HTTP/1.1
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
GET <%=request.getContextPath()%>/public/rest/challenges?answers=true&helpdesk=true HTTP/1.1
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
                <td class="key">JSON Parameter username</td>
                <td>
                    <i>Optional username or userDN of user to whom the responses will be written.</i>
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
POST <%=request.getContextPath()%>/public/rest/challenges HTTP/1.1
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
POST <%=request.getContextPath()%>/public/rest/challenges HTTP/1.1
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
                <td class="key">Content-Type Header</td>
                <td>application/x-www-form-urlencoded</td>
            </tr>
            <tr>
                <td class="key">Parameter username</td>
                <td>
                    <i>Optional username or userDN of user to whom the responses will be deleted.</i>
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
DELETE <%=request.getContextPath()%>/public/rest/challenges HTTP/1.1
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
</div>
<div data-dojo-type="dijit.layout.ContentPane" title="checkpassword">
    <table >
        <tr>
            <td class="key" style="width:50px">url</td>
            <td><a href="<%=request.getContextPath()%>/public/rest/checkpassword"><%=request.getContextPath()%>/public/rest/checkpassword</a></td>
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
                        <td><i>password value</i></td>
                    </tr>
                    <tr>
                        <td class="key">Parameter password2</td>
                        <td><i>password value confirmation</i></td>
                    </tr>
                    <tr>
                        <td class="key">Parameter username</td>
                        <td><i>Optional username or userDN of user's password policy to check against</i></td>
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
POST <%=request.getContextPath()%>/public/rest/checkpassword HTTP/1.1
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
POST <%=request.getContextPath()%>/public/rest/checkpassword HTTP/1.1
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
</div>
<div data-dojo-type="dijit.layout.ContentPane" title="health">
    <table >
        <tr>
            <td class="key" style="width:50px">url</td>
            <td><a href="<%=request.getContextPath()%>/public/rest/health"><%=request.getContextPath()%>/public/rest/health</a></td>
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
GET <%=request.getContextPath()%>/public/rest/health HTTP/1.1
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
            "detail":"PWM is currently in <b>configuration</b> mode. Anyone accessing this site can modify the configuration without authenticating. When ready, lock the configuration to secure this installation."
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
GET <%=request.getContextPath()%>/public/rest/health&refreshImmediate=true HTTP/1.1
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
</div>
<div data-dojo-type="dijit.layout.ContentPane" title="profile">
    <table >
        <tr>
            <td class="key" style="width:50px">url</td>
            <td><a href="<%=request.getContextPath()%>/public/rest/profile"><%=request.getContextPath()%>/public/rest/profile</a></td>
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
GET <%=request.getContextPath()%>/public/rest/profile HTTP/1.1
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
POST <%=request.getContextPath()%>/public/rest/profile HTTP/1.1
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
</div>
<div data-dojo-type="dijit.layout.ContentPane" title="randompassword">
    <table>
        <tr>
            <td class="key" style="width:50px">url</td>
            <td><a href="<%=request.getContextPath()%>/public/rest/randompassword"><%=request.getContextPath()%>/public/rest/randompassword</a></td>
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
GET <%=request.getContextPath()%>/public/rest/randompassword HTTP/1.1
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
GET <%=request.getContextPath()%>/public/rest/randompassword HTTP/1.1
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
</div>
<div data-dojo-type="dijit.layout.ContentPane" title="setpassword">
    <table>
        <tr>
            <td class="key" style="width:50px">url</td>
            <td><a href="<%=request.getContextPath()%>/public/rest/setpassword"><%=request.getContextPath()%>/public/rest/setpassword</a></td>
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
POST <%=request.getContextPath()%>/public/rest/setpassword HTTP/1.1
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
</div>
<div data-dojo-type="dijit.layout.ContentPane" title="statistics">
    <table>
        <tr>
            <td class="key" style="width:50px">url</td>
            <td><a href="<%=request.getContextPath()%>/public/rest/statistics"><%=request.getContextPath()%>/public/rest/statistics</a></td>
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
GET <%=request.getContextPath()%>/public/rest/statistics?days=14&statName=PASSWORD_CHANGES HTTP/1.1
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
</div>
<div data-dojo-type="dijit.layout.ContentPane" title="status">
    <table >
        <tr>
            <td class="key" style="width:50px">url</td>
            <td><a href="<%=request.getContextPath()%>/public/rest/status"><%=request.getContextPath()%>/public/rest/status</a></td>
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
GET <%=request.getContextPath()%>/public/rest/status HTTP/1.1
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
</div>
<div data-dojo-type="dijit.layout.ContentPane" title="verifyresponses">
    <table>
        <tr>
            <td class="key" style="width:50px">url</td>
            <td><a href="<%=request.getContextPath()%>/public/rest/verifyresponses"><%=request.getContextPath()%>/public/rest/verifyresponses</a></td>
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
                            username=user1234
                            <br/>
                            <i>Optional username or ldap DN of a user on which to verify the responses.</i>
                        </td>
                    </tr>
                    <tr>
                        <td class="key">Parameter challenges</td>
                        <td>
                            challenges=[list of challenges]
                            <br/>
                            <i>List of challenge objects including answers with an answerText property.  Retrieve challenge objects using
                                the challenges service to discover the proper object formatting.</i>
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
POST <%=request.getContextPath()%>/public/rest/verifyresponses HTTP/1.1
Accept-Language: en
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
</div>
<div class="push"></div>
</div>
<script type="text/javascript">
    PWM_GLOBAL['startupFunctions'].push(function(){
        PWM_GLOBAL['idle_suspendTimeout'] = true;
        require(["dojo/parser","dojo/domReady!","dijit/layout/TabContainer","dijit/layout/ContentPane","dijit/Dialog"],function(dojoParser){
            dojoParser.parse();
        });
        require(["dojo/parser","dijit/TitlePane"],function(dojoParser){
            dojoParser.parse();
        });
    });
</script>
<%@ include file="/WEB-INF/jsp/fragment/footer.jsp" %>
</body>
</html>
