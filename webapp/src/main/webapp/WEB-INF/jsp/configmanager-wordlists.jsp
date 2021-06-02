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


<%@ page import="password.pwm.http.JspUtility" %>
<%@ page import="password.pwm.http.tag.conditional.PwmIfTest" %>
<%@ page import="password.pwm.i18n.Config" %>
<%@ page import="password.pwm.util.i18n.LocaleHelper" %>

<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.INCLUDE_CONFIG_CSS);%>
<%@ taglib uri="pwm" prefix="pwm" %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="fragment/header.jsp" %>
<body>
<link href="<pwm:context/><pwm:url url='/public/resources/configmanagerStyle.css'/>" rel="stylesheet" type="text/css"/>
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="<%=LocaleHelper.getLocalizedMessage(Config.Title_ConfigManager, JspUtility.getPwmRequest(pageContext))%>"/>
    </jsp:include>
    <div id="centerbody">
        <h1 id="page-content-title"><%=LocaleHelper.getLocalizedMessage(Config.Title_ConfigManager, JspUtility.getPwmRequest(pageContext))%></h1>
        <%@ include file="fragment/configmanager-nav.jsp" %>
        <% { %>
        <table style="width:550px" id="table-wordlistInfo">
        </table>
        <br/>
        <table class="noborder">
            <tr class="buttonrow">
                <td class="buttoncell">
                    <button class="menubutton" id="MenuItem_UploadWordlist" style="visibility: hidden;">
                        <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-upload"></span></pwm:if>
                        Upload Word List
                    </button>
                </td>
                <td class="buttoncell">
                    <button class="menubutton" id="MenuItem_ClearWordlist" style="visibility: hidden;">
                        <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-trash"></span></pwm:if>
                        Clear Word List
                    </button>
                </td>
            </tr>
        </table>
        <% } %>
        <br/><br/><br/><br/><br/>
        <% { %>
        <table style="width:550px" id="table-seedlistInfo">
        </table>
        <br/>
        <table class="noborder">
            <tr class="buttonrow">
                <td class="buttoncell">
                    <button class="menubutton" id="MenuItem_UploadSeedlist" style="visibility: hidden;">
                        <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-upload"></span></pwm:if>
                        Upload Seed List
                    </button>
                </td>
                <td class="buttoncell">
                    <button class="menubutton" id="MenuItem_ClearSeedlist" style="visibility: hidden;">
                        <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-trash"></span></pwm:if>
                        Clear Seed List
                    </button>
                </td>
            </tr>
        </table>
        <% } %>
        <div style="margin: 25px">
            <span class="footnote"><pwm:display key="Display_Wordlists_Description" bundle="Config"/></span>
        </div>
    </div>
    <div class="push"></div>
</div>
<pwm:script>
    <script type="text/javascript">

        PWM_GLOBAL['startupFunctions'].push(function () {
            PWM_CONFIG.initConfigManagerWordlistPage();
        });

    </script>
</pwm:script>
<pwm:script-ref url="/public/resources/js/configmanager.js"/>
<pwm:script-ref url="/public/resources/js/uilibrary.js"/>
<pwm:script-ref url="/public/resources/js/admin.js"/>
<div><%@ include file="fragment/footer.jsp" %></div>
</body>
</html>
