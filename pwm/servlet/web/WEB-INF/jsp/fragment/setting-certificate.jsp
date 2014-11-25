<%@ page import="password.pwm.PwmConstants" %>
<%@ page import="password.pwm.config.PwmSetting" %>
<%@ page import="password.pwm.util.SecureHelper" %>
<%@ page import="password.pwm.util.StringUtil" %>
<%@ page import="password.pwm.util.X509Utils" %>
<%@ page import="java.io.ByteArrayInputStream" %>
<%@ page import="java.security.cert.X509Certificate" %>
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

<%@ taglib uri="pwm" prefix="pwm" %>
<% final PwmSetting loopSetting = (PwmSetting)request.getAttribute("setting"); %>
<% X509Certificate[] certificates = (X509Certificate[])request.getAttribute("certificate"); %>
<% final boolean hideActions = request.getAttribute("hideActions") != null && Boolean.parseBoolean((String)request.getAttribute("hideActions")); %>
<% for (X509Certificate certificate : certificates) {%>
<% final String md5sum = SecureHelper.hash(new ByteArrayInputStream(certificate.getEncoded()), SecureHelper.HashAlgorithm.MD5); %>
<% final String sha1sum = SecureHelper.hash(new ByteArrayInputStream(certificate.getEncoded()), SecureHelper.HashAlgorithm.SHA1); %>
<table style="width:100%" id="table_certificate0">
    <tr><td colspan="2" class="key" style="text-align: center">
        Certificate
    </td></tr>
    <tr><td>Subject Name</td><td><%=certificate.getSubjectX500Principal().getName()%></td></tr>
    <tr><td>Issuer Name</td><td><%=certificate.getIssuerX500Principal().getName()%></td></tr>
    <% final String serialNum = X509Utils.hexSerial(certificate); %>
    <tr><td>Serial Number</td><td><span style="font-family: 'Courier New', monospace; word-wrap: break-word"><%=serialNum%></span></td></tr>
    <tr><td>Validity</td><td>From <span class="timestamp"><%=PwmConstants.DEFAULT_DATETIME_FORMAT.format(certificate.getNotBefore())%></span><br/>To <span class="timestamp"><%=PwmConstants.DEFAULT_DATETIME_FORMAT.format(certificate.getNotAfter())%></span></td></tr>
    <tr><td colspan="2" class="key" style="text-align: center; font-size: smaller">
        <a href="#" id="button-showCert_<%=md5sum%>">details</a>
    </td></tr>
</table>
<pwm:script>
<script type="text/javascript">
    PWM_GLOBAL['startupFunctions'].push(function(){
        PWM_MAIN.addEventHandler('button-showCert_<%=md5sum%>','click',function(){
            var body = '<pre style="white-space: pre-wrap; word-wrap: break-word; max-width: 640px">';
            body += 'md5sum: <%=md5sum%>\n';
            body += 'sha1sum: <%=sha1sum%>\n';
            body += '<%=StringUtil.escapeJS(certificate.toString())%>';
            body += '</pre>';
            PWM_MAIN.showDialog({
                title: "Certificate Detail",
                text: body,
                showClose: true,
                showOk: false,
                dialogClass:'wide'
            });
        });
    });
</script>
</pwm:script>
<% } %>
<% if (!hideActions) { %>
<% if (certificates != null && certificates.length > 0) { %>
<button id="<%=loopSetting.getKey()%>_ClearButton" data-dojo-type="dijit.form.Button">Clear</button>
<% } %>
<button id="<%=loopSetting.getKey()%>_AutoImportButton" data-dojo-type="dijit.form.Button">Import From Server</button>
<pwm:script>
<script type="text/javascript">
    PWM_GLOBAL['startupFunctions'].push(function(){
        require(["dojo/parser","dijit/form/Button"],function(parser,Button){
            new Button({
                onClick:function(){handleResetClick('<%=loopSetting.getKey()%>') }
            },'<%=loopSetting.getKey()%>_ClearButton');
            new Button({
                <% if (loopSetting == PwmSetting.LDAP_SERVER_CERTS) { %>
                onClick:function(){PWM_CFGEDIT.executeSettingFunction('<%=loopSetting.getKey()%>','password.pwm.config.function.LdapCertImportFunction')}
                <% } else if (loopSetting == PwmSetting.AUDIT_SYSLOG_CERTIFICATES) { %>
                onClick:function(){PWM_CFGEDIT.executeSettingFunction('<%=loopSetting.getKey()%>','password.pwm.config.function.SyslogCertImportFunction')}
                <% } %>
            },'<%=loopSetting.getKey()%>_AutoImportButton');
        });

        PWM_CFGEDIT.readSetting('<%=loopSetting.getKey()%>',function(){});
    });
</script>
</pwm:script>
<% } %>
