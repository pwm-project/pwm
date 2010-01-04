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
<jsp:include page="../jsp/header.jsp"/>
<body onunload="unloadHandler();">
<div id="wrapper">
    <jsp:include page="../jsp/header-body.jsp"><jsp:param name="pwm.PageName" value="PWM Administration"/></jsp:include>
    <div id="content">
        <div id="centerbody">
            <p>PWM Version <%=password.pwm.Constants.SERVLET_VERSION %></p>

            <h1>Administrator Functions</h1>

            <h2><a href="<pwm:url url='status.jsp'/>">PWM Status</a></h2>
            <p>PWM and Java status</p>

            <h2><a href="<pwm:url url='eventlog.jsp?level=INFO&count=100'/>">Event Logs</a></h2>
            <p>Recent PWM events</p>

            <h2><a href="<pwm:url url='intruderstatus.jsp'/>">Intruder Table</a></h2>
            <p>List of all intruder monitored users and addresses</p>

            <h2><a href="<pwm:url url='activesessions.jsp'/>">Active Sessions</a></h2>
            <p>List of all active PWM sessions</p>

            <h2><a href="<pwm:url url='config.jsp'/>">Configuration</a></h2>
            <p>Selected PWM configuration settings</p>

            <h2><a href="<pwm:url url='threads.jsp'/>">Threads</a></h2>
            <p>Current table of Java VM threads</p>

            <h2><a href="<pwm:url url='UserInformation'/>">User Information</a></h2>
            <p>Useful for debugging user password issues</p>

            <h2><a href="<pwm:url url='http-request-information.jsp'/>">HTTP Request Information</a></h2>
            <p>Useful for debugging HTTP request issues</p>

            <h2><a href="<pwm:url url='../public/Logout'/>">Logout</a></h2>
            <p>Logout of the Password Management Servlet</p>

            <hr/>

            <h1>Command Functions</h1>
            <p>These functions are not intended to be used directly in a browser, but instead
            used as links from a portal or authentication gateway.</p>

            <h2><a href="<pwm:url url='../private/CommandServlet?processAction=checkExpire'/>">Check Expire</a></h2>
            <p>Checks the user's password expiration. If the experiation date is within the configured threshold, the user will be required to change password.</p>

            <h2><a href="<pwm:url url='../private/CommandServlet?processAction=checkResponses'/>">Check Responses</a></h2>
            <p>Checks the user's challenge responses. If incomplete responses are configured, the user will be set them up.</p>

            <h2><a href="<pwm:url url='../private/CommandServlet?processAction=checkAttributes'/>">Check Attributes</a></h2>
            <p>Checks the user's attributes. If the user's attributes do not meet
            the configured requirements, the user will be required to set their
            attributes.</p>

            <h2><a href="<pwm:url url='../private/CommandServlet?processAction=checkAll'/>">Check All</a></h2>
            <p>Calls checkExpire, checkResponses and checkAttributes consecutively. checkAttributes is only
            called if update attributes is enabled.</p>

            <h2><a href="<pwm:url url='../private/ChangePasswordServlet?passwordExpired=true&logoutURL=/AGLogout'/>">Expired password URL for Access Manager</a></h2>
            <p>Link for Access Manager password management servlet parameter</p>

            <h2><a href="<pwm:url url='../private/ChangePasswordServlet?passwordExpired=true&logoutURL=/cmd/ICSLogout'/>">Expired password URL for iChain</a></h2>
            <p>Link for iChain password management servlet parameter</p>

            <hr/>

            <h1>Other</h1>

            <h2><a href="<pwm:url url='../adminguide.html'/>">PWM Admin Guide</a></h2>
            <p>PWM Administrator Guide</p>

            <h2><a href="<pwm:url url='http://code.google.com/p/pwm'/>">PWM Home Page</a></h2>
            <p>The PWM project homepage</p>
        </div>
    </div>
    <br class="clear" />
</div>
</body>
</html>

