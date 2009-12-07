<%--
  ~ Password Management Servlets (PWM)
  ~ http://code.google.com/p/pwm/
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
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

<%@ page import="com.novell.ldapchai.exception.ChaiOperationException" %>
<%@ page import="com.novell.ldapchai.exception.ChaiUnavailableException" %>
<%@ page import="com.novell.ldapchai.provider.ChaiProvider" %>
<%@ page import="password.pwm.ContextManager" %>
<%@ page import="password.pwm.PwmSession" %>
<%@ page import="password.pwm.config.PwmSetting" %>
<%@ page import="java.util.Comparator" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.Properties" %>
<%@ page import="java.util.TreeMap" %>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en" lang="en">
<%@ include file="../jsp/header.jsp" %>
<body class="bodymain" onunload="unloadHandler();">
<%
    final PwmSession beanMgr = PwmSession.getPwmSession(session);
    final ChaiProvider provider = ContextManager.getContextManager(session).getProxyChaiProvider();
    final String searchRoot = beanMgr.getConfig().readSettingAsString(PwmSetting.LDAP_CONTEXTLESS_ROOT);
%>
<table>
<tr>
<td width="33%">&nbsp;</td>
<td width="33%">
<table width="600">
    <tr>
        <td>
            <table width="100%" border="0" cellspacing="0" cellpadding="0">
                <tr>
                    <td height="8"></td>
                </tr>
            </table>
            <table class="tablemain">
                <tr>
                    <td class="tableheader" colspan="10">
                        PWM User Report
                    </td>
                </tr>
                <tr>
                    <td class="tablekey">
                        Total Users
                    </td>
                    <td class="tablebody" colspan="3">
                        <%
                            try {
                                final String searchFilter = "(objectClass=inetOrgPerson)";
                                final Map results = provider.search(searchRoot, searchFilter);
                                out.print(results.size());
                            } catch (ChaiUnavailableException e) {
                                e.printStackTrace();
                            } catch (ChaiOperationException e) {
                                e.printStackTrace();
                            }
                        %>
                    </td>
                </tr>
                <tr>
                    <td class="tablekey">
                        Users who have changed password using PWM
                    </td>
                    <td class="tablebody" colspan="3">
                        <%
                            try {
                                final String searchFilter = "(&(objectClass=inetOrgPerson)(pwmLastPwdUpdate=*))";
                                final Map results = provider.search(searchRoot, searchFilter);
                                out.print(results.size());
                            } catch (ChaiUnavailableException e) {
                                e.printStackTrace();
                            } catch (ChaiOperationException e) {
                                e.printStackTrace();
                            }
                        %>
                    </td>
                </tr>
                <tr>
                    <td class="tablekey">
                        Users with completed PWM responses
                    </td>
                    <td class="tablebody" colspan="3">
                        <%
                            try {
                                final String searchFilter = "(&(objectClass=inetOrgPerson)(pwmResponseSet=*))";
                                final Map results = provider.search(searchRoot, searchFilter);
                                out.print(results.size());
                            } catch (ChaiUnavailableException e) {
                                e.printStackTrace();
                            } catch (ChaiOperationException e) {
                                e.printStackTrace();
                            }
                        %>
                    </td>
                </tr>
                <tr>
                    <td class="tablekey">
                        Users who have not setup their PWM responses
                    </td>
                    <td class="tablebody" colspan="3">
                        <%
                            try {
                                final String searchFilter = "(&(objectClass=inetOrgPerson)(!(pwmResponseSet=*)))";
                                //alphabetic sort
                                final Map<String, Properties> results = new TreeMap<String, Properties>(
                                        new Comparator<String>() {
                                            public int compare(String o1, String o2)
                                            {
                                                return ((o1).toLowerCase().compareTo((o2).toLowerCase()));
                                            }
                                        });
                                results.putAll(provider.search(searchRoot, searchFilter));
                                for (String dn : results.keySet()) {
                                    out.print(dn);
                                    out.println("<br/>");
                                }
                            } catch (ChaiUnavailableException e) {
                                e.printStackTrace();
                            } catch (ChaiOperationException e) {
                                e.printStackTrace();
                            }
                        %>
                    </td>
                </tr>
            </table>
        </td>
    </tr>
</table>
</td>
<td width="33%">&nbsp;</td>
</tr>
</table>
</body>
</html>
