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

<%@ page import="com.google.gson.Gson" %>
<%@ page import="password.pwm.util.UserReport" %>
<%@ page import="java.text.DateFormat" %>
<%@ page import="java.util.*" %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<% DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mmm:ss"); %>
<body class="nihilo" onload="pwmPageLoadHandler();">
<div id="wrapper">
    <jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Event Log"/>
    </jsp:include>
    <% if ("true".equalsIgnoreCase(request.getParameter("doReport"))) { %>
    <div id="centerbody" style="width:98%">
        <%@ include file="admin-nav.jsp" %>
        <br class="clear"/>
        <%
            final UserReport userReport = new UserReport(ContextManager.getPwmApplication(session));
            final List<Map<String,Object>> gridData = new ArrayList<Map<String, Object>>();
            for (final Iterator<UserReport.UserInformation> resultIterator = userReport.resultIterator(50*1000); resultIterator.hasNext(); ) {
                final UserReport.UserInformation userInformation = resultIterator.next();
                final Map<String,Object> rowData = new HashMap<String,Object>();
                rowData.put("userID",userInformation.getUserInfoBean().getUserID());
                rowData.put("userDN",userInformation.getUserInfoBean().getUserDN());
                rowData.put("userGUID",userInformation.getUserInfoBean().getUserGuid());
                rowData.put("pet",userInformation.getUserInfoBean().getPasswordExpirationTime());
                rowData.put("pct",userInformation.getPasswordChangeTime());
                rowData.put("rst",userInformation.getResponseSetTime());
                rowData.put("hasResponses",userInformation.isHasValidResponses());
                rowData.put("expired",userInformation.getPasswordStatus().isExpired());
                rowData.put("preExpired",userInformation.getPasswordStatus().isPreExpired());
                rowData.put("violatesPolicy",userInformation.getPasswordStatus().isViolatesPolicy());
                rowData.put("passwordWarn",userInformation.getPasswordStatus().isWarnPeriod());
                gridData.add(rowData);
            }
        %>
        <div id="grid">
        </div>
        <script type="text/javascript">
            function startupPage() {
                var headers = {"userID":"User ID","userDN":"User DN","userGUID":"User GUID","pet":"Password Expiration Time","pct":"Password Change Time","rst":"Response Save Time",
                    "hasResponses":"Has Valid Responses","expired":"Password Expired","preExpired":"Password Pre-Expired","violatesPolicy":"Password Violates Policy", "passwordWarn":"Password In Warn Period"};

                require(["dojo/_base/declare", "dgrid/Grid", "dgrid/Keyboard", "dgrid/Selection", "dgrid/extensions/ColumnResizer", "dgrid/extensions/ColumnReorder", "dgrid/extensions/ColumnHider", "dojo/domReady!"],
                        function(declare, Grid, Keyboard, Selection, ColumnResizer, ColumnReorder, ColumnHider){
                            var data = <%=new Gson().toJson(gridData)%>;

                            // Create a new constructor by mixing in the components
                            var CustomGrid = declare([ Grid, Keyboard, Selection, ColumnResizer, ColumnReorder, ColumnHider ]);

                            // Now, create an instance of our custom grid which
                            // have the features we added!
                            var grid = new CustomGrid({
                                columns: headers
                            }, "grid");
                            grid.renderArray(data);
                            grid.set("sort","userID");
                        });
            }

            PWM_GLOBAL['startupFunctions'].push(function(){
                startupPage();
            });
        </script>

        <style scoped="scoped">
            .dgrid { height: auto; }
            .dgrid .dgrid-scroller { position: relative; overflow: visible; }
        </style>
        <br/>
        <br/>
        <div id="buttonbar" style="align: center">
            <form action="userreport.jsp" method="GET">
                <button type="submit" class="btn">Continue</button>
                <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
            </form>
        </div>
    </div>
    <% } else { %>
    <div id="centerbody">
        <%@ include file="admin-nav.jsp" %>
        <br/>
        <p>This report may take a long time to generate depending on the number of users in the search.</p>
        <p>If the user count is large, PWM may need large sizes of Java memory (heap size) to run the report.  It is also possible to run
            this report from the <i>PwmCommand</i> command line utility.
        </p>
        <div id="buttonbar" style="align: center">
            <form action="userreport.jsp" onclick="showWaitDialog()" method="GET">
                <input type="hidden" name="doReport" value="true"/>
                <button type="submit" class="btn">Run Report</button>
                <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
            </form>
            <form action="<%=request.getContextPath()%><pwm:url url="/private/CommandServlet"/>" method="GET">
                <button type="submit" class="btn">Download Report as CSV</button>
                <input type="hidden" name="processAction" value="outputUserReportCsv"/>
                <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
            </form>
        </div>
    </div>
    <% } %>
</div>
<%@ include file="/WEB-INF/jsp/fragment/footer.jsp" %>
</body>
</html>
