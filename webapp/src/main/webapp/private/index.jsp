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
<%@ page import="password.pwm.http.servlet.PwmServletDefinition" %>
<%@ page import="password.pwm.http.tag.conditional.PwmIfTag" %>
<%@ page import="password.pwm.http.tag.conditional.PwmIfTest" %>
<%@ page import="password.pwm.*" %>

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
        <pwm:if test="<%=PwmIfTest.endUserFunctionalityAvailable%>" negate="true">
            <p><pwm:display key="Warning_NoEndUserModules" bundle="Config"/></p>
            <br/>
        </pwm:if>
        <pwm:if test="<%=PwmIfTest.endUserFunctionalityAvailable%>">
            <pwm:if test="<%=PwmIfTest.changePasswordAvailable%>">
                <a id="button_ChangePassword" href="<pwm:url addContext="true" url='<%=PwmServletDefinition.PrivateChangePassword.servletUrl()%>'/>">
                    <div class="tile">
                        <div class="tile-content">
                            <div class="tile-image password-image"></div>
                            <div class="tile-title" title="<pwm:display key='Title_ChangePassword'/>"><pwm:display key="Title_ChangePassword"/></div>
                            <div class="tile-subtitle" title="<pwm:display key='Long_Title_ChangePassword'/>"><pwm:display key="Long_Title_ChangePassword"/></div>
                        </div>
                    </div>
                </a>
            </pwm:if>

            <pwm:if test="<%=PwmIfTest.peopleSearchAvailable%>">
                <a id="button_PeopleSearch" href="<pwm:url addContext="true" url='<%=PwmServletDefinition.PrivatePeopleSearch.servletUrl()%>'/>#/search">
                    <div class="tile">
                        <div class="tile-content">
                            <div class="tile-image search-image"></div>
                            <div class="tile-title" title="<pwm:display key='Title_PeopleSearch'/>"><pwm:display key="Title_PeopleSearch"/></div>
                            <div class="tile-subtitle" title="<pwm:display key='Long_Title_PeopleSearch'/>"><pwm:display key="Long_Title_PeopleSearch"/></div>
                        </div>
                    </div>
                </a>
            </pwm:if>

            <pwm:if test="<%=PwmIfTest.orgChartEnabled%>">
                <a id="button_PeopleSearch" href="<pwm:url addContext="true" url='<%=PwmServletDefinition.PrivatePeopleSearch.servletUrl()%>'/>#/orgchart">
                    <div class="tile">
                        <div class="tile-content">
                            <div class="tile-image orgchart-image"></div>
                            <div class="tile-title" title="<pwm:display key='Title_OrgChart'/>"><pwm:display key="Title_OrgChart"/></div>
                            <div class="tile-subtitle" title="<pwm:display key='Title_OrgChart'/>"><pwm:display key="Title_OrgChart"/></div>
                        </div>
                    </div>
                </a>
            </pwm:if>

            <pwm:if test="<%=PwmIfTest.setupChallengeEnabled%>">
                <pwm:if test="<%=PwmIfTest.permission%>" permission="<%=Permission.SETUP_RESPONSE%>">
                    <a id="button_SetupResponses" href="<pwm:url addContext="true" url='<%=PwmServletDefinition.SetupResponses.servletUrl()%>'/>">
                        <div class="tile">
                            <div class="tile-content">
                                <div class="tile-image security-image"></div>
                                <div class="tile-title" title="<pwm:display key='Title_SetupResponses'/>"><pwm:display key="Title_SetupResponses"/></div>
                                <div class="tile-subtitle" title="<pwm:display key='Long_Title_SetupResponses'/>"><pwm:display key="Long_Title_SetupResponses"/></div>
                            </div>
                        </div>
                    </a>
                </pwm:if>
            </pwm:if>

            <pwm:if test="<%=PwmIfTest.otpSetupEnabled%>">
                <a id="button_SetupOtpSecret" href="<pwm:url addContext="true" url='<%=PwmServletDefinition.SetupOtp.servletUrl()%>'/>">
                    <div class="tile">
                        <div class="tile-content">
                            <div class="tile-image mobile-image"></div>
                            <div class="tile-title" title="<pwm:display key='Title_SetupOtpSecret'/>"><pwm:display key="Title_SetupOtpSecret"/></div>
                            <div class="tile-subtitle" title="<pwm:display key='Long_Title_SetupOtpSecret'/>"><pwm:display key="Long_Title_SetupOtpSecret"/></div>
                        </div>
                    </div>
                </a>
            </pwm:if>

            <pwm:if test="<%=PwmIfTest.updateProfileAvailable%>">
                <a id="button_UpdateProfile" href="<pwm:url addContext="true" url='<%=PwmServletDefinition.UpdateProfile.servletUrl()%>'/>">
                    <div class="tile">
                        <div class="tile-content">
                            <div class="tile-image profile-image"></div>
                            <div class="tile-title" title="<pwm:display key='Title_UpdateProfile'/>"><pwm:display key="Title_UpdateProfile"/></div>
                            <div class="tile-subtitle" title="<pwm:display key='Long_Title_UpdateProfile'/>"><pwm:display key="Long_Title_UpdateProfile"/></div>
                        </div>
                    </div>
                </a>
            </pwm:if>

            <pwm:if test="<%=PwmIfTest.shortcutsEnabled%>">
                <a id="button_Shortcuts" href="<pwm:url addContext="true" url='<%=PwmServletDefinition.Shortcuts.servletUrl()%>'/>">
                    <div class="tile">
                        <div class="tile-content">
                            <div class="tile-image shortcut-image"></div>
                            <div class="tile-title" title="<pwm:display key='Title_Shortcuts'/>"><pwm:display key="Title_Shortcuts"/></div>
                            <div class="tile-subtitle" title="<pwm:display key='Long_Title_Shortcuts'/>"><pwm:display key="Long_Title_Shortcuts"/></div>
                        </div>
                    </div>
                </a>
            </pwm:if>

            <pwm:if test="<%=PwmIfTest.accountInfoEnabled%>">
                <a id="button_UserInformation" href="<pwm:url addContext="true" url='<%=PwmServletDefinition.AccountInformation.servletUrl()%>'/>">
                    <div class="tile">
                        <div class="tile-content">
                            <div class="tile-image user-image"></div>
                            <div class="tile-title" title="<pwm:display key='Title_UserInformation'/>"><pwm:display key="Title_UserInformation"/></div>
                            <div class="tile-subtitle" title="<pwm:display key='Long_Title_UserInformation'/>"><pwm:display key="Long_Title_UserInformation"/></div>
                        </div>
                    </div>
                </a>
            </pwm:if>

            <pwm:if test="<%=PwmIfTest.helpdeskAvailable%>">
                <a id="button_Helpdesk" href="<pwm:url addContext="true" url='<%=PwmServletDefinition.Helpdesk.servletUrl()%>'/>">
                    <div class="tile">
                        <div class="tile-content">
                            <div class="tile-image support-image"></div>
                            <div class="tile-title" title="<pwm:display key='Title_Helpdesk'/>"><pwm:display key="Title_Helpdesk"/></div>
                            <div class="tile-subtitle" title="<pwm:display key='Long_Title_Helpdesk'/>"><pwm:display key="Long_Title_Helpdesk"/></div>
                        </div>
                    </div>
                </a>
            </pwm:if>


            <pwm:if test="<%=PwmIfTest.deleteAccountAvailable%>">
                <a id="button_Helpdesk" href="<pwm:url addContext="true" url='<%=PwmServletDefinition.SelfDelete.servletUrl()%>'/>">
                    <div class="tile">
                        <div class="tile-content">
                            <div class="tile-image selfdelete-image"></div>
                            <div class="tile-title" title="<pwm:display key='Title_DeleteAccount'/>"><pwm:display key="Title_DeleteAccount"/></div>
                            <div class="tile-subtitle" title="<pwm:display key='Long_Title_DeleteAccount'/>"><pwm:display key="Long_Title_DeleteAccount"/></div>
                        </div>
                    </div>
                </a>
            </pwm:if>

            <pwm:if test="<%=PwmIfTest.guestRegistrationAvailable%>">
                <a id="button_GuestRegistration" href="<pwm:url url='<%=PwmServletDefinition.GuestRegistration.servletUrl()%>' addContext="true"/>">
                    <div class="tile">
                        <div class="tile-content">
                            <div class="tile-image guest-image"></div>
                            <div class="tile-title" title="<pwm:display key='Title_GuestRegistration'/>"><pwm:display key="Title_GuestRegistration"/></div>
                            <div class="tile-subtitle" title="<pwm:display key='Long_Title_GuestRegistration'/>"><pwm:display key="Long_Title_GuestRegistration"/></div>
                        </div>
                    </div>
                </a>
            </pwm:if>
        </pwm:if>
        <pwm:if test="<%=PwmIfTest.permission%>" permission="<%=Permission.PWMADMIN%>">
            <a id="button_Admin" href="<pwm:url url='<%=PwmServletDefinition.Admin.servletUrl()%>' addContext="true"/> ">
                <div class="tile">
                    <div class="tile-content">
                        <div class="tile-image admin-image"></div>
                        <div class="tile-title" title="<pwm:display key='Title_Admin'/>"><pwm:display key="Title_Admin"/></div>
                        <div class="tile-subtitle" title="<pwm:display key='Long_Title_Admin'/>"><pwm:display key="Long_Title_Admin"/></div>
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
