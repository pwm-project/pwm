/**
 * PasswordManagementService.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis 1.4 Apr 22, 2006 (06:55:48 PDT) WSDL2Java emitter.
 */

package password.pwm.ws.client.novell.pwdmgt;

public interface PasswordManagementService extends javax.xml.rpc.Service {
    public java.lang.String getPasswordManagementPortAddress();

    public password.pwm.ws.client.novell.pwdmgt.PasswordManagement getPasswordManagementPort() throws javax.xml.rpc.ServiceException;

    public password.pwm.ws.client.novell.pwdmgt.PasswordManagement getPasswordManagementPort(java.net.URL portAddress) throws javax.xml.rpc.ServiceException;
}
