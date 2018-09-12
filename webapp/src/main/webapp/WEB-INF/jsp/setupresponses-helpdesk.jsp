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

<%@ page import="password.pwm.http.bean.SetupResponsesBean" %>
<%@ page import="password.pwm.http.tag.conditional.PwmIfTest" %>
<%@ page import="password.pwm.http.PwmRequestAttribute" %>
<!DOCTYPE html>

<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final SetupResponsesBean responseBean = (SetupResponsesBean)JspUtility.getAttribute(pageContext, PwmRequestAttribute.ModuleBean); %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo">
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_SetupResponses"/>
    </jsp:include>
    <div id="centerbody">
        <h1 id="page-content-title"><pwm:display key="Title_SetupResponses" displayIfMissing="true"/></h1>
        <p><pwm:display key="Display_SetupHelpdeskResponses"/></p>
        <form action="<pwm:current-url/>" method="post" name="form-setupResponses"
              enctype="application/x-www-form-urlencoded" id="form-setupResponses" class="pwm-form" autocomplete="off">
            <%@ include file="fragment/message.jsp" %>
            <div id="pwm-setupResponsesDiv">
                <% request.setAttribute("setupData",responseBean.getHelpdeskResponseData()); %>
                <jsp:include page="fragment/setupresponses-form.jsp"/>
            </div>
            <div class="buttonbar">
                <input type="hidden" name="processAction" value="setHelpdeskResponses"/>
                <button type="submit" name="setResponses" class="btn" id="button-setResponses">
                    <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-forward"></span></pwm:if>
                    <pwm:display key="Button_SetResponses"/>
                </button>
                <input type="hidden" id="pwmFormID" name="pwmFormID" value="<pwm:FormID/>"/>
            </div>
        </form>
    </div>
    <div class="push"></div>
</div>
<pwm:script>
    <script type="text/javascript">
        PWM_GLOBAL['responseMode'] = "helpdesk";
        PWM_GLOBAL['startupFunctions'].push(function(){
            PWM_RESPONSES.startupResponsesPage();
        });
    </script>
</pwm:script>
<pwm:script-ref url="/public/resources/js/responses.js"/>
<%@ include file="/WEB-INF/jsp/fragment/cancel-form.jsp" %>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
