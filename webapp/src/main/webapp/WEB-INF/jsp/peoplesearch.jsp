<%--
 ~ Password Management Servlets (PWM)
 ~ http://www.pwm-project.org
 ~
 ~ Copyright (c) 2006-2009 Novell, Inc.
 ~ Copyright (c) 2009-2020 The PWM Project
 ~
 ~ Licensed under the Apache License, Version 2.0 (the "License");
 ~ you may not use this file except in compliance with the License.
 ~ You may obtain a copy of the License at
 ~
 ~     http://www.apache.org/licenses/LICENSE-2.0
 ~
 ~ Unless required by applicable law or agreed to in writing, software
 ~ distributed under the License is distributed on an "AS IS" BASIS,
 ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 ~ See the License for the specific language governing permissions and
 ~ limitations under the License.
--%>
<%--
       THIS FILE IS NOT INTENDED FOR END USER MODIFICATION.
       See the README.TXT file in WEB-INF/jsp before making changes.
--%>


<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<head>
    <%@ include file="/WEB-INF/jsp/fragment/header-common.jsp" %>
    <link rel="stylesheet" type="text/css" href="<pwm:url url='/public/resources/webjars/pwm-client/vendor/ux-ias/ias-icons.css' addContext="true"/>"/>
</head>
<body class="nihilo printable">
<div id="wrapper" class="peoplesearch-wrapper">
    <jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_PeopleSearch"/>
    </jsp:include>
    <div id="centerbody" class="wide tall" style="height:100%">
        <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>

        <ui-view id="people-search-view" class="ias-styles-root"><div class="WaitDialogBlank"></div></ui-view>
    </div>
    <div class="push"></div>
</div>

<%@ include file="fragment/footer.jsp" %>

<pwm:script-ref url="/public/resources/webjars/pwm-client/vendor.js" />
<pwm:script-ref url="/public/resources/webjars/pwm-client/peoplesearch.ng.js" />

</body>
</html>
