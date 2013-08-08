<%--
  ~ Password Management Servlets (PWM)
  ~ http://code.google.com/p/pwm/
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2012 The PWM Project
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
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="fragment/header.jsp" %>
<body onload="pwmPageLoadHandler();" class="nihilo">
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_ForgottenPassword"/>
    </jsp:include>
    <div id="centerbody">
        <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
        <p><pwm:Display key="Display_RecoverPasswordChoices"/></p>
        <table style="border: 0">
            <tr style="border: 0">
                <td class="key" style="border: 0">
                    <form action="<pwm:url url='ForgottenPassword'/>" method="post"
                          enctype="application/x-www-form-urlencoded" name="search">
                        <input class="btn" type="submit" name="submitBtn"
                               value="<pwm:Display key="Button_UnlockPassword"/>"/>
                        <input type="hidden" name="processAction" value="selectUnlock"/>
                        <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
                    </form>
                </td>
                <td style="border: 0">
                    <pwm:Display key="Display_RecoverChoiceUnlock"/>
                </td>
            </tr>
            <tr style="border: 0">
                <td class="key" style="border: 0">
                    &nbsp;
                </td>
            </tr>
            <tr style="border: 0">
                <td class="key" style="border: 0">
                    <form action="<pwm:url url='ForgottenPassword'/>" method="post"
                          enctype="application/x-www-form-urlencoded" name="search">
                        <input class="btn" type="submit" name="submitBtn" value="<pwm:Display key="Button_ChangePassword"/>"/>
                        <input type="hidden" name="processAction" value="selectResetPassword"/>
                        <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
                    </form>
                </td>
                <td style="border: 0">
                    <pwm:Display key="Display_RecoverChoiceReset"/>
                </td>
            </tr>
            <% if (ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(password.pwm.config.PwmSetting.DISPLAY_CANCEL_BUTTON)) { %>
            <tr style="border: 0">
                <td class="key" style="border: 0">
                        <button style="visibility:hidden;" name="button" class="btn" id="button_cancel"
                                onclick="window.location='<%=request.getContextPath()%>/public/<pwm:url url='CommandServlet'/>?processAction=continue';return false">
                            <pwm:Display key="Button_Cancel"/>
                        </button>
                </td>
                <td style="border: 0">
                    &nbsp;
                </td>
            </tr>
            <% } %>
            <tr style="border: 0">
                <td class="key" style="border: 0">
                    &nbsp;
                </td>
            </tr>
        </table>
    </div>
    <div class="push"></div>
</div>
<script type="text/javascript">
    PWM_GLOBAL['startupFunctions'].push(function(){
        getObject('username').focus();
    });
</script>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>

