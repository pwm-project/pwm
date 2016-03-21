<%@ page import="password.pwm.http.JspUtility" %>
<%@ page import="password.pwm.http.servlet.PwmServletDefinition" %>
<%@ page import="password.pwm.http.tag.conditional.PwmIfTag" %>
<%@ page import="password.pwm.http.tag.conditional.PwmIfTest" %>
<%@ page import="password.pwm.*" %>
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
<%@ include file="../WEB-INF/jsp/fragment/header.jsp" %>
<body class="nihilo">
<div id="wrapper">
    <jsp:include page="../WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_MainPage"/>
    </jsp:include>

    <div id="centerbody" class="tile-centerbody">
        <pwm:if test="<%=PwmIfTest.configurationOpen%>" negate="true">
            <pwm:if test="<%=PwmIfTest.permission%>" permission="<%=Permission.CHANGE_PASSWORD%>">
                <a id="button_ChangePassword" href="<pwm:url addContext="true" url='<%=PwmServletDefinition.ChangePassword.servletUrl()%>'/>" style="font-weight: normal;">
                    <div class="tile">
                        <div class="tile-content">
                            <div class="tile-image password-image"></div>
                            <div class="tile-title"><pwm:display key="Title_ChangePassword"/></div>
                            <div class="tile-subtitle"><pwm:display key="Long_Title_ChangePassword"/></div>
                        </div>
                    </div>
                </a>
            </pwm:if>

            <pwm:if test="<%=PwmIfTest.peopleSearchEnabled%>">
                <pwm:if test="<%=PwmIfTest.permission%>" permission="<%=Permission.PEOPLE_SEARCH%>">
                    <a id="button_PeopleSearch" href="<pwm:url addContext="true" url='<%=PwmServletDefinition.PeopleSearch.servletUrl()%>'/>" style="font-weight: normal;">
                        <div class="tile">
                            <div class="tile-content">
                                <div class="tile-image search-image"></div>
                                <div class="tile-title"><pwm:display key="Title_PeopleSearch"/></div>
                                <div class="tile-subtitle"><pwm:display key="Long_Title_PeopleSearch"/></div>
                            </div>
                        </div>
                    </a>
                </pwm:if>
            </pwm:if>

            <pwm:if test="<%=PwmIfTest.setupChallengeEnabled%>">
                <pwm:if test="<%=PwmIfTest.permission%>" permission="<%=Permission.SETUP_RESPONSE%>">
                    <a id="button_SetupResponses" href="<pwm:url addContext="true" url='<%=PwmServletDefinition.SetupResponses.servletUrl()%>'/>" style="font-weight: normal;">
                        <div class="tile">
                            <div class="tile-content">
                                <div class="tile-image security-image"></div>
                                <div class="tile-title"><pwm:display key="Title_SetupResponses"/></div>
                                <div class="tile-subtitle"><pwm:display key="Long_Title_SetupResponses"/></div>
                            </div>
                        </div>
                    </a>
                </pwm:if>
            </pwm:if>

            <pwm:if test="<%=PwmIfTest.otpEnabled%>">
                <pwm:if test="<%=PwmIfTest.permission%>" permission="<%=Permission.SETUP_OTP_SECRET%>">
                    <a id="button_SetupOtpSecret" href="<pwm:url addContext="true" url='<%=PwmServletDefinition.SetupOtp.servletUrl()%>'/>" style="font-weight: normal;">
                        <div class="tile">
                            <div class="tile-content">
                                <div class="tile-image mobile-image"></div>
                                <div class="tile-title"><pwm:display key="Title_SetupOtpSecret"/></div>
                                <div class="tile-subtitle"><pwm:display key="Long_Title_SetupOtpSecret"/></div>
                            </div>
                        </div>
                    </a>
                </pwm:if>
            </pwm:if>

            <pwm:if test="<%=PwmIfTest.updateProfileAvailable%>">
                <a id="button_UpdateProfile" href="<pwm:url addContext="true" url='<%=PwmServletDefinition.UpdateProfile.servletUrl()%>'/>" style="font-weight: normal;">
                    <div class="tile">
                        <div class="tile-content">
                            <div class="tile-image profile-image"></div>
                            <div class="tile-title"><pwm:display key="Title_UpdateProfile"/></div>
                            <div class="tile-subtitle"><pwm:display key="Long_Title_UpdateProfile"/></div>
                        </div>
                    </div>
                </a>
            </pwm:if>

            <pwm:if test="<%=PwmIfTest.shortcutsEnabled%>">
                <a id="button_Shortcuts" href="<pwm:url addContext="true" url='<%=PwmServletDefinition.Shortcuts.servletUrl()%>'/>" style="font-weight: normal;">
                    <div class="tile">
                        <div class="tile-content">
                            <div class="tile-image shortcut-image"></div>
                            <div class="tile-title"><pwm:display key="Title_Shortcuts"/></div>
                            <div class="tile-subtitle"><pwm:display key="Long_Title_Shortcuts"/></div>
                        </div>
                    </div>
                </a>
            </pwm:if>

            <pwm:if test="<%=PwmIfTest.accountInfoEnabled%>">
                <a id="button_UserInformation" href="<pwm:url addContext="true" url='<%=PwmServletDefinition.AccountInformation.servletUrl()%>'/>" style="font-weight: normal;">
                    <div class="tile">
                        <div class="tile-content">
                            <div class="tile-image user-image"></div>
                            <div class="tile-title"><pwm:display key="Title_UserInformation"/></div>
                            <div class="tile-subtitle"><pwm:display key="Long_Title_UserInformation"/></div>
                        </div>
                    </div>
                </a>
            </pwm:if>

            <pwm:if test="<%=PwmIfTest.helpdeskAvailable%>">
                <a id="button_Helpdesk" href="<pwm:url addContext="true" url='<%=PwmServletDefinition.Helpdesk.servletUrl()%>'/>" style="font-weight: normal;">
                    <div class="tile">
                        <div class="tile-content">
                            <div class="tile-image support-image"></div>
                            <div class="tile-title"><pwm:display key="Title_Helpdesk"/></div>
                            <div class="tile-subtitle"><pwm:display key="Long_Title_Helpdesk"/></div>
                        </div>
                    </div>
                </a>
            </pwm:if>

            <pwm:if test="<%=PwmIfTest.guestRegistrationAvailable%>">
                <a id="button_GuestRegistration" href="<pwm:url url='<%=PwmServletDefinition.GuestRegistration.servletUrl()%>' addContext="true"/>" style="font-weight: normal;">
                    <div class="tile">
                        <div class="tile-content">
                            <div class="tile-image guest-image"></div>
                            <div class="tile-title"><pwm:display key="Title_GuestRegistration"/></div>
                            <div class="tile-subtitle"><pwm:display key="Long_Title_GuestRegistration"/></div>
                        </div>
                    </div>
                </a>
            </pwm:if>
        </pwm:if>

        <pwm:if test="<%=PwmIfTest.permission%>" permission="<%=Permission.PWMADMIN%>">
            <a id="button_Admin" href="<pwm:url url='<%=PwmServletDefinition.Admin.servletUrl()%>' addContext="true"/> " style="font-weight: normal;">
                <div class="tile">
                    <div class="tile-content">
                        <div class="tile-image admin-image"></div>
                        <div class="tile-title"><pwm:display key="Title_Admin"/></div>
                        <div class="tile-subtitle"><pwm:display key="Long_Title_Admin"/></div>
                    </div>
                </div>
            </a>
            <a id="button_Admin" href="<pwm:url url='<%=PwmServletDefinition.ConfigManager.servletUrl()%>' addContext="true"/> " style="font-weight: normal;">
                <div class="tile">
                    <div class="tile-content">
                        <div class="tile-image configmanager-image"></div>
                        <div class="tile-title"><pwm:display key="Title_ConfigManager" bundle="Config"/></div>
                    </div>
                </div>
            </a>
        </pwm:if>
    </div>
    <div class="push"></div>
</div>
<%@ include file="../WEB-INF/jsp/fragment/footer.jsp" %>
</body>
</html>
