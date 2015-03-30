<%@ page import="password.pwm.http.servlet.NewUserServlet" %>
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
<% JspUtility.setFlag(pageContext, PwmRequest.Flag.ALWAYS_EXPAND_MESSAGE_TEXT); %>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final PwmRequest pwmRequest = PwmRequest.forRequest(request,response); %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo">
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_NewUser"/>
    </jsp:include>
    <div id="centerbody">
        <p><pwm:display key="Display_NewUser"/></p>
        <%@ include file="fragment/message.jsp" %>
        <br/>
        <form action="<pwm:url url='NewUser'/>" method="post" name="newUser" enctype="application/x-www-form-urlencoded"
              id="newUserForm" class="pwm-form">
            <jsp:include page="fragment/form.jsp"/>
            <div class="buttonbar">
                <input type="hidden" name="processAction" value="processForm"/>
                <button type="submit" name="Create" class="btn" id="submitBtn">
                    <pwm:if test="showIcons"><span class="btn-icon fa fa-forward"></span></pwm:if>
                    <pwm:display key="Button_Continue"/>
                </button>
                <%@ include file="/WEB-INF/jsp/fragment/button-reset.jsp" %>
                <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>

                <% if (pwmRequest.getConfig().getNewUserProfiles().keySet().size() > 1) { %>
                <button type="button" id="button-goBack" name="button-goBack" class="btn" >
                    <pwm:if test="showIcons"><span class="btn-icon fa fa-backward"></span></pwm:if>
                    <pwm:display key="Button_GoBack"/>
                </button>
                <% } %>
                <pwm:if test="showCancel">
                    <button  type="submit" id="button-cancel" name="button-cancel" class="btn">
                        <pwm:if test="showIcons"><span class="btn-icon fa fa-times"></span></pwm:if>
                        <pwm:display key="Button_Cancel"/>
                    </button>
                </pwm:if>
            </div>
        </form>
    </div>
    <div class="push"></div>
</div>
<pwm:script>
    <script>
        PWM_GLOBAL['startupFunctions'].push(function(){
            PWM_MAIN.addEventHandler('newUserForm','input',function(){PWM_NEWUSER.validateNewUserForm()});
            PWM_MAIN.submitPostAction('button-goBack','NewUser','<%=NewUserServlet.NewUserAction.profileChoice%>');
            PWM_MAIN.submitPostAction('button-cancel','NewUser','<%=NewUserServlet.NewUserAction.reset%>');
        });
    </script>
</pwm:script>
<pwm:script-ref url="/public/resources/js/newuser.js"/>
<pwm:script-ref url="/public/resources/js/changepassword.js"/>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
