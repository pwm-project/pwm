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
<body class="nihilo">
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
