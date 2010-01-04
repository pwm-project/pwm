<%--
  ~ Password Management Servlets (PWM)
  ~ http://code.google.com/p/pwm/
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
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

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en" lang="en">
<%@ include file="jsp/header.jsp" %>
<body onunload="unloadHandler();">
<div id="wrapper">
    <jsp:include page="jsp/header-body.jsp"><jsp:param name="pwm.PageName" value="APPLICATION-TITLE"/></jsp:include>
    <div id="centerbody">
        <p>Password self-service main menu.  From here you can change your current password, reset a forgotten password, or perform other related password activities.</p>
        <h2><a href="<pwm:url url='private/ChangePassword'/>">Change Password</a></h2>
        <p>Change your current password.</p>

        <h2><a href="<pwm:url url='public/ForgottenPassword'/>">Forgotten Password</a></h2>
        <p>Recover your forgotten password.  If you have previously configured your forgotten password responses you will be able to recover a forgotten password.</p>

        <h2><a href="<pwm:url url='private/SetupResponses'/>">Setup Responses</a></h2>
        <p>Setup your forgotten password responses.  These secret questions will allow you to recover your password if you forget it.</p>

        <h2><a href="<pwm:url url='public/ActivateUser'/>">Account Activation</a></h2>
        <p>Activate a pre-configured account and establish a new password.</p>

        <h2><a href="<pwm:url url='public/NewUser'/>" class="tablekey">New User Registration</a></h2>
        <p>Register a new user account</p>

        <h2><a href="<pwm:url url='private/UpdateAttributes'/>" class="tablekey">Update User Info</a></h2>
        <p>Update your user information</p>

        <h2><a href="<pwm:url url='private/history.jsp'/>" class="tablekey">History</a></h2>
        <p>Password event history</p>

        <h2><a href="<pwm:url url='private/Shortcuts'/>" class="tablekey">Shortcuts</a></h2>
        <p>Personalized shortcuts</p>

        <h2><a href="<pwm:url url='private/userinfo.jsp'/>" class="tablekey">User Information</a></h2>
        <p>User Information for debugging</p>
        <hr/>

        <h2><a href="admin" class="tablekey">Admin</a></h2>
        <p>PWM administrative functions</p>

        <hr/>

        <h2><a href="<pwm:url url='public/Logout'/>">Logout</a></h2>
        <p>Logout of the Password Management Servlet</p>
    </div>
</div>
<%@ include file="jsp/footer.jsp" %>
</body>
</html>
