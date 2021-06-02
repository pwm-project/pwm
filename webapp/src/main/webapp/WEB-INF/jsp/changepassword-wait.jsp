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


<%@ page import="password.pwm.error.PwmException" %>
<%@ page import="password.pwm.http.bean.ChangePasswordBean" %>
<%@ page import="password.pwm.util.java.TimeDuration" %>
<%@ page import="java.time.Instant" %>
<%@ page import="password.pwm.http.PwmRequestAttribute" %>

<!DOCTYPE html>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.HIDE_HEADER_BUTTONS);%>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.NO_IDLE_TIMEOUT);%>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.HIDE_FOOTER_TEXT);%>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="fragment/header.jsp" %>
<body>
<% long maxWaitSeconds = (Long)JspUtility.getAttribute(pageContext, PwmRequestAttribute.ChangePassword_MaxWaitSeconds); %>
<% long checkIntervalSeconds = (Long)JspUtility.getAttribute(pageContext, PwmRequestAttribute.ChangePassword_CheckIntervalSeconds); %>
<meta http-equiv="refresh" content="<%=maxWaitSeconds%>;url='ChangePassword?processAction=complete&pwmFormID=<pwm:FormID/>">
<noscript>
    <meta http-equiv="refresh" content="<%=checkIntervalSeconds%>;url='ChangePassword?processAction=complete&pwmFormID=<pwm:FormID/>">
</noscript>
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_PleaseWait"/>
    </jsp:include>
    <div id="centerbody" >
        <h1 id="page-content-title"><pwm:display key="Title_PleaseWait" displayIfMissing="true"/></h1>
        <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
        <p><pwm:display key="Display_PleaseWaitPassword"/></p>
        <div class="meteredProgressBar">
          <progress id="html5ProgressBar" max="100" value="0">
              <div data-dojo-type="dijit/ProgressBar" style="width:100%" data-dojo-id="passwordProgressBar" id="passwordProgressBar" data-dojo-props="maximum:100"></div>
          </progress>
        </div>
        <div style="text-align: center; width: 100%; padding-top: 50px">
            <%--
            <div>Elapsed Time: <span id="elapsedSeconds"></span></div>7
            <div>Estimated Time Remaining: <span id="estimatedRemainingSeconds"></span></div>
            --%>
        </div>
        <br/>
        <table id="progressMessageTable" style="padding-bottom: 20px; width: 80%; margin-right: 10%; margin-left: 10%">
        </table>
    </div>
    <div class="push"></div>
</div>
<pwm:script>
<script type="text/javascript">
    PWM_GLOBAL['startupFunctions'].push(function(){
        require(["dojo/parser", "dijit/ProgressBar","dojo/ready"], function(parser){
            parser.parse();
            PWM_CHANGEPW.refreshCreateStatus(<%=checkIntervalSeconds * 1000%>);
        });
    });
</script>
</pwm:script>
<pwm:script-ref url="/public/resources/js/changepassword.js"/>
<%@ include file="/WEB-INF/jsp/fragment/footer.jsp" %>
</body>
</html>
