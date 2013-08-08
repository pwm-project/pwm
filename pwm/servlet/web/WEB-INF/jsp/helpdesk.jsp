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
<%@ page import="password.pwm.bean.servlet.HelpdeskBean" %>
<%@ page import="password.pwm.util.operations.UserSearchEngine" %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final PwmSession pwmSession = PwmSession.getPwmSession(request); %>
<% final HelpdeskBean helpdeskBean = pwmSession.getHelpdeskBean(); %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<body onload="pwmPageLoadHandler();" class="nihilo">
<div id="wrapper">
    <jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_Helpdesk"/>
    </jsp:include>
    <div id="centerbody">
        <% if (helpdeskBean.getSearchString() != null) { %>
        <p><pwm:Display key="Display_Helpdesk"/></p>
        <% } %>
        <form action="<pwm:url url='Helpdesk'/>" method="post" enctype="application/x-www-form-urlencoded" name="search"
              onsubmit="handleFormSubmit('submitBtn',this);" id="searchForm">
            <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
            <h2><label for="username"><pwm:Display key="Field_Username"/></label></h2>

            <input type="search" id="username" name="username" class="inputfield"
                   value="<%=helpdeskBean.getSearchString()!=null?helpdeskBean.getSearchString():""%>" autofocus/>
            <input type="submit" class="btn"
                   name="search"
                   value="<pwm:Display key="Button_Search"/>"
                   id="submitBtn"/>
            <input type="hidden"
                   name="processAction"
                   value="search"/>
            <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
            <% if (ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(password.pwm.config.PwmSetting.DISPLAY_CANCEL_BUTTON)) { %>
            <button style="visibility:hidden;" name="button" class="btn" id="button_cancel"
                    onclick="window.location='<%=request.getContextPath()%>/public/<pwm:url url='CommandServlet'/>?processAction=continue';return false">
                <pwm:Display key="Button_Cancel"/>
            </button>
            <% } %>
        </form>
        <br/>
        <% final UserSearchEngine.UserSearchResults searchResults = helpdeskBean.getSearchResults(); %>
        <% if (searchResults != null && searchResults.getResults() != null && !searchResults.getResults().isEmpty()) { %>
        <% final Gson gson = new Gson(); %>
        <noscript>
            <span>Javascript is required to view this page.</span>
        </noscript>
        <div id="waitMessage" style="width:100%; text-align: center; display: none">
            <pwm:Display key="Display_PleaseWait"/>
        </div>
        <div id="grid">
        </div>
        <script async="async">
            PWM_GLOBAL['startupFunctions'].push(function(){
                require(["dojo/domReady!"],function(){
                getObject("waitMessage").style.display = 'inline';
                require(["dojo","dojo/_base/declare", "dgrid/Grid", "dgrid/Keyboard", "dgrid/Selection", "dgrid/extensions/ColumnResizer", "dgrid/extensions/ColumnReorder", "dgrid/extensions/ColumnHider", "dojo/domReady!"],
                        function(dojo,declare, Grid, Keyboard, Selection, ColumnResizer, ColumnReorder, ColumnHider){
                            var data = <%=gson.toJson(searchResults.resultsAsJsonOutput(pwmSession))%>;
                            var columnHeaders = <%=gson.toJson(searchResults.getHeaderAttributeMap())%>;

                            // Create a new constructor by mixing in the components
                            var CustomGrid = declare([ Grid, Keyboard, Selection, ColumnResizer, ColumnReorder, ColumnHider ]);

                            // Now, create an instance of our custom grid which
                            // have the features we added!
                            var grid = new CustomGrid({
                                columns: columnHeaders
                            }, "grid");
                            grid.renderArray(data);
                            grid.set("sort","<%=searchResults.getHeaderAttributeMap().keySet().iterator().next()%>");
                            grid.on(".dgrid-row .dgrid-cell:click", function(evt){
                                var row = grid.row(evt);
                                loadDetails(row.data['userKey']);
                            });
                            getObject("waitMessage").style.display = 'none';
                        });
                });
            });
        </script>
        <style scoped="scoped">
            .dgrid { height: auto; }
            .dgrid .dgrid-scroller { position: relative; max-height: 360px; overflow: auto; }
        </style>
        <br/>
        <% if (searchResults.isSizeExceeded()) { %>
        <div style="width:100%; text-align: center; font-size: smaller">
            <pwm:Display key="Display_SearchResultsExceeded"/>
        </div>
        <% } %>
        <% } else if (helpdeskBean.getSearchString() != null && helpdeskBean.getSearchString().length() > 1 && searchResults != null && searchResults.getResults().isEmpty()) { %>
        <div style="width:100%; text-align: center">
            <pwm:Display key="Display_SearchResultsNone"/>
        </div>
        <% } %>
    </div>
    <div class="push"></div>
</div>
<form id="loadDetailsForm" name="loadDetailsForm" method="post" enctype="application/x-www-form-urlencoded">
    <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
    <input type="hidden" name="processAction" value="detail"/>
    <input type="hidden" name="userKey" id="userKey" value=""/>
</form>
<script>
    function loadDetails(userKey) {
        showWaitDialog(null,null,function(){
            setTimeout(function(){
                getObject("userKey").value = userKey;
                getObject("loadDetailsForm").submit();
            },10);
        });
    }
</script>
<script type="text/javascript">
    PWM_GLOBAL['startupFunctions'].push(function(){
        getObject('username').focus();
    });
</script>
<jsp:include page="/WEB-INF/jsp/fragment/footer.jsp"/>
</body>
</html>
