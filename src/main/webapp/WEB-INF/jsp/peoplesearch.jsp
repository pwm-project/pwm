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
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<body class="nihilo">
<div id="wrapper" class="peoplesearch-wrapper">
    <jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_PeopleSearch"/>
    </jsp:include>
    <div id="centerbody" class="wide tall" style="height:100%">
        <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>

        <ui-view id="people-search-view"><div class="WaitDialogBlank"></div></ui-view>
    </div>
    <div class="push"></div>
</div>

<pwm:script-ref url="/public/resources/webjars/angular/angular.min.js" />
<pwm:script-ref url="/public/resources/webjars/angular-ui-router/release/angular-ui-router.min.js" />
<pwm:script-ref url="/public/resources/webjars/angular-translate/dist/angular-translate.min.js" />

<%@ include file="fragment/footer.jsp" %>
<pwm:script-ref url="/public/resourcess/js/peoplesearch.js" />
<pwm:script-ref url="/public/resourcess/app/peoplesearch.ng.js" />

</body>
</html>
