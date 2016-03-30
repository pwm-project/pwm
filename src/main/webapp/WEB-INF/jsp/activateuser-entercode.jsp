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
<%@ page import="password.pwm.http.bean.ActivateUserBean" %>
<%@ page import="password.pwm.http.tag.conditional.PwmIfTest" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<%@ include file="fragment/header.jsp" %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<body class="nihilo">
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_ActivateUser"/>
    </jsp:include>
    <div id="centerbody">
        <div id="page-content-title"><pwm:display key="Title_ActivateUser" displayIfMissing="true"/></div>
        <% final ActivateUserBean activateUserBean = JspUtility.getSessionBean(pageContext, ActivateUserBean.class); %>
        <% String destination = activateUserBean.getTokenDisplayText(); %>
        <p><pwm:display key="Display_RecoverEnterCode" value1="<%=destination%>"/></p>
        <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
        <h2><label for="<%=PwmConstants.PARAM_TOKEN%>"><pwm:display key="Field_Code"/></label></h2>
        <div class="buttonbar">
            <form action="<pwm:current-url/>" method="post" enctype="application/x-www-form-urlencoded" name="search" class="pwm-form" style="display: inline;">
                <textarea id="<%=PwmConstants.PARAM_TOKEN%>" name="<%=PwmConstants.PARAM_TOKEN%>" class="tokenInput"></textarea>
                <button type="submit" class="btn" name="search" id="submitBtn">
                    <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-check"></span>&nbsp</pwm:if>
                    <pwm:display key="Button_CheckCode"/>
                </button>
                <input type="hidden" id="processAction" name="processAction" value="enterCode"/>
                <input type="hidden" id="pwmFormID" name="pwmFormID" value="<pwm:FormID/>"/>
            </form>
            <pwm:if test="<%=PwmIfTest.showCancel%>">
                <form action="<pwm:current-url/>" method="post" enctype="application/x-www-form-urlencoded" style="display: inline;">
                    <input type="hidden" name="processAction" value="reset"/>
                    <button type="submit" name="button" class="btn" id="buttonCancel">
                        <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-times"></span>&nbsp</pwm:if>
                        <pwm:display key="Button_Cancel"/>
                    </button>
                    <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
                </form>
            </pwm:if>
        </div>
    </div>
    <div class="push"></div>
</div>
<pwm:script>
<script type="text/javascript">
    PWM_GLOBAL['startupFunctions'].push(function(){
        PWM_MAIN.getObject('<%=PwmConstants.PARAM_TOKEN%>').focus();
    });
</script>
</pwm:script>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>

