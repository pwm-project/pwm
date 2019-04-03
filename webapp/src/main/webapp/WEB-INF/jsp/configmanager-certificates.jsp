<%--
 ~ Password Management Servlets (PWM)
 ~ http://www.pwm-project.org
 ~
 ~ Copyright (c) 2006-2009 Novell, Inc.
 ~ Copyright (c) 2009-2018 The PWM Project
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

<%@ page import="password.pwm.http.JspUtility" %>
<%@ page import="password.pwm.http.servlet.configmanager.ConfigManagerCertificatesServlet" %>
<%@ page import="password.pwm.i18n.Config" %>
<%@ page import="password.pwm.util.i18n.LocaleHelper" %>
<%@ page import="java.util.List" %>
<%@ page import="password.pwm.http.PwmRequestAttribute" %>

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
    <% if ((Boolean)JspUtility.getAttribute(pageContext, PwmRequestAttribute.ConfigHasCertificates)) { %>
    <div id="centerbody">
        <h1 id="page-content-title"><%=LocaleHelper.getLocalizedMessage(Config.Title_ConfigManager, JspUtility.getPwmRequest(pageContext))%></h1>
        <%@ include file="fragment/configmanager-nav.jsp" %>
        <div id="certDebugGrid" class="grid">
        </div>
    </div>
    <% } else { %>
    <div id="centerbody">
        <h1 id="page-content-title"><%=LocaleHelper.getLocalizedMessage(Config.Title_ConfigManager, JspUtility.getPwmRequest(pageContext))%></h1>
        <%@ include file="fragment/configmanager-nav.jsp" %>
        <p>No certificates are present in the active configuration.</p>
    </div>
    <% } %>

    <div class="push"></div>
</div>
<pwm:script>
    <script type="text/javascript">

        PWM_GLOBAL['startupFunctions'].push(function () {
            var PWM_CERT_FUNCTION = {};
            PWM_CERT_FUNCTION.certDebugHeaders = function() {
                return {
                    "subject":"Subject",
                    "menuLocation":"Configuration Setting",
                    "algorithm":"Algorithm",
                    "serial":"Serial",
                    "issueDate":"Issue Date",
                    "expirationDate":"Expiration Date"
                };
            };

            PWM_CERT_FUNCTION.initCertDebugGrid=function() {
                var headers = PWM_CERT_FUNCTION.certDebugHeaders();

                require(["dojo","dojo/_base/declare", "dgrid/Grid", "dgrid/Keyboard", "dgrid/Selection", "dgrid/extensions/ColumnResizer", "dgrid/extensions/ColumnReorder", "dgrid/extensions/ColumnHider", "dgrid/extensions/DijitRegistry"],
                    function(dojo, declare, Grid, Keyboard, Selection, ColumnResizer, ColumnReorder, ColumnHider, DijitRegistry){
                        var columnHeaders = headers;

                        // Create a new constructor by mixing in the components
                        var CustomGrid = declare([ Grid, Keyboard, Selection, ColumnResizer, ColumnReorder, ColumnHider, DijitRegistry ]);

                        // Now, create an instance of our custom grid
                        PWM_VAR['certDebugGrid'] = new CustomGrid({
                            columns: columnHeaders
                        }, "certDebugGrid");


                        PWM_CERT_FUNCTION.refreshCertDebugGrid();

                        PWM_VAR['certDebugGrid'].on(".dgrid-row:click", function(evt){
                            var row = PWM_VAR['certDebugGrid'].row(evt);
                            var text = '<pre>' + row.data.detail + '</pre>';
                            PWM_MAIN.showDialog({title:'Certificate Detail',text:text,showClose:true,showOk:false,dialogClass:'wide'});
                        });
                    });
            };

            PWM_CERT_FUNCTION.refreshCertDebugGrid=function() {
                require(["dojo"],function(dojo){
                    var grid = PWM_VAR['certDebugGrid'];
                    grid.refresh();

                    var url = 'certificates?processAction=certificateData';
                    var loadFunction = function(data) {
                        grid.renderArray(data['data']);
                        grid.set("sort", { attribute : 'expirationTime', ascending: true, descending: false });
                    };
                    PWM_MAIN.ajaxRequest(url,loadFunction,{method:'GET'});
                });
            };

            PWM_CERT_FUNCTION.initCertDebugGrid();
        });

    </script>
</pwm:script>
<pwm:script-ref url="/public/resources/js/configmanager.js"/>
<pwm:script-ref url="/public/resources/js/uilibrary.js"/>
<pwm:script-ref url="/public/resources/js/admin.js"/>
<div><%@ include file="fragment/footer.jsp" %></div>
</body>
</html>
