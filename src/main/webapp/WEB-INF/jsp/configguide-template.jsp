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
<%@ taglib uri="pwm" prefix="pwm" %>
<% ConfigGuideBean configGuideBean = JspUtility.getSessionBean(pageContext, ConfigGuideBean.class);%>
<% String selectedTemplate = configGuideBean.getFormData().get(ConfigGuideForm.FormParameter.PARAM_TEMPLATE_LDAP); %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo">
<div id="wrapper">
    <%@ include file="fragment/configguide-header.jsp"%>
    <div id="centerbody">
        <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
        <pwm:display key="template_description" bundle="ConfigGuide"/>
        <br/>
        <form id="configForm">
            <select id="<%=ConfigGuideForm.FormParameter.PARAM_TEMPLATE_LDAP%>" name="<%=ConfigGuideForm.FormParameter.PARAM_TEMPLATE_LDAP%>" style="width:300px">
                <% if (selectedTemplate == null || selectedTemplate.isEmpty()) { %>
                <option value="NOTSELECTED" selected disabled>-- Please select a template --</option>
                <% } %>
                <% for (final String loopTemplate : PwmSetting.TEMPLATE_LDAP.getOptions().keySet()) { %>
                <% boolean selected = loopTemplate.equals(selectedTemplate); %>
                <option value="<%=loopTemplate%>"<% if (selected) { %> selected="selected"<% } %>>
                    <%=PwmSetting.TEMPLATE_LDAP.getOptions().get(loopTemplate)%>
                </option>
                <% } %>
            </select>
        </form>
        <br/>
        <%@ include file="fragment/configguide-buttonbar.jsp" %>
    </div>
    <div class="push"></div>
</div>
<pwm:script>
    <script type="text/javascript">
        function formHandler() {
            PWM_GUIDE.updateForm();
            updateNextButton();
        }

        function getSelectedValue() {
            var selectedIndex = PWM_MAIN.getObject('<%=ConfigGuideForm.FormParameter.PARAM_TEMPLATE_LDAP%>').selectedIndex;
            var newTemplate = PWM_MAIN.getObject('<%=ConfigGuideForm.FormParameter.PARAM_TEMPLATE_LDAP%>').options[selectedIndex];
            return newTemplate.value;
        }

        function updateNextButton() {
            var newTemplate = getSelectedValue();
            var notSelected = newTemplate == 'NOTSELECTED';
            PWM_MAIN.getObject('button_next').disabled = notSelected;
        }

        PWM_GLOBAL['startupFunctions'].push(function(){
            PWM_MAIN.addEventHandler('button_previous','click',function(){
                PWM_MAIN.showConfirmDialog({
                    text:'Proceeding will cause existing guide settings to be cleared.  Are you sure you wish to continue?',
                    okAction:function(){
                        PWM_GUIDE.gotoStep('PREVIOUS');
                    }
                });
            });
            PWM_MAIN.addEventHandler('button_next','click',function(){PWM_GUIDE.gotoStep('NEXT')});
            PWM_MAIN.addEventHandler('<%=ConfigGuideForm.FormParameter.PARAM_TEMPLATE_LDAP%>','change',function(){formHandler()});
            updateNextButton();
        });
    </script>
</pwm:script>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
