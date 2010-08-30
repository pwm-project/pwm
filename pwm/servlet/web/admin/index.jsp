<%@ page import="password.pwm.PwmConstants" %>
<%--
~ Password Management Servlets (PWM)
~ http://code.google.com/p/pwm/
~
~ Copyright (c) 2006-2009 Novell, Inc.
~ Copyright (c) 2009-2010 The PWM Project
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
<body onload="pwmPageLoadHandler();">
<div id="wrapper">
    <jsp:include page="../jsp/header-body.jsp"><jsp:param name="pwm.PageName" value="PWM Administration"/></jsp:include>
    <div id="content">
        <div id="centerbody">
            <h1>Administrator Functions</h1>

            <h2><a href="<pwm:url url='status.jsp'/>">PWM Status</a></h2>
            <p>PWM and Java status</p>

            <h2><a href="<pwm:url url='statistics.jsp'/>">PWM Statistics</a></h2>
            <p>Current and historical PWM Statistics</p>

            <h2><a href="<pwm:url url='eventlog.jsp?level=INFO&count=100'/>">Event Logs</a></h2>
            <p>Recent PWM events</p>

            <h2><a href="<pwm:url url='intruderstatus.jsp'/>">Intruder Table</a></h2>
            <p>List of all intruder monitored users and addresses</p>

            <h2><a href="<pwm:url url='activesessions.jsp'/>">Active Sessions</a></h2>
            <p>List of all active PWM sessions</p>

            <h2><a href="<pwm:url url='config.jsp'/>">Configuration</a></h2>
            <p>Selected PWM configuration settings</p>

            <h2><a href="<pwm:url url='UserInformation'/>">User Information</a></h2>
            <p>Useful for debugging user password issues</p>

            <h2><a href="<pwm:url url='http-request-information.jsp'/>">HTTP Request Information</a></h2>
            <p>Useful for debugging HTTP request issues</p>

            <h2><a href="<pwm:url url='/pwm/config/ConfigManager'/>">ConfigManager</a></h2>
            <p>PWM configuration manager and editor</p>

            <hr/>

            <h1>Other</h1>

            <h2><a href="<pwm:url url='http://code.google.com/p/pwm'/>">PWM Home Page</a></h2>
            <p>The PWM project homepage</p>
        </div>
    </div>
    <br class="clear" />
</div>
<%@ include file="../jsp/footer.jsp" %>
</body>
</html>

