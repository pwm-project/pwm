<%--
  ~ Password Management Servlets (PWM)
  ~ http://code.google.com/p/pwm/
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2015 The PWM Project
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
<% final PwmRequest pwmRequest = PwmRequest.forRequest(request, response); %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<body class="nihilo">
<div id="wrapper">
    <jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="URL Reference"/>
    </jsp:include>
    <div id="centerbody">
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
                <td><a href="<pwm:context/>/public"><pwm:context/>/public</a></td>
            </tr>
            <tr>
                <td  class="key">Forgotten Password</td>
                <td><a href="<pwm:context/>/public/ForgottenPassword"><pwm:context/>/public/ForgottenPassword</a></td>
            </tr>
            <tr>
                <td class="key">Activate User</td>
                <td><a href="<pwm:context/>/public/ActivateUser"><pwm:context/>/public/ActivateUser</a></td>
            </tr>
            <tr>
                <td class="key">New User Registration</td>
                <td><a href="<pwm:context/>/public/NewUser"><pwm:context/>/public/NewUser</a></td>
            </tr>
            <% boolean enabled = pwmRequest.getConfig().readSettingAsBoolean(PwmSetting.PEOPLE_SEARCH_ENABLE_PUBLIC);%>
            <tr>
                <td class="key">Public People Search </td>
                <td>
                    <a disabled href="<pwm:context/>/public/PeopleSearch"><pwm:context/>/public/PeopleSearch</a>
                </td>
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
                <td><a href="<pwm:context/>/private"><pwm:context/>/private</a></td>
            </tr>
            <tr>
                <td class="key">Change Password</td>
                <td><a href="<pwm:context/>/private/ChangePassword"><pwm:context/>/private/ChangePassword</a></td>
            </tr>
            <tr>
                <td class="key">Setup Responses</td>
                <td><a href="<pwm:context/>/private/SetupResponses"><pwm:context/>/private/SetupResponses</a></td>
            </tr>
            <tr>
                <td class="key">Helpdesk</td>
                <td><a href="<pwm:context/>/private/Helpdesk"><pwm:context/>/private/Helpdesk</a></td>
            </tr>
            <tr>
                <td class="key">People Search</td>
                <td><a href="<pwm:context/>/private/PeopleSearch"><pwm:context/>/private/PeopleSearch</a></td>
            </tr>
        </table>
    </div>
    <div class="push"></div>
</div>
<%@ include file="/WEB-INF/jsp/fragment/footer.jsp" %>
</body>
</html>


