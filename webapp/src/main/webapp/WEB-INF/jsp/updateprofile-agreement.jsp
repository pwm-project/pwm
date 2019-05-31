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

<%@ page import="password.pwm.http.servlet.PwmServletDefinition" %>
<%@ page import="password.pwm.http.tag.conditional.PwmIfTest" %>
<%@ page import="password.pwm.http.PwmRequestAttribute" %>

<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo">
<div id="wrapper">
    <pwm:if test="<%=PwmIfTest.forcedPageView%>">
        <% JspUtility.setFlag(pageContext, PwmRequestFlag.HIDE_HEADER_BUTTONS); %>
    </pwm:if>
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_UpdateProfile"/>
    </jsp:include>
    <div id="centerbody">
        <h1 id="page-content-title"><pwm:display key="Title_UpdateProfile" displayIfMissing="true"/></h1>
        <%@ include file="fragment/message.jsp" %>
        <% final String expandedText = (String)JspUtility.getAttribute(pageContext, PwmRequestAttribute.AgreementText); %>
        <div class="agreementText"><%= expandedText %></div>
        <div class="buttonbar">
            <form action="<pwm:current-url/>" method="post" enctype="application/x-www-form-urlencoded">
                <%-- remove the next line to remove the "I Agree" checkbox --%>
                <label class="checkboxWrapper">
                    <input type="checkbox" id="agreeCheckBox"/>
                    <pwm:display key="Button_Agree"/>
                </label>
                <br/><br/>
                <input type="hidden" name="processAction"
                       value="agree"/>
                <button type="submit" name="button" class="btn" id="button_continue">
                    <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-forward"></span></pwm:if>
                    <pwm:display key="Button_Continue"/>
                </button>
                <input type="hidden" name="pwmFormID" id="pwmFormID" value="<pwm:FormID/>"/>
            </form>
            <pwm:if test="<%=PwmIfTest.forcedPageView%>">
                <form action="<pwm:url url='<%=PwmServletDefinition.Logout.servletUrl()%>' addContext="true"/>" method="post" enctype="application/x-www-form-urlencoded">
                    <button type="submit" name="button" class="btn" id="button_logout">
                        <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-sign-out"></span></pwm:if>
                        <pwm:display key="Button_Logout"/>
                    </button>
                    <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
                </form>
            </pwm:if>
            <pwm:if test="<%=PwmIfTest.forcedPageView%>" negate="true">
                <%@ include file="/WEB-INF/jsp/fragment/cancel-button.jsp" %>
                <%@ include file="/WEB-INF/jsp/fragment/cancel-form.jsp" %>
            </pwm:if>
        </div>
    </div>
    <div class="push"></div>
</div>
<pwm:script>
    <script type="text/javascript">
        function updateContinueButton() {
            var checkBox = PWM_MAIN.getObject("agreeCheckBox");
            var continueButton = PWM_MAIN.getObject("button_continue");
            if (checkBox != null && continueButton != null) {
                if (checkBox.checked) {
                    continueButton.removeAttribute('disabled');
                } else {
                    continueButton.disabled = "disabled";
                }
            }
        }


        PWM_GLOBAL['startupFunctions'].push(function(){
            PWM_MAIN.addEventHandler('agreeCheckBox','click, change',function(){ updateContinueButton() });
            updateContinueButton();
        });
    </script>
</pwm:script>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
