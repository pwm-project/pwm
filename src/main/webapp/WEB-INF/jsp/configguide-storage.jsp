<%@ page import="password.pwm.http.servlet.configguide.ConfigGuideForm" %>
<%--
  ~ Password Management Servlets (PWM)
  ~ http://www.pwm-project.org
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2016 The PWM Project
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

<% JspUtility.setFlag(pageContext, PwmRequestFlag.HIDE_LOCALE); %>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.INCLUDE_CONFIG_CSS); %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<% ConfigGuideBean configGuideBean = JspUtility.getSessionBean(pageContext, ConfigGuideBean.class);%>
<% String selectedTemplate = configGuideBean.getFormData().get(ConfigGuideForm.FormParameter.PARAM_TEMPLATE_STORAGE); %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo">
<div id="wrapper">
    <%@ include file="fragment/configguide-header.jsp"%>
    <div id="centerbody">
        <form id="configForm">
            <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
            <pwm:display key="Display_ConfigGuideSelectStorage" bundle="Config"/>
            <br/>
            <select id="<%=ConfigGuideForm.FormParameter.PARAM_TEMPLATE_STORAGE%>" name="<%=ConfigGuideForm.FormParameter.PARAM_TEMPLATE_STORAGE%>">
            <% if (selectedTemplate == null || selectedTemplate.isEmpty()) { %>
            <option value="NOTSELECTED" selected disabled> -- Please select a template -- </option>
            <% } %>
            <% for (final String loopTemplate : PwmSetting.TEMPLATE_STORAGE.getOptions().keySet()) { %>
            <% boolean selected = loopTemplate.equals(selectedTemplate); %>
            <option value="<%=loopTemplate%>"<% if (selected) { %> selected="selected"<% } %>>
                <%=PwmSetting.TEMPLATE_STORAGE.getOptions().get(loopTemplate)%>
            </option>
            <% } %>
            </select>

            <br/>
            <br/>
        </form>
        <p>
            <b>LDAP</b> <p> Storing user data in LDAP is ideal if your LDAP directory is extensible and can accommodate the storage.  You will need to extend
            the LDAP server's schema or adjust the configuration to use pre-existing defined attributes.  You will also need to adjust the access control lists (ACLs) or rights
            in the LDAP directory to accommodate the challenge/response storage and other data.  See the documentation for more information.</p>
        </p>
        <p>
            <b>Remote Database</b> <p> If modifying the LDAP's server schema and rights is not desired or possible, you can use a database to store user data.
            Your database vendor will supply you with the appropriate JDBC driver file and configuration instructions.</p>
        </p>
        <p>
            <b>LocalDB (Testing only)</b> <p> This server has it's own embedded local database (LocalDB) that is capable of storing user challenge/responses.  This option should never be used in a production
            environment and is provided only for testing purposes.  User data including challenge/response answers stored in the LocalDB are server specific.</p>
        </p>
        <%@ include file="fragment/configguide-buttonbar.jsp" %>
    </div>
    <div class="push"></div>
</div>
<pwm:script>
    <script type="text/javascript">

    </script>
    <script type="text/javascript">
        function formHandler() {
            PWM_GUIDE.updateForm();
            updateNextButton();
        }

        function getSelectedValue() {
            var selectedIndex = PWM_MAIN.getObject('<%=ConfigGuideForm.FormParameter.PARAM_TEMPLATE_STORAGE%>').selectedIndex;
            var newTemplate = PWM_MAIN.getObject('<%=ConfigGuideForm.FormParameter.PARAM_TEMPLATE_STORAGE%>').options[selectedIndex];
            return newTemplate.value;
        }

        function updateNextButton() {
            var newTemplate = getSelectedValue();
            var notSelected = newTemplate == 'NOTSELECTED';
            PWM_MAIN.getObject('button_next').disabled = notSelected;
        }

        PWM_GLOBAL['startupFunctions'].push(function(){
            PWM_GLOBAL['startupFunctions'].push(function(){
                PWM_MAIN.addEventHandler('button_next','click',function(){ PWM_GUIDE.gotoStep('NEXT')});
                PWM_MAIN.addEventHandler('button_previous','click',function(){PWM_GUIDE.gotoStep('PREVIOUS')});

                PWM_MAIN.addEventHandler('<%=ConfigGuideForm.FormParameter.PARAM_TEMPLATE_STORAGE%>','change',function(){
                    formHandler();
                });
            });
            updateNextButton();
        });
    </script>
</pwm:script>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
