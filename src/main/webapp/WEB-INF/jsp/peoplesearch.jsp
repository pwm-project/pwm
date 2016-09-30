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

        <ui-view id="people-search-view">Loading...</ui-view>
    </div>
    <div class="push"></div>
</div>

<%-- TODO: change these to the 'min' versions (i.e. angular.min.js) --%>
<pwm:script-ref url="/public/resources/systemjs/dist/system.js" />

<%--
Note: I'd rather access angular from the typescript code using: import "angular", and import "angular-ui-router", but
angular-ui-router kept having problems, so I just hard coded the paths in script tags here: --%>
<pwm:script-ref url="/public/resources/angular/angular.js" />
<pwm:script-ref url="/public/resources/angular-ui-router/release/angular-ui-router.js" />

<%@ include file="fragment/footer.jsp" %>
<pwm:script-ref url="/public/resources/js/peoplesearch.js" />

<pwm:script>
    <script>
    System.config({
        defaultJSExtensions: true,
        map: {
            "angular": "<pwm:url addContext='true' url='/public/resources/angular/angular.js' />",
            "angular-ui-router": "<pwm:url addContext='true' url='/public/resources/angular-ui-router/release/angular-ui-router.js' />"
        }
    });

    System.import("<pwm:url addContext='true' url='/public/resources/app/peoplesearch.main.js' />");
    </script>
</pwm:script>

</body>
</html>
