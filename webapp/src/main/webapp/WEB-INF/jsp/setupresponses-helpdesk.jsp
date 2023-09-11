<%--
 ~ Password Management Servlets (PWM)
 ~ http://www.pwm-project.org
 ~
 ~ Copyright (c) 2006-2009 Novell, Inc.
 ~ Copyright (c) 2009-2021 The PWM Project
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


<%@ page import="password.pwm.http.bean.SetupResponsesBean" %>
<%@ page import="password.pwm.http.tag.conditional.PwmIfTest" %>
<%@ page import="password.pwm.http.PwmRequestAttribute" %>
<%@ page import="password.pwm.http.servlet.setupresponses.SetupResponsesServlet" %>
<%@ page import="password.pwm.http.servlet.setupresponses.ResponseMode" %>
<%@ page import="password.pwm.bean.ResponseInfoBean" %>
<%@ page import="password.pwm.http.PwmResponse" %>
<%@ page import="password.pwm.http.servlet.setupresponses.SetupResponsesUtil" %>
<!DOCTYPE html>

<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="fragment/header.jsp" %>
<body>
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_SetupResponses"/>
    </jsp:include>
    <div id="centerbody">
        <h1 id="page-content-title"><pwm:display key="Title_SetupResponses" displayIfMissing="true"/></h1>
        <p><pwm:display key="Display_SetupHelpdeskResponses"/></p>
        <form action="<pwm:current-url/>" method="post" name="form-setupResponses"
              enctype="application/x-www-form-urlencoded" id="form-setupResponses" class="pwm-form" autocomplete="off">
            <%@ include file="fragment/message.jsp" %>
            <div id="pwm-setupResponsesDiv">
                <jsp:include page="fragment/setupresponses-form.jsp"/>
            </div>
            <div class="buttonbar">
                <input type="hidden" name="processAction" value="setHelpdeskResponses"/>
                <button type="submit" name="setResponses" class="btn" id="button-setResponses">
                    <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-forward"></span></pwm:if>
                    <pwm:display key="Button_SetResponses"/>
                </button>
                <% if ( SetupResponsesUtil.hasChallenges( JspUtility.getPwmRequest( pageContext ), ResponseMode.user ) ) { %>
                <button type="submit" name="skip" class="btn" id="skipbutton" form="skipForm">
                    <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-backward"></span></pwm:if>
                    <pwm:display key="Button_GoBack"/>
                </button>
                <% } %>
                <input type="hidden" id="pwmFormID" name="pwmFormID" value="<pwm:FormID/>"/>
            </div>
        </form>
        <form class="hidden" action="<pwm:current-url/>" method="post" name="goBackForm" id="skipForm" enctype="application/x-www-form-urlencoded" class="pwmForm">
            <input type="hidden" name="<%=PwmConstants.PARAM_ACTION_REQUEST%>" value="<%=SetupResponsesServlet.SetupResponsesAction.changeResponses%>"/>
            <input type="hidden" name="<%=PwmConstants.PARAM_FORM_ID%>" value="<pwm:FormID/>"/>
        </form>
    </div>
    <div class="push"></div>
</div>
<script type="module" nonce="<pwm:value name="<%=PwmValue.cspNonce%>"/>">
    import {PWM_RESPONSES} from "<pwm:url url="/public/resources/js/responses.js" addContext="true"/>";
    PWM_RESPONSES.startupResponsesPage("<%=ResponseMode.helpdesk%>");
</script>
<%@ include file="/WEB-INF/jsp/fragment/cancel-form.jsp" %>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
