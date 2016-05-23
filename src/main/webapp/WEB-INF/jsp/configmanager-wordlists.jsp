<%@ page import="password.pwm.http.JspUtility" %>
<%@ page import="password.pwm.http.tag.conditional.PwmIfTest" %>
<%@ page import="password.pwm.i18n.Config" %>
<%@ page import="password.pwm.util.LocaleHelper" %>
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
<% JspUtility.setFlag(pageContext, PwmRequestFlag.INCLUDE_CONFIG_CSS);%>
<%@ taglib uri="pwm" prefix="pwm" %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo">
<link href="<pwm:context/><pwm:url url='/public/resources/configmanagerStyle.css'/>" rel="stylesheet" type="text/css"/>
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="<%=LocaleHelper.getLocalizedMessage(Config.Title_ConfigManager, JspUtility.getPwmRequest(pageContext))%>"/>
    </jsp:include>
    <div id="centerbody">
        <div id="page-content-title"><%=LocaleHelper.getLocalizedMessage(Config.Title_ConfigManager, JspUtility.getPwmRequest(pageContext))%></div>
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
