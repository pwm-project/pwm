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


<%--  This file is imported by most JSPs, it shows the main 'header' in the html --%>
<%@ page import="password.pwm.http.tag.value.PwmValue" %>
<%@ page import="password.pwm.http.JspUtility" %>
<%@ page import="password.pwm.http.PwmRequestFlag" %>
<%@ page import="password.pwm.http.tag.conditional.PwmIfTest" %>
<%@ taglib uri="pwm" prefix="pwm" %>

<pwm:if test="<%=PwmIfTest.headerMenuIsVisible%>">
    <%@ include file="header-menu.jsp" %>
</pwm:if>
<div id="header">
    <div id="header-company-logo">
    </div>
    <div id="header-balance-div">
        <br/><%-- balance div for ie 6 --%>
    </div>
    <div id="header-right-logo">
    </div>
    <div id="header-center">
        <div id="header-center-left">
            <div id="header-page"><pwm:display key="${param['pwm.PageName']}" displayIfMissing="true"/></div>
            <div id="header-title">
                <span class="title-long"><pwm:display key="Title_Application"/></span>
                <span class="title-short"><pwm:display key="Title_Application_Abbrev"/></span>
            </div>
        </div>

        <div id="header-center-right">
            <div id="header-menu-wrapper">
                <div id="header-menu">
                    <pwm:if test="<%=PwmIfTest.permission%>" permission="<%=Permission.PWMADMIN%>">
                        <div id="header-menu-alert" class="pwm-icon pwm-icon-warning display-none" title="<pwm:display key="Header_HealthWarningsPresent" bundle="Admin"/>"></div>
                    </pwm:if>
                    <div id="header-username-group">
                        <pwm:if test="<%=PwmIfTest.authenticated%>">
                            <div id="header-username"><pwm:display key="Display_UsernameHeader"/></div>
                        </pwm:if>
                        <pwm:if test="<%=PwmIfTest.headerMenuIsVisible%>">
                            <div id="header-username-caret" class="pwm-icon pwm-icon-chevron-down"></div>
                        </pwm:if>
                    </div>
                </div>

                <% if (!JspUtility.isFlag(request, PwmRequestFlag.HIDE_HEADER_BUTTONS)) { %>
                    <pwm:if test="<%=PwmIfTest.authenticated%>">
                        <pwm:if test="<%=PwmIfTest.showHome%>">
                            <pwm:if test="<%=PwmIfTest.forcedPageView%>" negate="true">
                                <a class="header-button" href="<pwm:value name="<%=PwmValue.homeURL%>"/>" id="HomeButton">
                                    <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="pwm-icon pwm-icon-home" title="<pwm:display key="Button_Home"/>"></span></pwm:if>
                                </a>
                            </pwm:if>
                        </pwm:if>
                        <pwm:if test="<%=PwmIfTest.showLogout%>">
                            <a class="header-button" href="<pwm:url url='<%=PwmServletDefinition.Logout.servletUrl()%>' addContext="true"/>" id="LogoutButton">
                                <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="pwm-icon pwm-icon-sign-out" title="<pwm:display key="Button_Logout"/>"></span></pwm:if>
                            </a>
                        </pwm:if>
                    </pwm:if>
                <% } %>
            </div>
        </div>
    </div>
</div>
<% if (request.getParameter("debug") != null) { %>
<%@ include file="debug.jsp" %>
<% } %>
