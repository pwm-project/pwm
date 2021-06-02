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


<%@ page import="password.pwm.error.PwmException" %>
<%@ page import="password.pwm.http.JspUtility" %>
<%@ page import="java.util.Collections" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Locale" %>

<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<%
    List<Locale> localeList = Collections.emptyList();
    PwmApplication localeselect_pwmApplication = null;
    try {
        localeselect_pwmApplication = PwmRequest.forRequest(request, response).getPwmApplication();
        localeList = localeselect_pwmApplication.getConfig().getKnownLocales();
    } catch (PwmException e) {
        /* noop */
    }
%>
<body>
<div id="wrapper">
    <jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_LocaleSelect"/>
    </jsp:include>
    <div id="centerbody">
        <div style="margin-left: auto; margin-right: auto; padding: 30px">
            <table class="noborder" style="width: auto">
                <% for (final Locale locale : localeList) { %>
                <tr>
                    <td>
                        <% final String flagCode = localeselect_pwmApplication.getConfig().getKnownLocaleFlagMap().get(locale); %>
                        <img alt="flag" src="<pwm:context/><pwm:url url='/public/resources/flags/png/'/><%=flagCode%>.png"/>
                    </td>
                    <td>
                        <a href="<pwm:context/>?<%=localeselect_pwmApplication.getConfig().readAppProperty(password.pwm.AppProperty.HTTP_PARAM_NAME_LOCALE)%>=<%=locale.toString()%>">
                            <%=locale.getDisplayName()%> - <%=locale.getDisplayName(locale)%>
                        </a>
                    </td>
                </tr>
                <% } %>
            </table>
        </div>
    </div>
    <div class="push"></div>
</div>
<pwm:script>
    <script type="text/javascript">
        PWM_GLOBAL['startupFunctions'].push(function(){
        });
    </script>
</pwm:script>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.HIDE_LOCALE); %>
<%@ include file="/WEB-INF/jsp/fragment/footer.jsp" %>
</body>
</html>
