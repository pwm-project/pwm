<%--
  ~ Password Management Servlets (PWM)
  ~ http://code.google.com/p/pwm/
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2014 The PWM Project
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

<%@ page import="password.pwm.http.bean.HelpdeskBean" %>
<%@ page import="password.pwm.util.JsonUtil" %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final PwmSession pwmSession = PwmSession.getPwmSession(request); %>
<% final HelpdeskBean helpdeskBean = pwmSession.getHelpdeskBean(); %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<body class="nihilo">
<div id="wrapper">
    <jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_Helpdesk"/>
    </jsp:include>
    <div id="centerbody" class="wide">
        <div id="searchControlPanel" style="position: relative; margin-left: auto; margin-right: auto; width: 100%; text-align: center">
            <br/>
            <table style="border: 0; margin-left: auto; margin-right: auto; max-width: 450px">
                <tr style="border: 0">
                    <td style="border:0" colspan="10">
                        <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
                    </td>
                </tr>
                <tr style="border: 0">
                    <td style="border:0">
                        <form action="<pwm:url url='Helpdesk'/>" method="post" enctype="application/x-www-form-urlencoded" name="search"
                              id="searchForm" onsubmit="return false">
                            <input type="search" id="username" name="username" class="inputfield" style="width: 400px"
                                   value="<%=helpdeskBean.getSearchString()!=null?helpdeskBean.getSearchString():""%>" autofocus/>

                        </form>
                    </td>
                    <td style="border:0;">
                        <div id="searchIndicator" style="visibility: hidden">
                            <span class="fa fa-lg fa-spin fa-spinner"></span>
                        </div>
                    </td>
                    <td style="border:0;">
                        <div id="maxResultsIndicator" style="visibility: hidden;">
                            <span style="color: #ffcd59;" class="fa fa-lg fa-exclamation-circle"></span>
                        </div>
                    </td>
                </tr>
            </table>
            <noscript>
                <span><pwm:display key="Display_JavascriptRequired"/></span>
                <a href="<pwm:context/>"><pwm:display key="Title_MainPage"/></a>
            </noscript>
            <br/>
        </div>
        <div id="grid">
        </div>
    </div>
    <div class="push"></div>
</div>
<form action="<pwm:url url='Helpdesk'/>" id="loadDetailsForm" name="loadDetailsForm" method="post" enctype="application/x-www-form-urlencoded">
    <input type="hidden" name="processAction" value="detail"/>
    <input type="hidden" name="userKey" id="userKey" value=""/>
    <input type="hidden" id="pwmFormID" name="pwmFormID" value="<pwm:FormID/>"/>
</form>
<pwm:script>
<script>
    PWM_GLOBAL['startupFunctions'].push(function(){
        PWM_VAR['helpdesk_search_columns'] = <%=JsonUtil.serializeMap(helpdeskBean.getSearchColumnHeaders())%>;
        PWM_MAIN.getObject('username').focus();
        PWM_HELPDESK.initHelpdeskSearchPage();
    });
</script>
</pwm:script>
<script type="text/javascript" defer="defer" src="<pwm:context/><pwm:url url='/public/resources/js/helpdesk.js'/>"></script>
<jsp:include page="/WEB-INF/jsp/fragment/footer.jsp"/>
</body>
</html>
