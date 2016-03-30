<%@ page import="password.pwm.config.option.IdentityVerificationMethod" %>
<%@ page import="java.util.HashSet" %>
<%@ page import="java.util.Set" %>
<%@ page import="password.pwm.http.tag.conditional.PwmIfTest" %>
<%--
  ~ Password Management Servlets (PWM)
  ~ http://www.pwm-project.org
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2016 The PWM Project
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
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<%
    final PwmRequest pwmRequest = PwmRequest.forRequest(request, response);
    final Set<IdentityVerificationMethod> methods = new HashSet<IdentityVerificationMethod>((Set<IdentityVerificationMethod>) JspUtility.getAttribute(pageContext, PwmRequest.Attribute.AvailableAuthMethods));
%>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo">
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_ForgottenPassword"/>
    </jsp:include>
    <div id="centerbody">
        <div id="page-content-title"><pwm:display key="Title_ForgottenPassword" displayIfMissing="true"/></div>
        <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
        <p>
            <pwm:display key="Display_RecoverVerificationChoice"/>
        </p>
        <table class="noborder">
            <colgroup>

            </colgroup>
            <% for (IdentityVerificationMethod method : methods) { %>
            <% if (method.isUserSelectable()) { %>
            <tr>
                <td>
                    <form action="<pwm:current-url/>" method="post" enctype="application/x-www-form-urlencoded" class="pwm-form" id="form-<%=method.toString()%>">
                        <button class="btn" type="submit" name="submitBtn">
                            <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-forward"></span></pwm:if>
                            <%=method.getLabel(pwmRequest.getConfig(),pwmRequest.getLocale())%>
                        </button>
                        <input type="hidden" name="choice" value="<%=method.toString()%>"/>
                        <input type="hidden" name="processAction" value="<%=ForgottenPasswordServlet.ForgottenPasswordAction.verificationChoice%>"/>
                        <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
                    </form>
                </td>
            </tr>
            <% } %>
            <% } %>
        </table>
        <br/>
        <div style="text-align:center;">
            <%@ include file="/WEB-INF/jsp/fragment/forgottenpassword-cancel.jsp" %>
        </div>
    </div>
    <div class="push"></div>
</div>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>

