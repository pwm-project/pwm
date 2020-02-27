<%--
 ~ Password Management Servlets (PWM)
 ~ http://www.pwm-project.org
 ~
 ~ Copyright (c) 2006-2009 Novell, Inc.
 ~ Copyright (c) 2009-2019 The PWM Project
 ~
 ~ Licensed under the Apache License, Version 2.0 (the "License");
 ~ you may not use this file except in compliance with the License.
 ~ You may obtain a copy of the License at
 ~
 ~     http://www.apache.org/licenses/LICENSE-2.0
 ~
 ~ Unless required by applicable law or agreed to in writing, software
 ~ distributed under the License is distributed on an "AS IS" BASIS,
 ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 ~ See the License for the specific language governing permissions and
 ~ limitations under the License.
--%>
<%--
       THIS FILE IS NOT INTENDED FOR END USER MODIFICATION.
       See the README.TXT file in WEB-INF/jsp before making changes.
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
        width:500px;
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
            <li><a href="#rest-forgottenpassword">Rest Service - forgottenpassword</a></li>
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
            <tr><td>error</td><td>boolean</td><td>false if the operation was successfull</td></tr>
            <tr><td>errorCode</td><td>four-digit number</td><td>application error code</td></tr>
            <tr><td>errorMessage</td><td>string</td><td>Localized error message string</td></tr>
            <tr><td>errorDetail</td><td>string</td><td>Error Number, Error ID and debugging detail message if any, English only</td></tr>
            <tr><td>successMessage</td><td>string</td><td>Localized success message string</td></tr>
            <tr><td>data</td><td>object</td><td>Requested data</td></tr>
        </table>
        <br/>
        <h2>Example REST Client</h2>
        <a href="../examples/rest-client-example.jsp">End User Module Example REST Client</a>
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
DELETE <pwm:context/>/public/rest/challenges?username=user1234 HTTP/1.1
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
        <h1><a id="rest-forgottenpassword">Rest Service - forgottenpassword</a></h1>
        <table >
            <tr>
                <td class="key" style="width:50px">url</td>
                <td><a href="<pwm:context/>/public/rest/forgottenpassword"><pwm:context/>/public/rest/forgottenpassword</a></td>
            </tr>
            <tr>
            </tr>
            <tr>
                <td class="key" style="width:50px">POST Method</td>
                <td>
                    <table>
                        <tr>
                            <td class="key">Description</td>
                            <td>
                                A multi-stage endpoint suitable for anonymous clients to implement a forgotten password module.
                                Clients will call this service repeatedly until the process is completed.  The following stages will be
                                processed (some may be repeated or omitted based on policy and configuration):
                                <ul>
                                    <li>IDENTIFICATION</li>
                                    <li>METHOD_CHOICE</li>
                                    <li>TOKEN_CHOICE</li>
                                    <li>VERIFICATION</li>
                                    <li>ACTION_CHOICE</li>
                                    <li>NEW_PASSWORD</li>
                                    <li>COMPLETE</li>
                                </ul>
                                <p>Each invocation of this service (except the first) requires a state parameter and form data.  The state parameter is
                                    received from the previous invocation (or empty on the first call).  It is the client's responsibility to maintain
                                    this state value throughout the process.  If the state is invalid or expired, a new state will be generated by the server and the process restarted.
                                The client can clear the state at any time to restart the sequence.  Once the COMPLETE stage is reached, no further steps are possible
                                and a new sequence will require discarding the state.</p>
                                <p>Each response from the service will include form data with one or more rows that will guide the client to create a UI for
                                the user.  Form fields are generally of type 'text' or 'select'.  In the case of 'select' form fields, a 'selectOptions' value
                                will include the possible values for the row.  The next request to the server should include the state and the form data values
                                for each form field.  A form label and message is also included for display to the user.</p>
                                <p>The response from the service will include additional data of the 'stage' and 'method' (if appropriate).  These values
                                are informational and intended to aid the client in displaying an appropriate UI to the end user.</p>
                                <p>Errors can indicate syntax, operational, or internal errors.  In most cases form data will also be included along with
                                the error so that the user has an opportunity to attempt the operation again.</p>
                                <p>See also: <a href="../examples/rest-client-example.jsp">End User Module Example REST Client</a></p>
                            </td>
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
                            <td>application/json</td>
                        </tr>
                        <tr>
                            <td class="key">Content-Type Header</td>
                            <td>
                                application/json
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
POST <pwm:context/>/public/rest/forgottenpassword HTTP/1.1
Accept-Language: en
Accept: application/json
Content-Type: application/json

