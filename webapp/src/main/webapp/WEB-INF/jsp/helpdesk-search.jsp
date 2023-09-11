<%--
 ~ Password Management Servlets (PWM)
 ~ http://www.pwm-project.org
 ~
 ~ Copyright (c) 2006-2009 Novell, Inc.
 ~ Copyright (c) 2009-2021 The PWM Project
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

<%@ page import="password.pwm.http.PwmRequestAttribute" %>
<%@ page import="password.pwm.http.JspUtility" %>
<%@ page import="password.pwm.http.PwmRequestAttribute" %>

<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<body>
<div id="wrapper" class="helpdesk-wrapper">
    <jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_Helpdesk"/>
    </jsp:include>
    <div id="centerbody" class="tall">
        <div id="page-content-title"><pwm:display key="Title_Helpdesk" displayIfMissing="true"/></div>
        <div id="panel-searchbar" class="searchbar">
            <input id="username" name="username" placeholder="<pwm:display key="Placeholder_Search"/>"
                   class="helpdesk-input-username" <pwm:autofocus/>/>
            <div class="searchbar-extras">
                <div id="searchIndicator" class="hidden">
                    <span class="pwm-icon pwm-icon-lg pwm-icon-spin pwm-icon-spinner"></span>
                </div>

                <div id="maxResultsIndicator" class="hidden">
                    <span class="pwm-icon pwm-icon-lg pwm-icon-exclamation-circle"></span>
                </div>

                <% if ((Boolean)JspUtility.getPwmRequest(pageContext).getAttribute( PwmRequestAttribute.HelpdeskVerificationEnabled)) { %>
                <div id="verifications-btn">
                    <button class="btn" id="button-show-current-verifications">
                        <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-check"></span></pwm:if>
                        <pwm:display key="Button_Verifications"/>
                    </button>
                </div>
                <% } %>
            </div>

            <span>
                Cards
                <input type="checkbox" id="helpdesk-search-result-mode"/><label for="helpdesk-search-result-mode">&nbsp;</label></input>
                Grid
            </span>
            <noscript>
                <span><pwm:display key="Display_JavascriptRequired"/></span>
                <a href="<pwm:context/>"><pwm:display key="Title_MainPage"/></a>
            </noscript>
        </div>
        <div id="helpdesk-searchResultsGrid">
        </div>
    </div>
    <div class="push"></div>
</div>
<jsp:include page="/WEB-INF/jsp/fragment/footer.jsp"/>

<link href="<pwm:url url='/public/resources/helpdesk.css' addContext="true"/>" rel="stylesheet" type="text/css" media="screen"/>

<script type="module" nonce="<pwm:value name="<%=PwmValue.cspNonce%>"/>">
    import {PWM_HELPDESK} from "<pwm:url url="/public/resources/js/helpdesk.js" addContext="true"/>";
    PWM_HELPDESK.initHelpdeskSearchPage();
</script>

</body>
</html>
