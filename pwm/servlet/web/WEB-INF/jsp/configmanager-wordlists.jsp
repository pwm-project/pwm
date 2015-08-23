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
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo">
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="<%=JspUtility.getAttribute(pageContext,PwmConstants.REQUEST_ATTR.PageTitle)%>"/>
    </jsp:include>
    <div id="centerbody">
        <%@ include file="fragment/configmanager-nav.jsp" %>
        <style nonce="<pwm:value name="cspNonce"/>">
            .buttoncell {
                border: 0;
                width:50%;
            }
            .buttonrow {
                height: 45px;
                margin-top: 20px;
                margin-bottom: 20px;
            }
            .menubutton {
                cursor: pointer;
                display: block;
                margin-left: auto;
                margin-right: auto;

            }.menubutton:disabled {
                 background-color: purple;
             }
        </style>

        <% { %>
        <table style="width:550px" id="table-wordlistInfo">
        </table>
        <br/>
        <table class="noborder">
            <tr class="buttonrow">
                <td class="buttoncell">
                    <button class="menubutton" id="MenuItem_UploadWordlist" style="visibility: hidden;">
                        <pwm:if test="showIcons"><span class="btn-icon fa fa-upload"></span></pwm:if>
                        Upload Wordlist
                    </button>
                </td>
                <td class="buttoncell">
                    <button class="menubutton" id="MenuItem_ClearWordlist" style="visibility: hidden;">
                        <pwm:if test="showIcons"><span class="btn-icon fa fa-trash"></span></pwm:if>
                        Clear Uploaded Wordlist
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
                        <pwm:if test="showIcons"><span class="btn-icon fa fa-upload"></span></pwm:if>
                        Upload Seedlist
                    </button>
                </td>
                <td class="buttoncell">
                    <button class="menubutton" id="MenuItem_ClearSeedlist" style="visibility: hidden;">
                        <pwm:if test="showIcons"><span class="btn-icon fa fa-trash"></span></pwm:if>
                        Clear Uploaded Seedlist
                    </button>
                </td>
            </tr>
        </table>
        <% } %>
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
