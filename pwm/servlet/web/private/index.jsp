<%@ page import="password.pwm.config.Configuration" %>
<%@ page import="password.pwm.config.PwmSetting" %>
<%@ page import="password.pwm.Permission" %>
<%--
~ Password Management Servlets (PWM)
~ http://code.google.com/p/pwm/
~
~ Copyright (c) 2006-2009 Novell, Inc.
~ Copyright (c) 2009-2010 The PWM Project
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

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html xmlns="http://www.w3.org/1999/xhtml" dir="<pwm:LocaleOrientation/>">
<%@ include file="../WEB-INF/jsp/fragment/header.jsp" %>
<body onload="pwmPageLoadHandler();" class="tundra">
<div id="wrapper">
    <jsp:include page="../WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_MainPage"/>
    </jsp:include>
    <div id="centerbody">
        <br/>
        <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
        <table style="border:0">
            <% if (Permission.checkPermission(Permission.CHANGE_PASSWORD, PwmSession.getPwmSession(request), ContextManager.getPwmApplication(session))) { %>
            <tr>
                <td style="border:0; width: 25%; white-space: nowrap; height:50px">
                    <a href="<pwm:url url='ChangePassword'/>">
                        <button style="float: right; padding: 7px" class="btn">
                            <pwm:Display key="Title_ChangePassword"/>
                        </button>
                    </a>
                    <br/>
                    <br/>
                </td>
                <td style="border:0;">
                    <pwm:Display key="Long_Title_ChangePassword"/>
                </td>
            </tr>
            <% } %>
            <% if (ContextManager.getPwmApplication(session).getConfig() != null && ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(PwmSetting.CHALLENGE_ENABLE)) { %>
            <% if (Permission.checkPermission(Permission.SETUP_RESPONSE, PwmSession.getPwmSession(request), ContextManager.getPwmApplication(session))) { %>
            <tr>
                <td style="border:0; width: 25%; white-space: nowrap; height:50px">
                    <a href="<pwm:url url='SetupResponses'/>">
                                                <button style="float: right; padding: 7px" class="btn"> 
                            <pwm:Display key="Title_SetupResponses"/>
                        </button>
                    </a>
                    <br/>
                    <br/>
                </td>
                <td style="border:0;">
                    <pwm:Display key="Long_Title_SetupResponses"/>
                </td>
            </tr>
            <% } %>
            <% } %>
            <% if (ContextManager.getPwmApplication(session).getConfig() != null && ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(PwmSetting.UPDATE_PROFILE_ENABLE)) { %>
            <tr>
                <td style="border:0; width: 25%; white-space: nowrap; height:50px">
                    <a href="<pwm:url url='UpdateProfile'/>">
                                                <button style="float: right; padding: 7px" class="btn"> 
                            <pwm:Display key="Title_UpdateProfile"/>
                        </button>
                    </a>
                    <br/>
                    <br/>
                </td>
                <td style="border:0;">
                    <pwm:Display key="Long_Title_UpdateProfile"/>
                </td>
            </tr>
            <% } %>
            <% if (ContextManager.getPwmApplication(session).getConfig() != null && ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(PwmSetting.DISPLAY_PASSWORD_HISTORY)) { %>
            <tr>
                <td style="border:0; width: 25%; white-space: nowrap; height:50px">
                    <a href="<pwm:url url='history.jsp'/>">
                                                <button style="float: right; padding: 7px" class="btn"> 
                            <pwm:Display key="Title_UserEventHistory"/>
                        </button>
                    </a>
                    <br/>
                    <br/>
                </td>
                <td style="border:0;">
                    <pwm:Display key="Long_Title_UserEventHistory"/>
                </td>
            </tr>
            <% } %>
            <% if (ContextManager.getPwmApplication(session).getConfig() != null && ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(PwmSetting.SHORTCUT_ENABLE)) { %>
            <tr>
                <td style="border:0; width: 25%; white-space: nowrap; height:50px">
                    <a href="<pwm:url url='Shortcuts'/>">
                                                <button style="float: right; padding: 7px" class="btn"> 
                            <pwm:Display key="Title_Shortcuts"/>
                        </button>
                    </a>
                    <br/>
                    <br/>
                </td>
                <td style="border:0;">
                    <pwm:Display key="Long_Title_Shortcuts"/>
                </td>
            </tr>
            <% } %>
            <% if (ContextManager.getPwmApplication(session).getConfig() != null && ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(PwmSetting.PEOPLE_SEARCH_ENABLE)) { %>
            <% if (Permission.checkPermission(Permission.PEOPLE_SEARCH, PwmSession.getPwmSession(request), ContextManager.getPwmApplication(session))) { %>
            <tr>
                <td style="border:0; width: 25%; white-space: nowrap; height:50px">
                    <a href="<pwm:url url='PeopleSearch'/>">
                                                <button style="float: right; padding: 7px" class="btn"> 
                            <pwm:Display key="Title_PeopleSearch"/>
                        </button>
                    </a>
                    <br/>
                    <br/>
                </td>
                <td style="border:0;">
                    <pwm:Display key="Long_Title_PeopleSearch"/>
                </td>
            </tr>
            <% } %>
            <% } %>
            <% if (ContextManager.getPwmApplication(session).getConfig() != null && ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(PwmSetting.DISPLAY_ACCOUNT_INFORMATION)) { %>
            <tr>
                <td style="border:0; width: 25%; white-space: nowrap; height:50px">
                    <a href="<pwm:url url='userinfo.jsp'/>">
                                                <button style="float: right; padding: 7px" class="btn"> 
                            <pwm:Display key="Title_UserInformation"/>
                        </button>
                    </a>
                    <br/>
                    <br/>
                </td>
                <td style="border:0;">
                    <pwm:Display key="Long_Title_UserInformation"/>
                </td>
            </tr>
            <% } %>
            <% if (ContextManager.getPwmApplication(session).getConfig() != null && ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(PwmSetting.HELPDESK_ENABLE)) { %>
            <% if (Permission.checkPermission(Permission.HELPDESK, PwmSession.getPwmSession(request), ContextManager.getPwmApplication(session))) { %>
            <tr>
                <td style="border:0; width: 25%; white-space: nowrap; height:50px">
                    <a href="<pwm:url url='Helpdesk'/>">
                                                <button style="float: right; padding: 7px" class="btn"> 
                            <pwm:Display key="Title_Helpdesk"/>
                        </button>
                    </a>
                    <br/>
                    <br/>
                </td>
                <td style="border:0;">
                    <pwm:Display key="Long_Title_Helpdesk"/>
                </td>
            </tr>
            <% } %>
            <% } %>
            <% if (ContextManager.getPwmApplication(session).getConfig() != null && ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(PwmSetting.GUEST_ENABLE)) { %>
            <% if (Permission.checkPermission(Permission.GUEST_REGISTRATION, PwmSession.getPwmSession(request), ContextManager.getPwmApplication(session))) { %>
            <tr>
                <td style="border:0; width: 25%; height:50px">
                    <a href="<pwm:url url='GuestRegistration'/>">
                                                <button style="float: right; padding: 7px" class="btn"> 
                            <pwm:Display key="Title_GuestRegistration"/> & <pwm:Display key="Title_GuestUpdate"/>
                        </button>
                    </a>
                    <br/>
                    <br/>
                </td>
                <td style="border:0;">
                    <pwm:Display key="Long_Title_GuestRegistration"/>
                </td>
            </tr>
            <% } %>
            <% } %>
            <% if (Permission.checkPermission(Permission.PWMADMIN, PwmSession.getPwmSession(request), ContextManager.getPwmApplication(session))) { %>
            <tr>
                <td style="border:0; width: 25%; white-space: nowrap; height:50px">
                    <a href="<pwm:url url='admin/status.jsp'/>">
                        <button style="float: right; padding: 7px" class="btn">
                            <pwm:Display key="Title_Admin"/>
                        </button>
                    </a>
                    <br/>
                    <br/>
                </td>
                <td style="border:0;">
                    <pwm:Display key="Long_Title_Admin"/>
                </td>
            </tr>
            <% } %>
            <tr>
                <td style="border:0; width: 25%; white-space: nowrap; height:50px">
                    <a href="<pwm:url url='../public/Logout'/>">
                                                <button style="float: right; padding: 7px" class="btn"> 
                            <pwm:Display key="Title_Logout"/>
                        </button>
                    </a>
                    <br/>
                    <br/>
                </td>
                <td style="border:0;">
                    <pwm:Display key="Long_Title_Logout"/>
                </td>
            </tr>
        </table>
    </div>
</div>
<%@ include file="../WEB-INF/jsp/fragment/footer.jsp" %>
</body>
</html>
