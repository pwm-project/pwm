<%@ page import="password.pwm.http.bean.ConfigGuideBean" %>
<%@ page import="password.pwm.http.servlet.ConfigGuideServlet" %>
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

<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<% ConfigGuideBean configGuideBean = (ConfigGuideBean) JspUtility.getPwmSession(pageContext).getSessionBean(ConfigGuideBean.class);%>
<%@ taglib uri="pwm" prefix="pwm" %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo">
<link href="<pwm:context/><pwm:url url='/public/resources/configStyle.css'/>" rel="stylesheet" type="text/css"/>
<div id="wrapper">
    <div id="header">
        <div id="header-center">
            <div id="header-page">
                <pwm:display key="Title_ConfigGuide" bundle="Config"/>
            </div>
            <div id="header-title">
                <pwm:display key="Title_ConfigGuide_crStorage" bundle="Config"/>
            </div>
        </div>
    </div>
    <div id="centerbody">
        <form id="configForm">
            <%--<input type="text" id="value_<%=ConfigGuideServlet.PARAM_CR_STORAGE_PREF%>" value="v1">q</input>--%>
            <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
            <pwm:display key="Display_ConfigGuideSelectCrStorage" bundle="Config"/>
            <br/>
            <select id="<%=ConfigGuideServlet.PARAM_CR_STORAGE_PREF%>" name="<%=ConfigGuideServlet.PARAM_CR_STORAGE_PREF%>"
                    style="width:300px">
                <% final String current = configGuideBean.getFormData().get(ConfigGuideServlet.PARAM_CR_STORAGE_PREF);%>
                <option value="LDAP"<% if ("LDAP".equals(current)) { %> selected="selected"<% } %>>
                    LDAP
                </option>
                <option value="DB"<% if ("DB".equals(current)) { %> selected="selected"<% } %>>
                    Remote Database
                </option>
                <option value="LOCALDB"<% if ("LOCALDB".equals(current)) { %> selected="selected"<% } %>>
                    LocalDB (Testing only)
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
            <b>LocalDB (Testing only)</b>: This server has it's own embedded local database (LocalDB) that is capable of storing user challenge/responses.  This option should never be used in a production
            environment and is provided only for testing purposes.  User challenge/response's stored in the LocalDB are server specific..
        </p>
        <div class="buttonbar">
            <button class="btn" id="button_previous">
                <pwm:if test="showIcons"><span class="btn-icon fa fa-backward"></span></pwm:if>
                <pwm:display key="Button_Previous" bundle="Config"/>
            </button>
            &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
            <button class="btn" id="button_next">
                <pwm:if test="showIcons"><span class="btn-icon fa fa-forward"></span></pwm:if>
                <pwm:display key="Button_Next" bundle="Config"/>
            </button>
        </div>
    </div>
    <div class="push"></div>
</div>
<pwm:script>
<script type="text/javascript">
    PWM_GLOBAL['startupFunctions'].push(function(){
        PWM_GLOBAL['startupFunctions'].push(function(){
            PWM_MAIN.addEventHandler('button_next','click',function(){ PWM_GUIDE.gotoStep('NEXT')});
            PWM_MAIN.addEventHandler('button_previous','click',function(){PWM_GUIDE.gotoStep('PREVIOUS')});

            PWM_MAIN.addEventHandler('configForm','input',function(){
                PWM_GUIDE.updateForm();
            });
        });
    });
</script>
</pwm:script>
<% JspUtility.setFlag(pageContext, PwmRequest.Flag.HIDE_LOCALE); %>
<script type="text/javascript" src="<pwm:context/><pwm:url url="/public/resources/js/configguide.js"/>"></script>
<script type="text/javascript" src="<pwm:context/><pwm:url url="/public/resources/js/configmanager.js"/>"></script>
<script type="text/javascript" src="<pwm:context/><pwm:url url="/public/resources/js/admin.js"/>"></script>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
