<%--
 ~ Password Management Servlets (PWM)
 ~ http://www.pwm-project.org
 ~
 ~ Copyright (c) 2006-2009 Novell, Inc.
 ~ Copyright (c) 2009-2018 The PWM Project
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

<%@ page import="password.pwm.http.servlet.configguide.ConfigGuideForm" %>
<%@ page import="password.pwm.util.java.StringUtil" %>
<%@ page import="password.pwm.http.servlet.configguide.ConfigGuideFormField" %>
<%@ page import="password.pwm.http.servlet.PwmServletDefinition" %>

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
        <form id="configForm">
            <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
            <br/>
            <div id="outline_ldap" class="setting_outline">
                <div class="setting_title">
                    Site URL
                </div>
                <div class="setting_body">
                    <%=PwmSetting.PWM_SITE_URL.getDescription(JspUtility.locale(request))%>
                    <br/><br/>
                    Example: <code><%=PwmSetting.PWM_SITE_URL.getExample(ConfigGuideForm.generateStoredConfig(configGuideBean).getTemplateSet())%></code>
                    <br/><br/>
                    <div class="setting_item">
                        <div id="titlePane_<%=ConfigGuideFormField.PARAM_APP_SITEURL%>" style="padding-left: 5px; padding-top: 5px">
                            <label>
                                <b>Site URL</b>
                                <br/>
                                <input class="configStringInput" id="<%=ConfigGuideFormField.PARAM_APP_SITEURL%>" name="<%=ConfigGuideFormField.PARAM_APP_SITEURL%>"
                                       value="<%=StringUtil.escapeHtml(configGuideBean.getFormData().get(ConfigGuideFormField.PARAM_APP_SITEURL))%>" required autofocus
                                       pattern="<%=PwmSetting.PWM_SITE_URL.getRegExPattern()%>"/>
                            </label>
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
        }

        PWM_GLOBAL['startupFunctions'].push(function(){

            PWM_MAIN.addEventHandler('button_next','click',function(){PWM_GUIDE.gotoStep('NEXT')});
            PWM_MAIN.addEventHandler('button_previous','click',function(){PWM_GUIDE.gotoStep('PREVIOUS')});

            PWM_MAIN.addEventHandler('configForm','input',function(){handleFormActivity()});

            checkIfNextEnabled();

            populateSiteUrl();
        });

        function populateSiteUrl() {
            var siteUrlInput = PWM_MAIN.getObject('<%=ConfigGuideFormField.PARAM_APP_SITEURL%>');
            if (siteUrlInput.value.length > 0) {
                return;
            }

            var suggestedSiteUrl = window.location.protocol + '//' + window.location.host + PWM_GLOBAL['url-context'];
            siteUrlInput.value = suggestedSiteUrl;

            handleFormActivity();
            checkIfNextEnabled();
        }

        function checkIfNextEnabled() {
            var siteUrlInput = PWM_MAIN.getObject('<%=ConfigGuideFormField.PARAM_APP_SITEURL%>');
            var passed = siteUrlInput.value.length > 1 && new RegExp(siteUrlInput.getAttribute('pattern')).test(siteUrlInput.value);
            if (passed) {
                PWM_MAIN.getObject('button_next').disabled = false;
            } else {
                PWM_MAIN.getObject('button_next').disabled = true;
            }
        }
    </script>
</pwm:script>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
