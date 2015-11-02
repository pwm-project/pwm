<%@ page import="password.pwm.http.JspUtility" %>
<%@ page import="password.pwm.http.servlet.PwmServletDefinition" %>
<%@ page import="password.pwm.http.tag.PwmIfTag" %>
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
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
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
                        <a id="button_ChangePassword" class="menubutton" href="<pwm:url addContext="true" url='<%=PwmServletDefinition.ChangePassword.servletUrl()%>'/>">
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
                            <a class="menubutton" href="<pwm:url addContext="true" url='<%=PwmServletDefinition.SetupResponses.servletUrl()%>'/>">
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
                            <a class="menubutton" href="<pwm:url addContext="true" url='<%=PwmServletDefinition.SetupOtp.servletUrl()%>'/>">
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
                            <a class="menubutton" href="<pwm:url addContext="true" url='<%=PwmServletDefinition.UpdateProfile.servletUrl()%>'/>">
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
                        <a class="menubutton" href="<pwm:url addContext="true" url='<%=PwmServletDefinition.Shortcuts.servletUrl()%>'/>">
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
                            <a class="menubutton" href="<pwm:url addContext="true" url='<%=PwmServletDefinition.PeopleSearch.servletUrl()%>'/>">
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
            <pwm:if test="<%=PwmIfTag.TESTS.accountInfoEnabled.toString()%>">
                <tr>
                    <td class="menubutton_key">
                        <a class="menubutton" href="<pwm:url addContext="true" url='<%=PwmServletDefinition.AccountInformation.servletUrl()%>'/>">
                            <pwm:if test="showIcons"><span class="btn-icon fa fa-file-o"></span></pwm:if>
                            <pwm:display key="Title_UserInformation"/>
                        </a>
                    </td>
                    <td>
                        <p><pwm:display key="Long_Title_UserInformation"/></p>
                    </td>
                </tr>
            </pwm:if>
            <% if (JspUtility.getPwmRequest(pageContext).getPwmSession().getSessionManager().getHelpdeskProfile(JspUtility.getPwmRequest(pageContext).getPwmApplication()) != null) { %>
                <tr>
                    <td class="menubutton_key">
                        <a class="menubutton" href="<pwm:url addContext="true" url='<%=PwmServletDefinition.Helpdesk.servletUrl()%>'/>">
                            <pwm:if test="showIcons"><span class="btn-icon fa fa-user"></span></pwm:if>
                            <pwm:display key="Title_Helpdesk"/>
                        </a>
                    </td>
                    <td>
                        <p><pwm:display key="Long_Title_Helpdesk"/></p>
                    </td>
                </tr>
            <% } %>
            <% if (JspUtility.getPwmRequest(pageContext).getConfig() != null && JspUtility.getPwmRequest(pageContext).getConfig().readSettingAsBoolean(PwmSetting.GUEST_ENABLE)) { %>
            <pwm:if test="permission" arg1="GUEST_REGISTRATION">
                <tr>
                    <td class="menubutton_key">
                        <a class="menubutton" href="<pwm:url url='<%=PwmServletDefinition.GuestRegistration.servletUrl()%>' addContext="true"/>">
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
                        <a class="menubutton" href="<pwm:url url='<%=PwmServletDefinition.Admin.servletUrl()%>' addContext="true"/> ">
                            <pwm:if test="showIcons"><span class="btn-icon fa fa-dashboard"></span></pwm:if>
                            <pwm:display key="Title_Admin"/>
                        </a>
                    </td>
                    <td>
                        <p><pwm:display key="Long_Title_Admin"/></p>
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
