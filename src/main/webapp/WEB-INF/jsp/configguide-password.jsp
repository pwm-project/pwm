<%@ page import="password.pwm.http.servlet.configguide.ConfigGuideForm" %>
<%@ page import="password.pwm.http.tag.value.PwmValue" %>
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
<% ConfigGuideBean configGuideBean = JspUtility.getSessionBean(pageContext, ConfigGuideBean.class);%>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo">
<div id="wrapper">
    <%@ include file="fragment/configguide-header.jsp"%>
    <div id="centerbody">
        <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
        <br/>
        <div id="password" class="setting_outline">
            <div class="setting_title">
                <pwm:display key="password_title" bundle="ConfigGuide"/>
            </div>
            <div class="setting_body">
                <pwm:display key="password_description" bundle="ConfigGuide"/>
                <br/><br/>
                <form id="configForm">
                    <input type="hidden" id="<%=ConfigGuideForm.FormParameter.PARAM_CONFIG_PASSWORD%>" name="<%=ConfigGuideForm.FormParameter.PARAM_CONFIG_PASSWORD%>"/>
                </form>
                <div style="text-align: center"><button class="btn" id="button-setPassword">
                    <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-key"></span></pwm:if>
                    Set Password
                </button></div>
            </div>
        </div>
        <br/>
        <%@ include file="fragment/configguide-buttonbar.jsp" %>
    </div>
    <div class="push"></div>
</div>
<pwm:script>
    <script type="text/javascript">
        function handleFormActivity() {
            PWM_GUIDE.updateForm();
            checkIfNextEnabled();
        }

        PWM_GLOBAL['startupFunctions'].push(function(){
            PWM_MAIN.addEventHandler('button_next','click',function(){PWM_GUIDE.gotoStep('NEXT')});
            PWM_MAIN.addEventHandler('button_previous','click',function(){PWM_GUIDE.gotoStep('PREVIOUS')});

            PWM_MAIN.addEventHandler('configForm','input',function(){handleFormActivity()});

            PWM_MAIN.addEventHandler('button-setPassword','click',function(){
                var writeFunction = function(password) {
                    var hiddenInput = PWM_MAIN.getObject('<%=ConfigGuideForm.FormParameter.PARAM_CONFIG_PASSWORD%>');
                    hiddenInput.value = password;
                    PWM_GUIDE.updateForm();
                    checkIfNextEnabled();
                };

                UILibrary.passwordDialogPopup({writeFunction:writeFunction})
            });

            checkIfNextEnabled();
        });

        function checkIfNextEnabled() {
            var password = PWM_MAIN.getObject('<%=ConfigGuideForm.FormParameter.PARAM_CONFIG_PASSWORD%>').value;

            <% String existingPwd = configGuideBean.getFormData().get(ConfigGuideForm.FormParameter.PARAM_CONFIG_PASSWORD); %>
            <% if (existingPwd == null || existingPwd.isEmpty()) { %>
            PWM_MAIN.getObject('button_next').disabled = true;
            if (password.length > 0) {
                PWM_MAIN.getObject('button_next').disabled = false;
            }
            <% } %>
        }
    </script>
</pwm:script>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
