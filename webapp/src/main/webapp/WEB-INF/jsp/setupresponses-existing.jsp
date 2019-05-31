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

<%@ page import="com.novell.ldapchai.cr.Challenge" %>
<%@ page import="password.pwm.bean.ResponseInfoBean" %>
<%@ page import="password.pwm.util.java.StringUtil" %>
<%@ page import="password.pwm.http.PwmRequestAttribute" %>

<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="fragment/header.jsp" %>
<% final ResponseInfoBean responseInfoBean = (ResponseInfoBean)JspUtility.getAttribute(pageContext, PwmRequestAttribute.SetupResponses_ResponseInfo); %>
<body class="nihilo">
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_ConfirmResponses"/>
    </jsp:include>
    <div id="centerbody">
        <h1 id="page-content-title"><pwm:display key="Title_ConfirmResponses" displayIfMissing="true"/></h1>
        <p>
            <% if (responseInfoBean != null && responseInfoBean.getTimestamp() != null) { %>
            <pwm:display key="Display_WarnExistingResponseTime" value1="@ResponseSetupTime@"/>
            <% } else { %>
            <pwm:display key="Display_WarnExistingResponse"/>
            <% } %>
        </p>
        <%@ include file="fragment/message.jsp" %>
        <br/>
        <h2><pwm:display key="Title_AnsweredQuestions"/></h2>
        <% for (final Challenge loopChallenge : responseInfoBean.getCrMap().keySet()) { %>
                <p><%= StringUtil.escapeHtml(loopChallenge.getChallengeText()) %></p>
        <% } %>
        <br/>
        <div class="buttonbar">
            <form style="display: inline" action="<pwm:current-url/>" method="post" name="clearExistingForm" id="clearExistingForm"
                  enctype="application/x-www-form-urlencoded" class="pwmForm">
                <button type="submit" name="confirm_btn" class="btn" id="confirm_btn" value="">
                    <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-times"></span></pwm:if>
                    <pwm:display key="Button_ClearResponses"/>
                </button>
                <input type="hidden" name="processAction" value="clearExisting"/>
                <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
            </form>
            <%@ include file="/WEB-INF/jsp/fragment/cancel-button.jsp" %>
        </div>
    </div>
    <div class="push"></div>
</div>
<pwm:script>
    <script type="text/javascript">
        PWM_GLOBAL['startupFunctions'].push(function(){
            PWM_MAIN.addEventHandler('clearExistingForm','submit',function(event){
                PWM_MAIN.cancelEvent(event);
                PWM_MAIN.showConfirmDialog({
                    text: PWM_MAIN.showString("Display_ResponsesClearWarning"),
                    okAction:function(){
                        PWM_MAIN.handleFormSubmit(PWM_MAIN.getObject('clearExistingForm'));
                    }
                });
            });
        });
    </script>
</pwm:script>
<%@ include file="/WEB-INF/jsp/fragment/cancel-form.jsp" %>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
