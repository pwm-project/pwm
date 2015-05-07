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
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<body class="nihilo">
<div id="wrapper" class="peoplesearch-wrapper">
    <jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_PeopleSearch"/>
    </jsp:include>
    <div id="centerbody" class="wide" style="height:100%">
        <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>

        <div id="searchControlPanel" style="position: relative; margin-left: auto; margin-right: auto; width: 100%; text-align: center;">
            <table class="noborder" style="margin-left: auto; margin-right: auto; width:100px;" >
                <tr>
                    <td style="width:5%">
                        <span class="fa fa-search"></span>
                    </td>
                    <td style="width:90%">
                        <input type="search" id="username" name="username" class="peoplesearch-input-username" <pwm:autofocus/> autocomplete="off"/>
                    </td>
                    <td style="width:5%">
                        <div style="width:20px; max-width: 20px">
                            <div id="searchIndicator" style="display: none">
                                <span style="" class="fa fa-lg fa-spin fa-spinner"></span>
                            </div>
                            <div id="maxResultsIndicator" style="display: none;">
                                <span style="color: #ffcd59;" class="fa fa-lg fa-exclamation-circle"></span>
                            </div>
                        </div>
                    </td>
                </tr>
            </table>
            <noscript>
                <span><pwm:display key="Display_JavascriptRequired"/></span>
                <a href="<pwm:context/>"><pwm:display key="Title_MainPage"/></a>
            </noscript>
        </div>
        <br/>
        <div id="peoplesearch-searchResultsGrid" class="grid">
        </div>
    </div>
    <div class="push"></div>
</div>
<%@ include file="fragment/footer.jsp" %>
<pwm:script-ref url="/public/resources/js/peoplesearch.js"/>
</body>
</html>
