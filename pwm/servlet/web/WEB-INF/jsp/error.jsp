<%@ page import="password.pwm.error.ErrorInformation" %>
<%@ page import="password.pwm.http.JspUtility" %>
<%--
  ~ Password Management Servlets (PWM)
  ~ http://code.google.com/p/pwm/
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2015 The PWM Project
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

<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final ErrorInformation errorInformation = (ErrorInformation)JspUtility.getAttribute(pageContext, PwmConstants.REQUEST_ATTR.PwmErrorInfo); %>
<html dir="<pwm:LocaleOrientation/>">
<% JspUtility.setFlag(pageContext, PwmRequest.Flag.HIDE_HEADER_BUTTONS); %>
<% JspUtility.setFlag(pageContext, PwmRequest.Flag.HIDE_HEADER_WARNINGS); %>
<% JspUtility.setFlag(pageContext, PwmRequest.Flag.NO_REQ_COUNTER); %>
<%@ include file="fragment/header.jsp" %>
<body class="nihilo" data-jsp-page="error.jsp">
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_Error"/>
    </jsp:include>
    <div id="centerbody">
        <br/>
        <h2><%=PwmConstants.PWM_APP_NAME%>&nbsp;<%=errorInformation == null ? "" : errorInformation.getError().getErrorCode()%></h2>
        <br/>
        <br/>
        <span id="message" class="message message-error"><pwm:ErrorMessage/></span>
        <br/>
        <br/>
        <pwm:if test="showErrorDetail">
        <% if (errorInformation != null && !errorInformation.getError().isErrorIsPermanent()) { %>
        <div class="buttonbar">
            <form action="<pwm:context/>/public/<pwm:url url='CommandServlet'/>" method="post"
                  enctype="application/x-www-form-urlencoded">
                <input type="hidden" name="processAction" value="continue"/>
                <button type="submit" name="button" class="btn" id="button_continue" autofocus="autofocus">
                    <pwm:if test="showIcons"><span class="btn-icon fa fa-forward"></span></pwm:if>
                    <pwm:display key="Button_Continue"/>
                </button>
                <input type="hidden" id="pwmFormID" name="pwmFormID" value="<pwm:FormID/>"/>
            </form>
        </div>
        <% } %>
        </pwm:if>
    </div>
    <div class="push"></div>
</div>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
