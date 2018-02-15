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

<%@ page import="password.pwm.http.JspUtility" %>
<%@ page import="password.pwm.http.PwmRequestAttribute" %>

<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<body class="nihilo">
<div id="wrapper" class="helpdesk-wrapper">
    <jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_Helpdesk"/>
    </jsp:include>
    <div id="centerbody" class="wide tall">
        <ui-view id="helpdesk-view"><div class="WaitDialogBlank"></div></ui-view>

        <noscript>
            <span><pwm:display key="Display_JavascriptRequired"/></span>
            <a href="<pwm:context/>"><pwm:display key="Title_MainPage"/></a>
        </noscript>
    </div>
    <div class="push"></div>
</div>

<pwm:script-ref url="/public/resources/webjars/angular/angular.min.js" />
<pwm:script-ref url="/public/resources/webjars/angular-aria/angular-aria.min.js" />
<pwm:script-ref url="/public/resources/webjars/angular-ui-router/release/angular-ui-router.min.js" />
<pwm:script-ref url="/public/resources/webjars/angular-translate/dist/angular-translate.min.js" />

<jsp:include page="/WEB-INF/jsp/fragment/footer.jsp"/>
<pwm:script-ref url="/public/resources/js/helpdesk.js"/>

<link rel="stylesheet" type="text/css" href="<pwm:url url='/public/resources/webjars/pwm-client/vendor/ias-icons.css' addContext="true"/>"/>
<link rel="stylesheet" type="text/css" href="<pwm:url url='/public/resources/webjars/pwm-client/vendor/ux-ias.css' addContext="true"/>"/>
<pwm:script-ref url="/public/resources/webjars/pwm-client/vendor/ng-ias.js" />
<pwm:script-ref url="/public/resources/webjars/pwm-client/helpdesk.ng.js" />
<pwm:script-ref url="/public/resources/js/changepassword.js"/>

</body>
</html>
