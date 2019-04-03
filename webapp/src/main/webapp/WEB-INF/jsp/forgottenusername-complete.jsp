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

<%@ page import="password.pwm.http.servlet.command.CommandServlet" %>
<%@ page import="password.pwm.http.servlet.PwmServletDefinition" %>
<%@ page import="password.pwm.http.PwmRequestAttribute" %>

<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo">
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_ForgottenUsername"/>
    </jsp:include>
    <div id="centerbody">
        <h1 id="page-content-title"><pwm:display key="Title_ForgottenUsername" displayIfMissing="true"/></h1>
        <%@ include file="fragment/message.jsp" %>
        <% final String expandedText = (String) JspUtility.getAttribute(pageContext, PwmRequestAttribute.CompleteText); %>
        <br/>
        <div id="agreementText" class="agreementText"><%= expandedText %></div>
        <div class="buttonbar">
            <form action="<pwm:url url='<%=PwmServletDefinition.PublicCommand.servletUrl()%>' addContext="true"/>" method="post" enctype="application/x-www-form-urlencoded" class="pwm-form">
                <input type="hidden" name="<%=PwmConstants.PARAM_ACTION_REQUEST%>" value="<%=CommandServlet.CommandAction.next.toString()%>"/>
                <button type="submit" name="button" class="btn" id="submitBtn">
                    <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-forward"></span></pwm:if>
                    <pwm:display key="Button_Continue"/>
                </button>
                <input type="hidden" name="pwmFormID" id="pwmFormID" value="<pwm:FormID/>"/>
            </form>
            <br/>
        </div>
    </div>
    <div class="push"></div>
</div>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
