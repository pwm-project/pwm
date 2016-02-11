<%@ page import="password.pwm.config.LDAPPermissionInfo" %>
<%@ page import="password.pwm.http.JspUtility" %>
<%@ page import="password.pwm.http.PwmRequest" %>
<%@ page import="password.pwm.util.LDAPPermissionCalculator" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.Set" %>
<%@ page import="java.util.TreeSet" %>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<% final LDAPPermissionCalculator outputData = (LDAPPermissionCalculator)JspUtility.getAttribute(pageContext, PwmRequest.Attribute.LdapPermissionItems); %>
<p>
  These permission suggestions are based on the current configuration.  These suggestions should be
  applied with caution and with an understanding of the security model of your specific LDAP directory
  environment.  The suggested permissions may not be appropriate in your particular environment.
</p>
<h1>Attribute Permissions</h1>
<% for (final LDAPPermissionInfo.Actor actor : LDAPPermissionInfo.Actor.values()) { %>
<% Map<String,Map<LDAPPermissionInfo.Access,List<LDAPPermissionCalculator.PermissionRecord>>> baseMap = outputData.getPermissionsByActor(actor); %>
<% if (!baseMap.isEmpty()) { %>
<h2>
  <%=actor.getLabel(JspUtility.locale(request),JspUtility.getPwmRequest(pageContext).getConfig())%>
</h2>
<p>
  <%=actor.getDescription(JspUtility.locale(request),JspUtility.getPwmRequest(pageContext).getConfig())%>
</p>
<table style="">
  <tr>
    <td class="title">Attribute Name</td>
    <td class="title">Access</td>
    <td class="title">Associated Setting</td>
  </tr>
  <% for (final String attribute : baseMap.keySet()) { %>
  <% for (final LDAPPermissionInfo.Access access : baseMap.get(attribute).keySet()) { %>
  <tr>
    <td style="text-align: left">
      <%= attribute %>
    </td>
    <td style="text-align: left">
      <%= access %>
    </td>
    <td style="text-align: left">
      <%
        final Set<String> menuLocations = new TreeSet<>();
        for (final LDAPPermissionCalculator.PermissionRecord record : baseMap.get(attribute).get(access)) {
          menuLocations.add(record.getPwmSetting().toMenuLocationDebug(record.getProfile(), JspUtility.locale(request)));
        }
      %>
      <% for (String menuLocation : menuLocations) { %>
      <%= menuLocation %>
      <br/>
      <% } %>
    </td>
  </tr>
  <% } %>
  <% } %>
</table>
<br/>
<% } %>
<% } %>
