<%--
 ~ Password Management Servlets (PWM)
 ~ http://www.pwm-project.org
 ~
 ~ Copyright (c) 2006-2009 Novell, Inc.
 ~ Copyright (c) 2009-2020 The PWM Project
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


<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<body class="nihilo">
<div id="wrapper">
    <jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_URLReference"/>
    </jsp:include>
    <div id="centerbody">
        <h1 id="page-content-title"><pwm:display key="Title_URLReference" bundle="Admin" displayIfMissing="true"/></h1>
        <%@ include file="fragment/admin-nav.jsp" %>
        <br/>
        <br/>
        <table>
            <tr>
                <td colspan="2" class="title">Public URLs</td>
            </tr>
            <tr>
                <td class="key">Application</td>
                <td><a href="<pwm:context/>"><pwm:context/></a></td>
            </tr>
            <tr>
                <td class="key">Public Menu</td>
                <td><a href="<pwm:context/><%=PwmConstants.URL_PREFIX_PUBLIC%>"><pwm:context/><%=PwmConstants.URL_PREFIX_PUBLIC%></a></td>
            </tr>
            <tr>
                <td  class="key">Forgotten Password</td>
                <td><a href="<pwm:context/><%=PwmServletDefinition.ForgottenPassword.servletUrl()%>"><pwm:context/><%=PwmServletDefinition.ForgottenPassword.servletUrl()%></a></td>
            </tr>
            <tr>
                <td class="key">Activate User</td>
                <td><a href="<pwm:context/><%=PwmServletDefinition.ActivateUser.servletUrl()%>"><pwm:context/><%=PwmServletDefinition.ActivateUser.servletUrl()%></a></td>
            </tr>
            <tr>
                <td class="key">New User Registration</td>
                <td><a href="<pwm:context/><%=PwmServletDefinition.NewUser.servletUrl()%>"><pwm:context/><%=PwmServletDefinition.NewUser.servletUrl()%></a></td>
            </tr>
            <% for (final String id : JspUtility.getPwmRequest(pageContext).getConfig().getNewUserProfiles().keySet()) { %>
            <tr>
                <td class="key">New User Registration "<%=id%>" profile</td>
                <td><a href="<pwm:context/><%=PwmServletDefinition.NewUser.servletUrl()%>"><pwm:context/><%=PwmServletDefinition.NewUser.servletUrl()%>/profile/<%=id%></a></td>
            </tr>
            <% } %>
            <tr>
                <td class="key">Public People Search </td>
                <td><a href="<pwm:context/><%=PwmServletDefinition.PublicPeopleSearch.servletUrl()%>"><pwm:context/><%=PwmServletDefinition.PublicPeopleSearch.servletUrl()%></a></td>
            </tr>
        </table>
        <br/>
        <br/>
        <table>
            <tr>
                <td colspan="2" class="title">Authenticated URLs</td>
            </tr>
            <tr>
                <td class="key">Logged-In Menu</td>
                <td><a href="<pwm:context/><%=PwmConstants.URL_PREFIX_PRIVATE%>"><pwm:context/><%=PwmConstants.URL_PREFIX_PRIVATE%></a></td>
            </tr>
            <tr>
                <td class="key">Account Information</td>
                <td><a href="<pwm:context/><%=PwmServletDefinition.AccountInformation.servletUrl()%>"><pwm:context/><%=PwmServletDefinition.AccountInformation.servletUrl()%></a></td>
            </tr>
            <tr>
                <td class="key">Change Password</td>
                <td><a href="<pwm:context/><%=PwmServletDefinition.PrivateChangePassword.servletUrl()%>"><pwm:context/><%=PwmServletDefinition.PrivateChangePassword.servletUrl()%></a></td>
            </tr>
            <tr>
                <td class="key">Setup Responses</td>
                <td><a href="<pwm:context/><%=PwmServletDefinition.SetupResponses.servletUrl()%>"><pwm:context/><%=PwmServletDefinition.SetupResponses.servletUrl()%></a></td>
            </tr>
            <tr>
                <td class="key">Setup OTP</td>
                <td><a href="<pwm:context/><%=PwmServletDefinition.SetupOtp.servletUrl()%>"><pwm:context/><%=PwmServletDefinition.SetupOtp.servletUrl()%></a></td>
            </tr>
            <tr>
                <td class="key">Helpdesk</td>
                <td><a href="<pwm:context/><%=PwmServletDefinition.Helpdesk.servletUrl()%>"><pwm:context/><%=PwmServletDefinition.Helpdesk.servletUrl()%></a></td>
            </tr>
            <tr>
                <td class="key">People Search</td>
                <td><a href="<pwm:context/><%=PwmServletDefinition.PrivatePeopleSearch.servletUrl()%>"><pwm:context/><%=PwmServletDefinition.PrivatePeopleSearch.servletUrl()%></a></td>
            </tr>
            <tr>
                <td class="key">Administration</td>
                <td><a href="<pwm:context/><%=PwmServletDefinition.Admin.servletUrl()%>"><pwm:context/><%=PwmServletDefinition.Admin.servletUrl()%></a></td>
            </tr>
            <tr>
                <td class="key">Configuration</td>
                <td><a href="<pwm:context/><%=PwmServletDefinition.ConfigManager.servletUrl()%>"><pwm:context/><%=PwmServletDefinition.ConfigManager.servletUrl()%></a></td>
            </tr>
        </table>
    </div>
    <div class="push"></div>
</div>
<%@ include file="/WEB-INF/jsp/fragment/footer.jsp" %>
</body>
</html>


