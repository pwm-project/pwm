<%@ page import="password.pwm.PwmSession" %>
<%@ page import="password.pwm.config.ConfigurationReader" %>
<%@ page import="password.pwm.wordlist.WordlistStatus" %>
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

<%--
  ~ This file is imported by most JSPs, it shows the main 'header' in the html
  - which by default is a blue-gray gradieted and rounded block.       cd ..
  --%>
<%@ taglib uri="pwm" prefix="pwm" %>
<% if (PwmSession.getPwmSession(session).getContextManager().getWordlistManager() != null && PwmSession.getPwmSession(session).getContextManager().getWordlistManager().getStatus() == WordlistStatus.POPULATING) { %>
<div id="header-warning" style="background: #8b0000; color:white;">The wordlist is currently populating.  PWM will be slow until population is completed.   Status: <%= PwmSession.getPwmSession(session).getContextManager().getWordlistManager().getDebugStatus()%></div>
<% } %>
<% if (PwmSession.getPwmSession(session).getContextManager().getConfigReader().getConfigMode() == ConfigurationReader.MODE.CONFIGURATION) { %>
<div id="header-warning">PWM is in configuration mode.  Use the <a href="<pwm:url url='/pwm/config/ConfigManager'/>">ConfigManager</a> to modify or finalize the configuration.</div>
<% } %>
<div id="header">
    <div id="header-page">
        <pwm:Display key="${param['pwm.PageName']}" displayIfMissing="true"/>
     </div>
    <div id="header-title">
        <pwm:Display key="APPLICATION-TITLE"/>
     </div>
</div>
