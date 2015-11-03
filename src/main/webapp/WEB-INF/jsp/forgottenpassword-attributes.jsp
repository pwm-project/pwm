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

<%@ page import="password.pwm.bean.SessionStateBean" %>
<%@ page import="password.pwm.config.FormConfiguration" %>
<%@ page import="password.pwm.http.bean.ForgottenPasswordBean" %>
<%@ page import="password.pwm.http.servlet.PwmServletDefinition" %>
<%@ page import="java.util.List" %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final PwmRequest pwmRequest = PwmRequest.forRequest(request, response); %>
<% final SessionStateBean ssBean = pwmRequest.getPwmSession().getSessionStateBean(); %>
<% final ForgottenPasswordBean recoverBean = pwmRequest.getPwmSession().getForgottenPasswordBean(); %>
<% final List<FormConfiguration> requiredAttrParams = recoverBean.getAttributeForm(); %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="fragment/header.jsp" %>
<%--
in the body onload below, the true parameter toggles the hide button an extra time to default the page to hiding the responses.
this is handled this way so on browsers where hiding fields is not possible, the default is to show the fields.
--%>
<body class="nihilo">
<div id="wrapper">
<jsp:include page="fragment/header-body.jsp">
    <jsp:param name="pwm.PageName" value="Title_RecoverPassword"/>
</jsp:include>
    <div id="centerbody">
        <p><pwm:display key="Display_RecoverPassword"/></p>

        <form name="responseForm" action="<pwm:current-url/>" method="post" enctype="application/x-www-form-urlencoded" class="pwm-form" autocomplete="off">
            <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>

            <% // loop through required attributes (challenge.requiredAttributes), if any are configured
                for (final FormConfiguration paramConfig : requiredAttrParams) {
            %>
            <h2><label for="attribute-<%= paramConfig.getName()%>"><%= paramConfig.getLabel(ssBean.getLocale()) %>
            </label></h2>
            <input type="<pwm:value name="responseFieldType"/>" name="<%= paramConfig.getName()%>" class="inputfield passwordfield" maxlength="255"
                    <pwm:autofocus/> id="attribute-<%= paramConfig.getName()%>" required="required" />
            <% } %>

            <div class="buttonbar">
                <input type="hidden" name="processAction" value="checkAttributes"/>
                <button type="submit" name="checkAttributes" class="btn" id="submitBtn">
                    <pwm:if test="showIcons"><span class="btn-icon fa fa-check"></span></pwm:if>
                    <pwm:display key="Button_RecoverPassword"/>
                </button>
                <% if ("true".equals(JspUtility.getAttribute(pageContext, PwmConstants.REQUEST_ATTR.ForgottenPasswordOptionalPageView))) { %>
                <button type="button" id="button-goBack" name="button-goBack" class="btn" >
                    <pwm:if test="showIcons"><span class="btn-icon fa fa-backward"></span></pwm:if>
                    <pwm:display key="Button_GoBack"/>
                </button>
                <% } %>
                <%@ include file="/WEB-INF/jsp/fragment/forgottenpassword-cancel.jsp" %>
                <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
            </div>
        </form>
    </div>
    <div class="push"></div>
</div>
<pwm:script>
    <script type="text/javascript">
        PWM_GLOBAL['startupFunctions'].push(function(){
            PWM_RESPONSES.startupResponsesPage();
            PWM_MAIN.addEventHandler('button_cancel', 'click', function(event){
                PWM_MAIN.handleFormSubmit(PWM_MAIN.getObject('button_cancel'),event);
            });
            PWM_MAIN.addEventHandler('button-goBack','click',function(){
                PWM_MAIN.submitPostAction('<%=PwmServletDefinition.ForgottenPassword.servletUrlName()%>','<%=ForgottenPasswordServlet.ForgottenPasswordAction.verificationChoice%>');
            });
        });
    </script>
</pwm:script>
<pwm:script-ref url="/public/resources/js/responses.js"/>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
