<%@ page import="password.pwm.error.PwmException" %>
<%@ page import="password.pwm.http.JspUtility" %>
<%--
  ~ Password Management Servlets (PWM)
  ~ http://code.google.com/p/pwm/
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2015 The PWM Project
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
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<%
    PwmRequest pwmRequest = null;
    try {
        pwmRequest = PwmRequest.forRequest(request, response);
    } catch (PwmException e) {
        JspUtility.logError(pageContext, "error during page setup: " + e.getMessage());
    }
%>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="../WEB-INF/jsp/fragment/header.jsp" %>
<body class="nihilo">
<div id="wrapper">
    <jsp:include page="../WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_MainPage"/>
    </jsp:include>
    <div id="centerbody">
        <table class="noborder">
            <pwm:if test="permission" arg1="CHANGE_PASSWORD">
                <tr>
                    <td class="menubutton_key">
                        <a id="button_ChangePassword" class="menubutton" href="<pwm:url url='ChangePassword'/>">
                            <pwm:if test="showIcons"><span class="btn-icon fa fa-key"></span></pwm:if>
                            <pwm:display key="Title_ChangePassword"/>
                        </a>
                    </td>
                    <td>
                        <p><pwm:display key="Long_Title_ChangePassword"/></p>
                    </td>
                </tr>
            </pwm:if>
            <pwm:if test="setupChallengeEnabled">
                <pwm:if test="permission" arg1="SETUP_RESPONSE">
                    <tr>
                        <td class="menubutton_key">
                            <a class="menubutton" href="<pwm:url url='SetupResponses'/>">
                                <pwm:if test="showIcons"><span class="btn-icon fa fa-list-ol"></span></pwm:if>
                                <pwm:display key="Title_SetupResponses"/>
                            </a>
                        </td>
                        <td>
                            <p><pwm:display key="Long_Title_SetupResponses"/></p>
                        </td>
                    </tr>
                </pwm:if>
            </pwm:if>
            <pwm:if test="otpEnabled">
                <pwm:if test="permission" arg1="SETUP_OTP_SECRET">
                    <tr>
                        <td class="menubutton_key">
                            <a class="menubutton" href="<pwm:url url='SetupOtp'/>">
                                <pwm:if test="showIcons"><span class="btn-icon fa fa-qrcode"></span></pwm:if>
                                <pwm:display key="Title_SetupOtpSecret"/>
                            </a>
                        </td>
                        <td>
                            <p><pwm:display key="Long_Title_SetupOtpSecret"/></p>
                        </td>
                    </tr>
                </pwm:if>
            </pwm:if>
            <pwm:if test="updateProfileEnabled">
                <pwm:if test="permission" arg1="PROFILE_UPDATE">
                    <tr>
                        <td class="menubutton_key">
                            <a class="menubutton" href="<pwm:url url='UpdateProfile'/>">
                                <pwm:if test="showIcons"><span class="btn-icon fa fa-edit"></span></pwm:if>
                                <pwm:display key="Title_UpdateProfile"/>
                            </a>
                        </td>
                        <td>
                            <p><pwm:display key="Long_Title_UpdateProfile"/></p>
                        </td>
                    </tr>
                </pwm:if>
            </pwm:if>
            <pwm:if test="shortcutsEnabled">
                <tr>
                    <td class="menubutton_key">
                        <a class="menubutton" href="<pwm:url url='Shortcuts'/>">
                            <pwm:if test="showIcons"><span class="btn-icon fa fa-external-link"></span></pwm:if>
                            <pwm:display key="Title_Shortcuts"/>
                        </a>
                    </td>
                    <td>
                        <p><pwm:display key="Long_Title_Shortcuts"/></p>
                    </td>
                </tr>
            </pwm:if>
            <pwm:if test="peopleSearchEnabled">
                <pwm:if test="permission" arg1="PEOPLE_SEARCH">
                    <tr>
                        <td class="menubutton_key">
                            <a class="menubutton" href="<pwm:url url='PeopleSearch'/>">
                                <pwm:if test="showIcons"><span class="btn-icon fa fa-search"></span></pwm:if>
                                <pwm:display key="Title_PeopleSearch"/>
                            </a>
                        </td>
                        <td>
                            <p><pwm:display key="Long_Title_PeopleSearch"/></p>
                        </td>
                    </tr>
                </pwm:if>
            </pwm:if>
            <pwm:if test="accountInfoEnabled">
                <tr>
                    <td class="menubutton_key">
                        <a class="menubutton" href="<pwm:url url='userinfo.jsp'/>">
                            <pwm:if test="showIcons"><span class="btn-icon fa fa-file-o"></span></pwm:if>
                            <pwm:display key="Title_UserInformation"/>
                        </a>
                    </td>
                    <td>
                        <p><pwm:display key="Long_Title_UserInformation"/></p>
                    </td>
                </tr>
            </pwm:if>
            <% if (pwmRequest.getPwmSession().getSessionManager().getHelpdeskProfile(pwmRequest.getPwmApplication()) != null) { %>
                <tr>
                    <td class="menubutton_key">
                        <a class="menubutton" href="<pwm:url url='Helpdesk'/>">
                            <pwm:if test="showIcons"><span class="btn-icon fa fa-user"></span></pwm:if>
                            <pwm:display key="Title_Helpdesk"/>
                        </a>
                    </td>
                    <td>
                        <p><pwm:display key="Long_Title_Helpdesk"/></p>
                    </td>
                </tr>
            <% } %>
            <% if (ContextManager.getPwmApplication(session).getConfig() != null && ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(PwmSetting.GUEST_ENABLE)) { %>
            <pwm:if test="permission" arg1="GUEST_REGISTRATION">
                <tr>
                    <td class="menubutton_key">
                        <a class="menubutton" href="<pwm:url url='GuestRegistration'/>">
                            <pwm:if test="showIcons"><span class="btn-icon fa fa-group"></span></pwm:if>
                            <pwm:display key="Title_GuestRegistration"/>
                        </a>
                    </td>
                    <td>
                        <p><pwm:display key="Long_Title_GuestRegistration"/></p>
                    </td>
                </tr>
            </pwm:if>
            <% } %>
            <pwm:if test="permission" arg1="PWMADMIN">
                <tr>
                    <td class="menubutton_key">
                        <a class="menubutton" href="<pwm:url url='/private/admin/Administration' addContext="true"/> ">
                            <pwm:if test="showIcons"><span class="btn-icon fa fa-dashboard"></span></pwm:if>
                            <pwm:display key="Title_Admin"/>
                        </a>
                    </td>
                    <td>
                        <p><pwm:display key="Long_Title_Admin"/></p>
                    </td>
                </tr>
            </pwm:if>
            <pwm:if test="showLogout">
                <tr>
                    <td class="menubutton_key">
                        <a class="menubutton" href="<pwm:url url='../public/Logout'/>">
                            <pwm:if test="showIcons"><span class="btn-icon fa fa-sign-out"></span></pwm:if>
                            <pwm:display key="Title_Logout"/>
                        </a>
                    </td>
                    <td>
                        <p><pwm:display key="Long_Title_Logout"/></p>
                    </td>
                </tr>
            </pwm:if>
        </table>
    </div>
    <div class="push"></div>
</div>
<%@ include file="../WEB-INF/jsp/fragment/footer.jsp" %>
</body>
</html>
