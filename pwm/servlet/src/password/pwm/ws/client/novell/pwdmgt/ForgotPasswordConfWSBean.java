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
 * ForgotPasswordConfWSBean.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis 1.4 Apr 22, 2006 (06:55:48 PDT) WSDL2Java emitter.
 */

package password.pwm.ws.client.novell.pwdmgt;

public class ForgotPasswordConfWSBean  implements java.io.Serializable {
    private java.lang.String configuredRtnLink;

    private boolean showReturnLink;

    public ForgotPasswordConfWSBean() {
    }

    public ForgotPasswordConfWSBean(
           java.lang.String configuredRtnLink,
           boolean showReturnLink) {
           this.configuredRtnLink = configuredRtnLink;
           this.showReturnLink = showReturnLink;
    }


    /**
     * Gets the configuredRtnLink value for this ForgotPasswordConfWSBean.
     * 
     * @return configuredRtnLink
     */
    public java.lang.String getConfiguredRtnLink() {
        return configuredRtnLink;
    }


    /**
     * Sets the configuredRtnLink value for this ForgotPasswordConfWSBean.
     * 
     * @param configuredRtnLink
     */
    public void setConfiguredRtnLink(java.lang.String configuredRtnLink) {
        this.configuredRtnLink = configuredRtnLink;
    }


    /**
     * Gets the showReturnLink value for this ForgotPasswordConfWSBean.
     * 
     * @return showReturnLink
     */
    public boolean isShowReturnLink() {
        return showReturnLink;
    }


    /**
     * Sets the showReturnLink value for this ForgotPasswordConfWSBean.
     * 
     * @param showReturnLink
     */
    public void setShowReturnLink(boolean showReturnLink) {
        this.showReturnLink = showReturnLink;
    }

    private java.lang.Object __equalsCalc = null;
    public synchronized boolean equals(java.lang.Object obj) {
        if (!(obj instanceof ForgotPasswordConfWSBean)) return false;
        ForgotPasswordConfWSBean other = (ForgotPasswordConfWSBean) obj;
        if (obj == null) return false;
        if (this == obj) return true;
        if (__equalsCalc != null) {
            return (__equalsCalc == obj);
        }
        __equalsCalc = obj;
        boolean _equals;
        _equals = true && 
            ((this.configuredRtnLink==null && other.getConfiguredRtnLink()==null) || 
             (this.configuredRtnLink!=null &&
              this.configuredRtnLink.equals(other.getConfiguredRtnLink()))) &&
            this.showReturnLink == other.isShowReturnLink();
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
        if (getConfiguredRtnLink() != null) {
            _hashCode += getConfiguredRtnLink().hashCode();
        }
        _hashCode += (isShowReturnLink() ? Boolean.TRUE : Boolean.FALSE).hashCode();
        __hashCodeCalc = false;
        return _hashCode;
    }

    // Type metadata
    private static org.apache.axis.description.TypeDesc typeDesc =
        new org.apache.axis.description.TypeDesc(ForgotPasswordConfWSBean.class, true);

    static {
        typeDesc.setXmlType(new javax.xml.namespace.QName("http://www.novell.com/pwdmgt/service", "ForgotPasswordConfWSBean"));
        org.apache.axis.description.ElementDesc elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("configuredRtnLink");
        elemField.setXmlName(new javax.xml.namespace.QName("http://www.novell.com/pwdmgt/service", "configuredRtnLink"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("showReturnLink");
        elemField.setXmlName(new javax.xml.namespace.QName("http://www.novell.com/pwdmgt/service", "showReturnLink"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "boolean"));
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
