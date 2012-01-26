/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2012 The PWM Project
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

/**
 * ProcessChgPwdRequest.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis 1.4 Apr 22, 2006 (06:55:48 PDT) WSDL2Java emitter.
 */

package password.pwm.ws.client.novell.pwdmgt;

public class ProcessChgPwdRequest  implements java.io.Serializable {
    private java.lang.String userDN;

    private java.lang.String newPassword;

    private java.lang.String confirmPassword;

    public ProcessChgPwdRequest() {
    }

    public ProcessChgPwdRequest(
           java.lang.String userDN,
           java.lang.String newPassword,
           java.lang.String confirmPassword) {
           this.userDN = userDN;
           this.newPassword = newPassword;
           this.confirmPassword = confirmPassword;
    }


    /**
     * Gets the userDN value for this ProcessChgPwdRequest.
     * 
     * @return userDN
     */
    public java.lang.String getUserDN() {
        return userDN;
    }


    /**
     * Sets the userDN value for this ProcessChgPwdRequest.
     * 
     * @param userDN
     */
    public void setUserDN(java.lang.String userDN) {
        this.userDN = userDN;
    }


    /**
     * Gets the newPassword value for this ProcessChgPwdRequest.
     * 
     * @return newPassword
     */
    public java.lang.String getNewPassword() {
        return newPassword;
    }


    /**
     * Sets the newPassword value for this ProcessChgPwdRequest.
     * 
     * @param newPassword
     */
    public void setNewPassword(java.lang.String newPassword) {
        this.newPassword = newPassword;
    }


    /**
     * Gets the confirmPassword value for this ProcessChgPwdRequest.
     * 
     * @return confirmPassword
     */
    public java.lang.String getConfirmPassword() {
        return confirmPassword;
    }


    /**
     * Sets the confirmPassword value for this ProcessChgPwdRequest.
     * 
     * @param confirmPassword
     */
    public void setConfirmPassword(java.lang.String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }

    private java.lang.Object __equalsCalc = null;
    public synchronized boolean equals(java.lang.Object obj) {
        if (!(obj instanceof ProcessChgPwdRequest)) return false;
        ProcessChgPwdRequest other = (ProcessChgPwdRequest) obj;
        if (obj == null) return false;
        if (this == obj) return true;
        if (__equalsCalc != null) {
            return (__equalsCalc == obj);
        }
        __equalsCalc = obj;
        boolean _equals;
        _equals = true && 
            ((this.userDN==null && other.getUserDN()==null) || 
             (this.userDN!=null &&
              this.userDN.equals(other.getUserDN()))) &&
            ((this.newPassword==null && other.getNewPassword()==null) || 
             (this.newPassword!=null &&
              this.newPassword.equals(other.getNewPassword()))) &&
            ((this.confirmPassword==null && other.getConfirmPassword()==null) || 
             (this.confirmPassword!=null &&
              this.confirmPassword.equals(other.getConfirmPassword())));
        __equalsCalc = null;
        return _equals;
    }

    private boolean __hashCodeCalc = false;
    public synchronized int hashCode() {
        if (__hashCodeCalc) {
            return 0;
        }
        __hashCodeCalc = true;
        int _hashCode = 1;
        if (getUserDN() != null) {
            _hashCode += getUserDN().hashCode();
        }
        if (getNewPassword() != null) {
            _hashCode += getNewPassword().hashCode();
        }
        if (getConfirmPassword() != null) {
            _hashCode += getConfirmPassword().hashCode();
        }
        __hashCodeCalc = false;
        return _hashCode;
    }

    // Type metadata
    private static org.apache.axis.description.TypeDesc typeDesc =
        new org.apache.axis.description.TypeDesc(ProcessChgPwdRequest.class, true);

    static {
        typeDesc.setXmlType(new javax.xml.namespace.QName("http://www.novell.com/pwdmgt/service", "processChgPwdRequest"));
        org.apache.axis.description.ElementDesc elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("userDN");
        elemField.setXmlName(new javax.xml.namespace.QName("http://www.novell.com/pwdmgt/service", "userDN"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("newPassword");
        elemField.setXmlName(new javax.xml.namespace.QName("http://www.novell.com/pwdmgt/service", "newPassword"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("confirmPassword");
        elemField.setXmlName(new javax.xml.namespace.QName("http://www.novell.com/pwdmgt/service", "confirmPassword"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
    }

    /**
     * Return type metadata object
     */
    public static org.apache.axis.description.TypeDesc getTypeDesc() {
        return typeDesc;
    }

    /**
     * Get Custom Serializer
     */
    public static org.apache.axis.encoding.Serializer getSerializer(
           java.lang.String mechType, 
           java.lang.Class _javaType,  
           javax.xml.namespace.QName _xmlType) {
        return 
          new  org.apache.axis.encoding.ser.BeanSerializer(
            _javaType, _xmlType, typeDesc);
    }

    /**
     * Get Custom Deserializer
     */
    public static org.apache.axis.encoding.Deserializer getDeserializer(
           java.lang.String mechType, 
           java.lang.Class _javaType,  
           javax.xml.namespace.QName _xmlType) {
        return 
          new  org.apache.axis.encoding.ser.BeanDeserializer(
            _javaType, _xmlType, typeDesc);
    }

}