{}
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
      "stage":"IDENTIFICATION",
      "form":{
         "formRows":[
            {
               "name":"cn",
               "minimumLength":1,
               "maximumLength":64,
               "type":"text",
               "required":true,
               "label":"Username",
               "selectOptions":{
               }
            }
         ],
         "label":"Forgotten Password",
         "message":"If you have forgotten your password, follow the prompts to reset your password."
      },
      "state":"NzJKin4agg3QXx9q0EBSeZ1UTTYhxVCTjwshrkyj.H4sIAAAAAAAAAAEDAfz-UFdNLkdDTTEQ5Rd8CuZrpUD4VXcp8aHuHE5FvUNrG9R_uILHRhxnt30XjKX_eKe1qClcM_I-SoKabNX6xIrJgkXx2N0Ic64uTKriexiGihMJ1SmNrG6YfKVkhtAPLsQieiqcfjgJTAnVkSlol4FFzOqgqVvpx8FIJz6TACZYG2l4aZ3JXfCGhu32Uq6iKVHkDs_skkndZmLIQsb4sLbUN-JVVwxwyzyzyqNccSHyeOPoWaIWeenjjzBa_lfGx0SM5U3Y_g3taMBx2TwyZzj2QOtQ5eUyaYQ_4P-SyGL5tQZCiXgb8FDwhZvHLuUKw289vWX8vBg7TX5cUj3Ki348AQpltLe1K6kDAQAA"
   }
}</pre>
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
POST <pwm:context/>/public/rest/forgottenpassword HTTP/1.1
Accept-Language: en
Accept: application/json
Content-Type: application/json

