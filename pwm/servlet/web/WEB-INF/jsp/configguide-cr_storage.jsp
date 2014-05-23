<%@ page import="password.pwm.bean.servlet.ConfigGuideBean" %>
<%@ page import="password.pwm.servlet.ConfigGuideServlet" %>
<%--
  ~ Password Management Servlets (PWM)
  ~ http://code.google.com/p/pwm/
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2014 The PWM Project
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
<body class="nihilo">
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
                    Local Embedded Database (Testing only)
                </option>
            </select>
            <br/>
            <br/>
        </form>
        <p>
            <b>LDAP</b>: Storing the challenge/response data in LDAP is ideal if your LDAP directory is extensible and can accommodate the storage.  You will need to extend
            the LDAP server's schema or adjust the configure to use pre-existing defined attributes.  You will also need to adjust the ACLs or rights
            in the directory to accommodate the challenge/response storage and other data.  See the documentation for more information.
        </p>
        <p>
            <b>Remote Database</b>: If modifying the LDAP's server schema and rights is not desired or possible, you can use a database to store the user's challenge/response data.
            After the configuration guide process is complete, you will need to edit the Database settings and place your database vendor's JDBC driver on this server.  See
            the documentation for more information.
        </p>
        <p>
            <b>LocalDB</b>: This server has it's own embedded LocalDB that is capable of storing user challenge/responses.  This option should never be used in a production
            environment and is provided only for testing purposes.  User challenge/response's stored in the LocalDB are server specific..
        </p>
        <div id="buttonbar">
            <button class="btn" id="button_previous" onclick="gotoStep('LDAP3');">
                <pwm:if test="showIcons"><span class="btn-icon fa fa-backward"></span></pwm:if>
                <pwm:Display key="Button_Previous" bundle="Config"/>
            </button>
            &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
            <button class="btn" id="button_next" onclick="gotoStep('PASSWORD');">
                <pwm:if test="showIcons"><span class="btn-icon fa fa-forward"></span></pwm:if>
                <pwm:Display key="Button_Next" bundle="Config"/>
            </button>
        </div>
    </div>
    <div class="push"></div>
</div>
<script type="text/javascript">
    function handleFormActivity() {
        //PWM_MAIN.getObject("value_<%=ConfigGuideServlet.PARAM_CR_STORAGE_PREF%>").value = PWM_MAIN.getObject("prefSelect").value;
        updateForm();
    }

    PWM_GLOBAL['startupFunctions'].push(function(){
        require(["dojo/parser","dijit/TitlePane","dijit/form/Form","dijit/form/ValidationTextBox","dijit/form/NumberSpinner","dijit/form/CheckBox"],function(dojoParser){
            dojoParser.parse();
        });

        handleFormActivity();
    });
</script>
<% request.setAttribute(PwmConstants.REQUEST_ATTR_SHOW_LOCALE,"false"); %>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
