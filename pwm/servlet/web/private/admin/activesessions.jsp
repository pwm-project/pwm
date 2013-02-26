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
<%@ page import="password.pwm.bean.SessionStateBean" %>
<%@ page import="password.pwm.bean.UserInfoBean" %>
<%@ page import="password.pwm.util.TimeDuration" %>
<%@ page import="java.util.*" %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<body class="nihilo" onload="pwmPageLoadHandler();">
<div id="wrapper">
    <jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Active Sessions"/>
    </jsp:include>
    <div id="centerbody">
        <%@ include file="admin-nav.jsp" %>
        <%
            final Gson gson = new Gson();
            final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
            timeFormat.setTimeZone(TimeZone.getTimeZone("Zulu"));
            final int maxResults = 100000;
            boolean maxResultsExceeded = false;
            final ContextManager theManager = ContextManager.getContextManager(request.getSession().getServletContext());
            final Set<PwmSession> activeSessions = new LinkedHashSet<PwmSession>(theManager.getPwmSessions());
            final List<Map<String,String>> gridData = new ArrayList<Map<String, String>>();
            for (Iterator<PwmSession> iterator = activeSessions.iterator(); iterator.hasNext() && !maxResultsExceeded;) {
                final PwmSession loopSession = iterator.next();
                try {
                    final SessionStateBean loopSsBean = loopSession.getSessionStateBean();
                    final UserInfoBean loopUiBean = loopSession.getUserInfoBean();
                    final Map<String, String> rowData = new HashMap<String, String>();
                    rowData.put("label", loopSession.getSessionStateBean().getSessionID());
                    rowData.put("createTime", timeFormat.format(new Date(loopSession.getCreationTime())));
                    rowData.put("idle", TimeDuration.fromCurrent(loopSession.getLastAccessedTime()).asCompactString());
                    rowData.put("locale", loopSsBean.getLocale() == null ? "" : loopSsBean.getLocale().toString());
                    rowData.put("userDN", loopSsBean.isAuthenticated() ? loopUiBean.getUserDN() : "");
                    rowData.put("userID", loopSsBean.isAuthenticated() ? loopUiBean.getUserID() : "");
                    rowData.put("srcAddress", loopSsBean.getSrcAddress());
                    gridData.add(rowData);
                } catch (IllegalStateException e) { /* ignore */ }
                if (gridData.size() >= maxResults) {
                    maxResultsExceeded = true;
                }
            }
        %>
        <div id="grid">
        </div>
        <script>
            var headers = {"createTime":"Create Time","label":"Label","idle":"Idle","srcAddress":"Address","locale":"Locale",
                "userID":"User ID","userDN":"User DN"};

            require(["dojo/_base/declare", "dgrid/Grid", "dgrid/Keyboard", "dgrid/Selection", "dgrid/extensions/ColumnResizer", "dgrid/extensions/ColumnReorder", "dgrid/extensions/ColumnHider", "dojo/domReady!"],
                    function(declare, Grid, Keyboard, Selection, ColumnResizer, ColumnReorder, ColumnHider){
                        var data = <%=gson.toJson(gridData)%>;
                        var columnHeaders = headers;

                        // Create a new constructor by mixing in the components
                        var CustomGrid = declare([ Grid, Keyboard, Selection, ColumnResizer, ColumnReorder, ColumnHider ]);

                        // Now, create an instance of our custom grid which
                        // have the features we added!
                        var grid = new CustomGrid({
                            columns: columnHeaders
                        }, "grid");
                        grid.renderArray(data);
                        grid.set("sort","createTime");
                    });
        </script>
        <style scoped="scoped">
            .dgrid { height: auto; }
            .dgrid .dgrid-scroller { position: relative; max-height: 360px; overflow: auto; }
        </style>
        <% if (maxResultsExceeded) { %>
        <div style="width:100%; text-align: center">
            <pwm:Display key="Display_SearchResultsExceeded"/>
        </div>
        <% } %>
    </div>
</div>
<%@ include file="/WEB-INF/jsp/fragment/footer.jsp" %>
</body>
</html>
