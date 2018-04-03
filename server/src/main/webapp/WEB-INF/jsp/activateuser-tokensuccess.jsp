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
<%@ page import="password.pwm.http.servlet.activation.ActivateUserServlet" %>

<!DOCTYPE html>
<% TokenDestinationItem tokenDestinationItem = (TokenDestinationItem)JspUtility.getAttribute(pageContext, PwmRequestAttribute.TokenDestItems); %>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo">
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_ActivateUser"/>
    </jsp:include>
    <div id="centerbody">
        <h1 id="page-content-title"><pwm:display key="Title_ActivateUser" displayIfMissing="true"/></h1>
        <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
        <p>
            <pwm:display key="Display_RecoverTokenSuccess" value1="<%=tokenDestinationItem.getDisplay()%>"/>
        </p>
        <div>
        <div class="buttonbar">
            <form action="<pwm:current-url/>" method="get" enctype="application/x-www-form-urlencoded" name="search" class="pwm-form">
                <button type="submit" id="button-continue" name="button-continue" class="btn">
                    <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-forward"></span></pwm:if>
                    <pwm:display key="Button_Continue"/>
                </button>
                <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
            </form>
            <pwm:if test="<%=PwmIfTest.showCancel%>">
            <form action="<pwm:current-url/>" method="post" enctype="application/x-www-form-urlencoded" name="search" class="pwm-form" autocomplete="off">
                <button type="submit" name="button" class="btn" id="button-sendReset">
                    <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-times"></span></pwm:if>
                    <pwm:display key="Button_Cancel"/>
                </button>
                <input type="hidden" name="<%=PwmConstants.PARAM_ACTION_REQUEST%>" value="<%=ActivateUserServlet.ActivateUserAction.reset%>"/>
                <input type="hidden" name="<%=PwmConstants.PARAM_RESET_TYPE%>" value="<%=ActivateUserServlet.ResetType.exitActivation%>"/>
                <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
            </form>
            </pwm:if>
        </div>
        </div>
        </form>
    </div>
    <div class="push"></div>
</div>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>

