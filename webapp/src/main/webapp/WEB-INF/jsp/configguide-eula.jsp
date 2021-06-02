<%--
 ~ Password Management Servlets (PWM)
 ~ http://www.pwm-project.org
 ~
 ~ Copyright (c) 2006-2009 Novell, Inc.
 ~ Copyright (c) 2009-2020 The PWM Project
 ~
 ~ Licensed under the Apache License, Version 2.0 (the "License");
 ~ you may not use this file except in compliance with the License.
 ~ You may obtain a copy of the License at
 ~
 ~     http://www.apache.org/licenses/LICENSE-2.0
 ~
 ~ Unless required by applicable law or agreed to in writing, software
 ~ distributed under the License is distributed on an "AS IS" BASIS,
 ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 ~ See the License for the specific language governing permissions and
 ~ limitations under the License.
--%>
<%--
       THIS FILE IS NOT INTENDED FOR END USER MODIFICATION.
       See the README.TXT file in WEB-INF/jsp before making changes.
--%>


<%@ page import="password.pwm.http.servlet.configguide.ConfigGuideForm" %>
<%@ page import="password.pwm.http.tag.value.PwmValue" %>
<%@ page import="com.novell.ldapchai.util.StringHelper" %>
<%@ page import="password.pwm.util.java.StringUtil" %>
<%@ page import="password.pwm.util.java.JavaHelper" %>

<% JspUtility.setFlag(pageContext, PwmRequestFlag.HIDE_LOCALE); %>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.INCLUDE_CONFIG_CSS); %>
<% final ConfigGuideBean configGuideBean = JspUtility.getSessionBean(pageContext, ConfigGuideBean.class);%>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="fragment/header.jsp" %>
<body>
<div id="wrapper">
    <%@ include file="fragment/configguide-header.jsp"%>
    <div id="centerbody" class="wide">
        <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
        <br/>
        <div id="password" class="setting_outline">
            <div class="setting_title">
                End User License Agreement
            </div>
            <div class="setting_body">
                <div id="agreementText" class="eulaText"><%=JavaHelper.readEulaText(ContextManager.getContextManager(session),PwmConstants.RESOURCE_FILE_EULA_TXT)%></div>
            </div>

            <br/><br/>
            <div style="text-align: center">
                <form id="configForm">

                    <label class="checkboxWrapper">
                        <input type="checkbox" id="agreeCheckBox"/>
                        <pwm:display key="Button_Agree"/>
                    </label>
                </form>
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
            PWM_MAIN.getObject('button_next').disabled = !checkIfNextEnabled();
        }

        PWM_GLOBAL['startupFunctions'].push(function(){
            PWM_MAIN.addEventHandler('button_next','click',function(){PWM_GUIDE.gotoStep('NEXT')});
            PWM_MAIN.addEventHandler('button_previous','click',function(){PWM_GUIDE.gotoStep('PREVIOUS')});
            PWM_MAIN.addEventHandler('configForm','input,click',function(){handleFormActivity()});

            handleFormActivity();
        });

        function checkIfNextEnabled() {

            var checkBox = PWM_MAIN.getObject("agreeCheckBox");
            if (checkBox != null) {
                if (checkBox.checked) {
                    return true;
                }
            }

            return false;
        }
    </script>
</pwm:script>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
