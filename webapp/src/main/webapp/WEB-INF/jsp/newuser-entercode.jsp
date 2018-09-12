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

<%@ page import="password.pwm.bean.TokenDestinationItem" %>
<%@ page import="password.pwm.http.servlet.newuser.NewUserServlet" %>

<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo">
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_NewUser"/>
    </jsp:include>
    <div id="centerbody">
        <% TokenDestinationItem tokenDestinationItem = (TokenDestinationItem )JspUtility.getAttribute(pageContext, PwmRequestAttribute.TokenDestItems);%>
        <h1 id="page-content-title"><pwm:display key="Title_NewUser" displayIfMissing="true"/></h1>
        <% if (tokenDestinationItem.getType() == TokenDestinationItem.Type.email) { %>
        <p><pwm:display key="Display_RecoverEnterCode" value1="<%=tokenDestinationItem.getDisplay()%>"/></p>
        <% } else if (tokenDestinationItem.getType() == TokenDestinationItem.Type.sms) { %>
        <p><pwm:display key="Display_RecoverEnterCodeSMS" value1="<%=tokenDestinationItem.getDisplay()%>"/></p>
        <% } %>
        <form action="<pwm:current-url/>" method="post" autocomplete="off" enctype="application/x-www-form-urlencoded" name="search" class="pwm-form">
            <%@ include file="fragment/message.jsp" %>
            <%@ include file="/WEB-INF/jsp/fragment/token-form-field.jsp"%>
            <div class="buttonbar">
                <button type="submit" class="btn" name="search" id="submitBtn">
                    <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-check"></span></pwm:if>
                    <pwm:display key="Button_CheckCode"/>
                </button>
                <input type="hidden" id="processAction" name="processAction" value="enterCode"/>
                <input type="hidden" id="pwmFormID" name="pwmFormID" value="<pwm:FormID/>"/>
                <button type="button" name="button-cancel" class="btn" id="button-cancel">
                    <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-times"></span></pwm:if>
                    <pwm:display key="Button_Cancel"/>
                </button>
            </div>
        </form>
    </div>
    <div class="push"></div>
</div>
<pwm:script>
    <script>
        PWM_GLOBAL['startupFunctions'].push(function(){
            PWM_MAIN.addEventHandler('button-cancel','click',function() {
                PWM_MAIN.submitPostAction('newuser', '<%=NewUserServlet.NewUserAction.reset%>');
            });
        });
    </script>
</pwm:script>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>