{
   "state":"yKtUhNohePx3zE02SmnWuJqArRlrxeb7jwsh9758.H4sIAAAAAAAAAAEDAfz-UFdNLkdDTTEQ5Rd8CuZrpUD4VXcp8aHuFQglychy7rHCsdBpaizdfE09tI-hs4aoP_GNo6bPahgM1vopVprxz_zsY0uAHxQvVdzLm79AxDAgDobrsGVxNq2GvGjE8DkrjT1SBNjdVmTMm6d80toJgMdCvjJvaMiLmDbiRI80blo5jxjwNXisTZcJAqJGxc-bbvtdgzl2sGmJijc2e4eAX4zf77b2BsyOnTfwkNdXMXlR4jnjis0yWeXK_WI0YCoOwWnE96VkQ1Rl9feBHfWLptuOAD2qDZ_rqs5k8ImUztPBmm_XHzs1K3giQE3jxOK3IxFT-onSBY-OMiCCUNaX7uuPhWRfsxIDAQAA",
   "form":{
      "cn":"asmith"
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
   "data":{
      "stage":"VERIFICATION",
      "method":"ATTRIBUTES",
      "form":{
         "formRows":[
            {
               "name":"challenge0",
               "minimumLength":6,
               "maximumLength":199,
               "type":"text",
               "required":false,
               "label":"What is the name of the main character in your favorite books?",
               "selectOptions":{

               }
            },
            {
               "name":"challenge1",
               "minimumLength":6,
               "maximumLength":199,
               "type":"text",
               "required":false,
               "label":"What is the name of your favoraite teacher?",
               "selectOptions":{

               }
            }
         ],
         "label":"Forgotten Password",
         "message":"Please answer the following questions. If you answer these questions correctly, you will then be able to reset your password."
      },
      "state":"yKtUhNohePx3zE02SmnWuJqArRlrxeb7jwsh9758.H4sIAAAAAAAAAAGcBWP6UFdNLkdDTTEQ5Rd8CuZrpUD4VXcp8aHuFgQsvTdvwJK3sRGhSZiSN9iUjeYDGBOmy0YsA573uvLxh1rUIPpJrFnmlbKKLUrM0JdzrktheFrdAvEhPKz3yrMGO7gmNqAcWToXxr6a81EBFRyPrOn3Yb2Jo7D9a-Pm8MFFZhNqrlsgqtH_UVvdC2qe-unxE54GT2GnNooL6SN2FUdskfvt7Jr8nld4hI9bhneRKRu9hXT8xdJ-fAXI7MYvGZtLs-JamVBgMrddVmhwxC3ehWEWslVKQqPpAx_3fAtdnum2IfcMuZAntTL3bypQ8HZ8Rad76uhDKiy2rngIVvd3qasslzxM1PhkAgPPT2aUsWULTxAPZYXMW6lX5ajSwV4rW48X5oN43g1PW-WLWMMmEn6kM3LbHRnWi7mVN1K7t2JvNqXxHGLQUQOfkCa7A4CVi2hoiCU6Cw88yXtRSA6unpdFSGziAH7EfLoYuG0jgqLHls_L1iJVb3ZVaSGZ0vHpzDuVqhbOq7p3NII7wtEIvDGlejp546f8zAwFIeWqX7UwsdRH3dOZ54UJkbLuV3Gm6q-E81ED_ZgZznuxZmZAenUYyo_qOO9XYGjKWDN3daCamkh2ybs7-HymLct2NsAR_1eLpdxeIQ4-TCa0pjA7waRqNwyMSlcrQfwXpOgvdvUBuIyK7hel3wvXkbF_zcPTCzrStb2f6XNcgEJ-p6zoN5uyTKV96eYzEQTYMvJkabq49aeqn3NItRJvOHP0yXWr3B1il1wdLD_6rK_CaUCunKUuTj5CF_yea7U_Wm7crGYgtFOcrcBlzGn2omvFSoJw3Sx79XlimVBNQLeOj37hTlhVmQ-6uaVmJk4ozfgPaPTUY3TIgLJSFtOkJb4AV6XZeFYb1dWgjdqW0TuurHOa04-JsXP-KhrkkX_b5ej8Wf17HdJyHXSf731tnp0QVYfoyIvsU0j6q1ZXmSxpoL7RrFIKx11tv6TT355vFtgkiXdXJKgNeLffil85p9quv8YCYW5hck35O3V41pEDE6BZ2RpLjU3akZnAqZSydieICDaznjdyxL-8qd1AQuZQwWntige1GrfHnGGEVvNFWcLTscdsbSnp8tzE2QNpFw45RAFgX5xJXfrla7ioDbFocvixqnhrKYA9Dr3IYYnyuOhx1U1Md5n7cGrcGc8i1tcgvqH1AclHbJZuP1jRAE009t72nPG45pLOgVoHF4hTHkxnvIId7sNsJxetcYP62gffgi3rdwmxJNcVeMZkKhJWA-syyPgcObRxK3s4OSf1wsyQTQ_-orY54izTLLSVMLG70MczUHx3Vm0er8131OMhtP0SRai0NcCT9gV-R4ig6P47EEu2hP1JBd5A9LWPpRSYaSIAL7K4gDhf-reHqH_V3-Q6MUDrHQJDtZAyYADE3qY7mWu2Xkw54khNxlEsgu3BpJmGFB_D6isG_QPbXtkjLgyanw5tcAUtljWnNeAN98B-sTrvUBP8UGSSJwLfHuWh7-HDskb9CWVxLEBf2mlphd4Cx-zXwMy5vS181NYSjsMfzHGCck3Jo5ynaQYa0KMAFw3wGkGkFssI0KTB3bUTW5lCM4GcQsXgPtI0f1G9uye6TVdsdgVVTzKUyaBlTrH_iel9oRwk2iJDVRiz4Sg9iLcw_DFK5Man4AOfuqdRvHqlc7DJ0nhDyxYcnIQc_yPPZA9ic5qoFCv5lMg_ABerHNwutg0Gdq6Ff4yER52KekY39bgHtJPpjs8W6ORUa_6G-HVGsCZeg-RQks9iq6OTgmWxzv4vdto7QP6snvaDGLmgInV-Ie1_g3g4fgcMRKFxy8q-jGYU9Y_TLtc0y47voLj8H-auWv9mAJo0Ztp43aPs7XujpmTJyqPdPBzwxl_Ey6sF9rzzjuchz1Avnf1_0B4JR8UynAUAAA=="
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
                            <td>Required unless otherwise configured</td>
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
                            </td>
                        </tr>
                        <tr>
                            <td class="key">Parameter days</td>
                            <td>
                                <table>
                                    <tr><td>Name</td><td>days</td></tr>
                                    <tr><td>Required</td><td>Optional</td></tr>
                                    <tr><td>Location</td><td>query string</td></tr>
                                    <tr><td>Value</td><td>Number of history days to return in result.</td></tr>
                                    <tr><td>Default</td><td>7</td></tr>
                                </table>
                            </td>
                        </tr>
                        <tr>
                            <td class="key">Parameter version</td>
                            <td>
                                <table>
                                    <tr><td>Name</td><td>helpdesk</td></tr>
                                    <tr><td>Required</td><td>Optional</td></tr>
                                    <tr><td>Location</td><td>query string</td></tr>
                                    <tr><td>Value</td><td>Number indicating API version to use.</td></tr>
                                    <tr><td>Default</td><td>2</td></tr>
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
GET <pwm:context/>/public/rest/statistics?days=30 HTTP/1.1
Accept-Language: en
Accept: application/json
Authorization: Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ==
</pre>
                            </td>
                        </tr>
                        <tr>
                            <td class="key">Response</td>
                            <td>
                                <div class="exampleTD">
<pre>
{
   "error":false,
   "errorCode":0,
   "data":{
     "error": false,
     "errorCode": 0,
     "data": {
       "labels": [
         {
           "name": "ACTIVATED_USERS",
           "label": "Activated Users",
           "type": "INCREMENTER",
           "description": "Number of users that have successfully completed the user activation process."
         }
       ],
       "eventRates": [
         {
           "name": "AUTHENTICATION_DAY",
           "value": "3.000"
         }
       ],
       "current": [
         {
           "name": "ACTIVATED_USERS",
           "value": "0"
         }
       ],
       "cumulative": [
         {
           "name": "ACTIVATED_USERS",
           "value": "15"
         }
       ],
       "history": [
         {
           "name": "DAILY_2018_206",
           "date": "2018-07-25",
           "year": 2018,
           "month": 6,
           "day": 25,
           "daysAgo": 0,
           "data": [
             {
               "name": "ACTIVATED_USERS",
               "value": "0"
             }
           ]
         }
       ]
     }
   }
}
</pre>
                                </div>
                                <div>
                            <span class="footnote">
                                Actual response is much larger, this example is truncated to show only
                                one instance of each data element.
                            </span></div>
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
