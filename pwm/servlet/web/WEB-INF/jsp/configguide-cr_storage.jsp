<%@ page import="password.pwm.bean.servlet.ConfigGuideBean" %>
<%@ page import="password.pwm.servlet.ConfigGuideServlet" %>
<%--
  ~ Password Management Servlets (PWM)
  ~ http://code.google.com/p/pwm/
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2013 The PWM Project
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
<% ConfigGuideBean configGuideBean = (ConfigGuideBean)PwmSession.getPwmSession(session).getSessionBean(ConfigGuideBean.class);%>
<%@ taglib uri="pwm" prefix="pwm" %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo" onload="pwmPageLoadHandler();">
<link href="<%=request.getContextPath()%><pwm:url url='/public/resources/configStyle.css'/>" rel="stylesheet" type="text/css"/>
<script type="text/javascript" src="<%=request.getContextPath()%><pwm:url url="/public/resources/js/configguide.js"/>"></script>
<script type="text/javascript" src="<%=request.getContextPath()%><pwm:url url="/public/resources/js/configeditor.js"/>"></script>
<div id="wrapper">
    <div id="header">
        <div id="header-company-logo"></div>
        <div id="header-page">
            <pwm:Display key="Title_ConfigGuide" bundle="Config"/>
        </div>
        <div id="header-title">
            Response Storage Preference
        </div>
    </div>
    <div id="centerbody">
        <form id="configForm" data-dojo-type="dijit/form/Form">
            <%--<input type="text" id="value_<%=ConfigGuideServlet.PARAM_CR_STORAGE_PREF%>" value="v1">q</input>--%>
            <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
            <pwm:Display key="Display_ConfigGuideSelectCrStorage" bundle="Config"/>
            <br/>
            <select id="<%=ConfigGuideServlet.PARAM_CR_STORAGE_PREF%>" name="<%=ConfigGuideServlet.PARAM_CR_STORAGE_PREF%>" onchange="handleFormActivity()">
                <% final String current = configGuideBean.getFormData().get(ConfigGuideServlet.PARAM_CR_STORAGE_PREF);%>
                <option value="LDAP"<% if ("LDAP".equals(current)) { %> selected="selected"<% } %>>
                    LDAP
                </option>
                <option value="DB"<% if ("DB".equals(current)) { %> selected="selected"<% } %>>
                    Remote Database
                </option>
                <option value="LOCALDB"<% if ("LOCALDB".equals(current)) { %> selected="selected"<% } %>>
                    Local Embedded Database
                </option>
            </select>
            <br/>
            <br/>
        </form>
        <div id="buttonbar">
            <button class="btn" id="button_previous" onclick="gotoStep('LDAP3');"><pwm:Display key="Button_Previous" bundle="Config"></pwm:Display></button>
            <button class="btn" id="button_next" onclick="gotoStep('PASSWORD');"><pwm:Display key="Button_Next" bundle="Config"></pwm:Display></button>
        </div>
    </div>
    <div class="push"></div>
</div>
<script type="text/javascript">
    function handleFormActivity() {
        //getObject("value_<%=ConfigGuideServlet.PARAM_CR_STORAGE_PREF%>").value = getObject("prefSelect").value;
        updateForm();
    }

    PWM_GLOBAL['startupFunctions'].push(function(){
        getObject('localeSelectionMenu').style.display = 'none';
        require(["dojo/parser","dijit/TitlePane","dijit/form/Form","dijit/form/ValidationTextBox","dijit/form/NumberSpinner","dijit/form/CheckBox"],function(dojoParser){
            dojoParser.parse();
        });

        handleFormActivity();
    });
</script>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
