<%@ page import="password.pwm.http.servlet.configguide.ConfigGuideForm" %>
<%@ page import="password.pwm.util.java.JavaHelper" %>
<%@ page import="password.pwm.http.servlet.configguide.ConfigGuideFormField" %>
<%@ page import="password.pwm.util.java.StringUtil" %>
<%--
  ~ Password Management Servlets (PWM)
  ~ http://www.pwm-project.org
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2017 The PWM Project
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
<% final ConfigGuideBean configGuideBean = JspUtility.getSessionBean(pageContext, ConfigGuideBean.class);%>
<%@ taglib uri="pwm" prefix="pwm" %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo">
<div id="wrapper">
    <%@ include file="fragment/configguide-header.jsp"%>
    <div id="centerbody">
        <form id="configForm" name="configForm">
            <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
            <div id="outline_ldap-server" class="setting_outline">
                <div id="titlePaneHeader-ldap-server" class="setting_title">
                    <div class="setting_title">
                        Feature Usage Statistics
                    </div>
                </div>
                <div class="setting_body">

                    <label for="widget_<%=ConfigGuideFormField.PARAM_TELEMETRY_ENABLE%>">
                        <b><%=PwmSetting.PUBLISH_STATS_ENABLE.getLabel(JspUtility.locale(request))%></b>
                    </label>
                    <br/><br/>
                    <%=PwmSetting.PUBLISH_STATS_ENABLE.getDescription(JspUtility.locale(request))%>
                    <br/><br/>
                    <%--
                <label class="checkboxWrapper">
                    <input type="checkbox" id="widget_<%=ConfigGuideFormField.PARAM_TELEMETRY_ENABLE%>" name="widget_<%=ConfigGuideFormField.PARAM_TELEMETRY_ENABLE%>" <%=telemEnabled ? "checked" : ""%>/> Enabled
                    <input type="hidden" id="<%=ConfigGuideFormField.PARAM_TELEMETRY_ENABLE%>" name="<%=ConfigGuideFormField.PARAM_TELEMETRY_ENABLE%>" value="false"/>
                    </label>
                    --%>

                    <label class="checkboxWrapper"><input type="radio" id="<%=ConfigGuideFormField.PARAM_TELEMETRY_ENABLE%>-enabled" name="<%=ConfigGuideFormField.PARAM_TELEMETRY_ENABLE%>" value="true">Enabled</label>
                    <br/>
                    <label class="checkboxWrapper"><input type="radio" id="<%=ConfigGuideFormField.PARAM_TELEMETRY_ENABLE%>-disabled" name="<%=ConfigGuideFormField.PARAM_TELEMETRY_ENABLE%>" value="false">Disabled</label>

                    <br/><br/>
                    <div id="descriptionWrapper" style="display: none">

                    <label for="<%=ConfigGuideFormField.PARAM_TELEMETRY_DESCRIPTION%>">
                        <b><%=PwmSetting.PUBLISH_STATS_SITE_DESCRIPTION.getLabel(JspUtility.locale(request))%></b>
                    </label>
                    <br/><br/>

                    <%=PwmSetting.PUBLISH_STATS_SITE_DESCRIPTION.getDescription(JspUtility.locale(request))%>
                    <br/><br/>
                    <input class="configStringInput" maxlength="100" id="<%=ConfigGuideFormField.PARAM_TELEMETRY_DESCRIPTION%>" name="<%=ConfigGuideFormField.PARAM_TELEMETRY_DESCRIPTION%>" value="<%=configGuideBean.getFormData().get(ConfigGuideFormField.PARAM_TELEMETRY_DESCRIPTION)%>" <pwm:autofocus/> />
                    <br/><br/>
                        <% String privacyText = JavaHelper.readEulaText(ContextManager.getContextManager(session),PwmConstants.RESOURCE_FILE_PRIVACY_TXT); %>
                        <div id="agreementWrapper" style="display: none" class="fadein">
                            <% if (!StringUtil.isEmpty(privacyText)) { %>
                            <label><b>Data Privacy Policy</b></label>
                            <div id="agreementText" class="eulaText"><%=privacyText%></div>
                            <% } %>
                        </div>
                    </div>
                </div>
            </div>
        </form>
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

            if (PWM_MAIN.getObject('<%=ConfigGuideFormField.PARAM_TELEMETRY_ENABLE%>-enabled').checked) {
                PWM_MAIN.getObject('<%=ConfigGuideFormField.PARAM_TELEMETRY_DESCRIPTION%>').disabled = false;
                PWM_MAIN.getObject('descriptionWrapper').style.display = 'inline';
                PWM_MAIN.getObject('agreementWrapper').style.display = 'inline';
            } else {
                PWM_MAIN.getObject('<%=ConfigGuideFormField.PARAM_TELEMETRY_DESCRIPTION%>').disabled = true;
                PWM_MAIN.getObject('<%=ConfigGuideFormField.PARAM_TELEMETRY_DESCRIPTION%>').value = '';
                PWM_MAIN.getObject('descriptionWrapper').style.display = 'none';
                PWM_MAIN.getObject('agreementWrapper').style.display = 'none';
            }
        }

        PWM_GLOBAL['startupFunctions'].push(function(){
            PWM_MAIN.addEventHandler('configForm','input,click',function(){
                handleFormActivity();
            });

            handleFormActivity();

            PWM_MAIN.addEventHandler('button_next','click',function(){PWM_GUIDE.gotoStep('NEXT')});
            PWM_MAIN.addEventHandler('button_previous','click',function(){PWM_GUIDE.gotoStep('PREVIOUS')});

            initPage();
            handleFormActivity();
        });

        function checkIfNextEnabled() {
            PWM_MAIN.getObject('button_next').disabled =
                (!(PWM_MAIN.getObject('<%=ConfigGuideFormField.PARAM_TELEMETRY_ENABLE%>-enabled').checked
                || PWM_MAIN.getObject('<%=ConfigGuideFormField.PARAM_TELEMETRY_ENABLE%>-disabled').checked));
        }


        function initPage() {
            <% final String currentValue = configGuideBean.getFormData().get(ConfigGuideFormField.PARAM_TELEMETRY_ENABLE);%>
            <% if ("true".equals(currentValue)) { %>
            PWM_MAIN.getObject('<%=ConfigGuideFormField.PARAM_TELEMETRY_ENABLE%>-enabled').checked = true;
            <% } else if ("false".equals(currentValue)) { %>
            PWM_MAIN.getObject('<%=ConfigGuideFormField.PARAM_TELEMETRY_ENABLE%>-disabled').checked = true;
            <% } %>
        }
    </script>
</pwm:script>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
