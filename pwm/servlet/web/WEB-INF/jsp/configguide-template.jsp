<%@ page import="password.pwm.bean.ConfigGuideBean" %>
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

<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% ConfigGuideBean configGuideBean = (ConfigGuideBean)PwmSession.getPwmSession(session).getSessionBean(ConfigGuideBean.class);%>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo" onload="pwmPageLoadHandler()">
<script type="text/javascript" src="<%=request.getContextPath()%><pwm:url url="/public/resources/configguide.js"/>"></script>
<div id="wrapper">
    <div id="header">
        <div id="header-company-logo"></div>
        <div id="header-page">
            <pwm:Display key="Title_ConfigGuide" bundle="Config"/>
        </div>
        <div id="header-title">
            <pwm:Display key="Title_ConfigGuide_template" bundle="Config"/>
        </div>
    </div>
    <div id="centerbody">
        <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
        <pwm:Display key="Display_ConfigGuideSelectTemplate" bundle="Config"/>
        <br/>
        <select onchange="selectTemplate(this.value);">
            <% for (final PwmSetting.Template template : PwmSetting.Template.values()) { %>
            <option value="<%=template.toString()%>"<% if (template == configGuideBean.getStoredConfiguration().getTemplate()) { %> selected="selected"<% } %>>
               <%=template.getDescription()%>
            </option>
            <% } %>
        </select>
        <br/>

        <div id="buttonbar">
            <button class="btn" id="button_previous" onclick="gotoStep('START')"><pwm:Display key="Button_Previous" bundle="Config"/></button>
            &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
            <button class="btn" id="button_next" onclick="gotoStep('LDAP')"><pwm:Display key="Button_Next" bundle="Config"/></button>
        </div>
    </div>
</div>
<script type="text/javascript">
    PWM_GLOBAL['startupFunctions'].push(function(){
        getObject('localeSelectionMenu').style.display = 'none';
    });
</script>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>