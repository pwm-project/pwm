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


<%@ page import="password.pwm.util.java.StringUtil" %>

<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final String nextURL = (String)request.getAttribute("Location"); %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo">
<meta id="meta-redirect" http-equiv="refresh" content="30;url='<%=StringUtil.escapeHtml(nextURL)%>'"><%-- failsafe... --%>
<div id="wrapper">
    <div style="height:100px">&nbsp;</div>
    <div id="centerbody">
        <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
        <p style="width: 100%; text-align: center">
            <pwm:display key="Display_PleaseWait"/>
            <br/>
            <br/>
        <div class="WaitDialogBlank"></div>

        </p>

        <div class="buttonbar"></div>
        <p id="failsafeAnchor">
            <a href="<%=StringUtil.escapeHtml(nextURL)%>">Click here to continue...</a>
        </p>
        <pwm:script>
            <script type="text/javascript">
                var div = document.getElementById('failsafeAnchor');
                while(div.firstChild){
                    div.removeChild(div.firstChild);
                }
            </script>
        </pwm:script>
    </div>
    <div class="push"></div>
</div>
<pwm:script>
    <script type="text/javascript">
        PWM_GLOBAL['startupFunctions'].push(function(){
            PWM_MAIN.preloadAll(function(){
                PWM_MAIN.gotoUrl('<%=StringUtil.escapeJS(nextURL)%>');
            });
        });
    </script>
</pwm:script>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.HIDE_LOCALE); %>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.HIDE_IDLE); %>
<%@ include file="/WEB-INF/jsp/fragment/footer.jsp" %>
</body>
</html>
