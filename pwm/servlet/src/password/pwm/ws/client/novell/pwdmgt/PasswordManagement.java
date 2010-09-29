/**
 * PasswordManagement.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis 1.4 Apr 22, 2006 (06:55:48 PDT) WSDL2Java emitter.
 */

package password.pwm.ws.client.novell.pwdmgt;

public interface PasswordManagement extends java.rmi.Remote {
    public password.pwm.ws.client.novell.pwdmgt.ForgotPasswordConfWSBean processForgotConf(password.pwm.ws.client.novell.pwdmgt.ProcessForgotConfRequest bodyIn) throws java.rmi.RemoteException;
    public password.pwm.ws.client.novell.pwdmgt.ForgotPasswordWSBean processUser(password.pwm.ws.client.novell.pwdmgt.ProcessUserRequest bodyIn) throws java.rmi.RemoteException;
    public password.pwm.ws.client.novell.pwdmgt.ForgotPasswordWSBean processChaRes(password.pwm.ws.client.novell.pwdmgt.ProcessChaResRequest bodyIn) throws java.rmi.RemoteException;
    public password.pwm.ws.client.novell.pwdmgt.ForgotPasswordWSBean processChgPwd(password.pwm.ws.client.novell.pwdmgt.ProcessChgPwdRequest bodyIn) throws java.rmi.RemoteException;
}
