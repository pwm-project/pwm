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
 * ProcessChaResRequest.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis 1.4 Apr 22, 2006 (06:55:48 PDT) WSDL2Java emitter.
 */

package password.pwm.ws.client.novell.pwdmgt;

public class ProcessChaResRequest  implements java.io.Serializable {
    private java.lang.String userDN;

    private java.lang.String[] chaAnswers;

    public ProcessChaResRequest() {
    }

    public ProcessChaResRequest(
           java.lang.String userDN,
           java.lang.String[] chaAnswers) {
           this.userDN = userDN;
           this.chaAnswers = chaAnswers;
    }


    /**
     * Gets the userDN value for this ProcessChaResRequest.
     * 
     * @return userDN
     */
    public java.lang.String getUserDN() {
        return userDN;
    }


    /**
     * Sets the userDN value for this ProcessChaResRequest.
     * 
     * @param userDN
     */
    public void setUserDN(java.lang.String userDN) {
        this.userDN = userDN;
    }


    /**
     * Gets the chaAnswers value for this ProcessChaResRequest.
     * 
     * @return chaAnswers
     */
    public java.lang.String[] getChaAnswers() {
        return chaAnswers;
    }


    /**
     * Sets the chaAnswers value for this ProcessChaResRequest.
     * 
     * @param chaAnswers
     */
    public void setChaAnswers(java.lang.String[] chaAnswers) {
        this.chaAnswers = chaAnswers;
    }

    public java.lang.String getChaAnswers(int i) {
        return this.chaAnswers[i];
    }

    public void setChaAnswers(int i, java.lang.String _value) {
        this.chaAnswers[i] = _value;
    }

    private java.lang.Object __equalsCalc = null;
    public synchronized boolean equals(java.lang.Object obj) {
        if (!(obj instanceof ProcessChaResRequest)) return false;
        ProcessChaResRequest other = (ProcessChaResRequest) obj;
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
            ((this.chaAnswers==null && other.getChaAnswers()==null) || 
             (this.chaAnswers!=null &&
              java.util.Arrays.equals(this.chaAnswers, other.getChaAnswers())));
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
        if (getChaAnswers() != null) {
            for (int i=0;
                 i<java.lang.reflect.Array.getLength(getChaAnswers());
                 i++) {
                java.lang.Object obj = java.lang.reflect.Array.get(getChaAnswers(), i);
                if (obj != null &&
                    !obj.getClass().isArray()) {
                    _hashCode += obj.hashCode();
                }
            }
        }
        __hashCodeCalc = false;
        return _hashCode;
    }

    // Type metadata
    private static org.apache.axis.description.TypeDesc typeDesc =
        new org.apache.axis.description.TypeDesc(ProcessChaResRequest.class, true);

    static {
        typeDesc.setXmlType(new javax.xml.namespace.QName("http://www.novell.com/pwdmgt/service", "processChaResRequest"));
        org.apache.axis.description.ElementDesc elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("userDN");
        elemField.setXmlName(new javax.xml.namespace.QName("http://www.novell.com/pwdmgt/service", "userDN"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("chaAnswers");
        elemField.setXmlName(new javax.xml.namespace.QName("http://www.novell.com/pwdmgt/service", "chaAnswers"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        elemField.setMaxOccursUnbounded(true);
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
