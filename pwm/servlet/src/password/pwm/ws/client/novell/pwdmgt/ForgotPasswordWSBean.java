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
 * ForgotPasswordWSBean.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis 1.4 Apr 22, 2006 (06:55:48 PDT) WSDL2Java emitter.
 */

package password.pwm.ws.client.novell.pwdmgt;

public class ForgotPasswordWSBean  implements java.io.Serializable {
    private password.pwm.ws.client.novell.pwdmgt.NameValue[] users;

    private java.lang.String[] challengeQuestions;

    private java.lang.String configuredRtnLink;

    private boolean showReturnLink;

    private boolean showHint;

    private boolean showFullDN;

    private java.lang.String userDN;

    private java.lang.String userDisplayDN;

    private java.lang.String user;

    private boolean error;

    private java.lang.String message;

    private java.lang.String action;

    private java.lang.String hint;

    private java.lang.String rules;

    private boolean chaResInUse;

    private java.lang.String locked;

    private boolean timeout;

    private java.lang.String loginAttribute;

    public ForgotPasswordWSBean() {
    }

    public ForgotPasswordWSBean(
           password.pwm.ws.client.novell.pwdmgt.NameValue[] users,
           java.lang.String[] challengeQuestions,
           java.lang.String configuredRtnLink,
           boolean showReturnLink,
           boolean showHint,
           boolean showFullDN,
           java.lang.String userDN,
           java.lang.String userDisplayDN,
           java.lang.String user,
           boolean error,
           java.lang.String message,
           java.lang.String action,
           java.lang.String hint,
           java.lang.String rules,
           boolean chaResInUse,
           java.lang.String locked,
           boolean timeout,
           java.lang.String loginAttribute) {
           this.users = users;
           this.challengeQuestions = challengeQuestions;
           this.configuredRtnLink = configuredRtnLink;
           this.showReturnLink = showReturnLink;
           this.showHint = showHint;
           this.showFullDN = showFullDN;
           this.userDN = userDN;
           this.userDisplayDN = userDisplayDN;
           this.user = user;
           this.error = error;
           this.message = message;
           this.action = action;
           this.hint = hint;
           this.rules = rules;
           this.chaResInUse = chaResInUse;
           this.locked = locked;
           this.timeout = timeout;
           this.loginAttribute = loginAttribute;
    }


    /**
     * Gets the users value for this ForgotPasswordWSBean.
     * 
     * @return users
     */
    public password.pwm.ws.client.novell.pwdmgt.NameValue[] getUsers() {
        return users;
    }


    /**
     * Sets the users value for this ForgotPasswordWSBean.
     * 
     * @param users
     */
    public void setUsers(password.pwm.ws.client.novell.pwdmgt.NameValue[] users) {
        this.users = users;
    }

    public password.pwm.ws.client.novell.pwdmgt.NameValue getUsers(int i) {
        return this.users[i];
    }

    public void setUsers(int i, password.pwm.ws.client.novell.pwdmgt.NameValue _value) {
        this.users[i] = _value;
    }


    /**
     * Gets the challengeQuestions value for this ForgotPasswordWSBean.
     * 
     * @return challengeQuestions
     */
    public java.lang.String[] getChallengeQuestions() {
        return challengeQuestions;
    }


    /**
     * Sets the challengeQuestions value for this ForgotPasswordWSBean.
     * 
     * @param challengeQuestions
     */
    public void setChallengeQuestions(java.lang.String[] challengeQuestions) {
        this.challengeQuestions = challengeQuestions;
    }

    public java.lang.String getChallengeQuestions(int i) {
        return this.challengeQuestions[i];
    }

    public void setChallengeQuestions(int i, java.lang.String _value) {
        this.challengeQuestions[i] = _value;
    }


    /**
     * Gets the configuredRtnLink value for this ForgotPasswordWSBean.
     * 
     * @return configuredRtnLink
     */
    public java.lang.String getConfiguredRtnLink() {
        return configuredRtnLink;
    }


    /**
     * Sets the configuredRtnLink value for this ForgotPasswordWSBean.
     * 
     * @param configuredRtnLink
     */
    public void setConfiguredRtnLink(java.lang.String configuredRtnLink) {
        this.configuredRtnLink = configuredRtnLink;
    }


    /**
     * Gets the showReturnLink value for this ForgotPasswordWSBean.
     * 
     * @return showReturnLink
     */
    public boolean isShowReturnLink() {
        return showReturnLink;
    }


    /**
     * Sets the showReturnLink value for this ForgotPasswordWSBean.
     * 
     * @param showReturnLink
     */
    public void setShowReturnLink(boolean showReturnLink) {
        this.showReturnLink = showReturnLink;
    }


    /**
     * Gets the showHint value for this ForgotPasswordWSBean.
     * 
     * @return showHint
     */
    public boolean isShowHint() {
        return showHint;
    }


    /**
     * Sets the showHint value for this ForgotPasswordWSBean.
     * 
     * @param showHint
     */
    public void setShowHint(boolean showHint) {
        this.showHint = showHint;
    }


    /**
     * Gets the showFullDN value for this ForgotPasswordWSBean.
     * 
     * @return showFullDN
     */
    public boolean isShowFullDN() {
        return showFullDN;
    }


    /**
     * Sets the showFullDN value for this ForgotPasswordWSBean.
     * 
     * @param showFullDN
     */
    public void setShowFullDN(boolean showFullDN) {
        this.showFullDN = showFullDN;
    }


    /**
     * Gets the userDN value for this ForgotPasswordWSBean.
     * 
     * @return userDN
     */
    public java.lang.String getUserDN() {
        return userDN;
    }


    /**
     * Sets the userDN value for this ForgotPasswordWSBean.
     * 
     * @param userDN
     */
    public void setUserDN(java.lang.String userDN) {
        this.userDN = userDN;
    }


    /**
     * Gets the userDisplayDN value for this ForgotPasswordWSBean.
     * 
     * @return userDisplayDN
     */
    public java.lang.String getUserDisplayDN() {
        return userDisplayDN;
    }


    /**
     * Sets the userDisplayDN value for this ForgotPasswordWSBean.
     * 
     * @param userDisplayDN
     */
    public void setUserDisplayDN(java.lang.String userDisplayDN) {
        this.userDisplayDN = userDisplayDN;
    }


    /**
     * Gets the user value for this ForgotPasswordWSBean.
     * 
     * @return user
     */
    public java.lang.String getUser() {
        return user;
    }


    /**
     * Sets the user value for this ForgotPasswordWSBean.
     * 
     * @param user
     */
    public void setUser(java.lang.String user) {
        this.user = user;
    }


    /**
     * Gets the error value for this ForgotPasswordWSBean.
     * 
     * @return error
     */
    public boolean isError() {
        return error;
    }


    /**
     * Sets the error value for this ForgotPasswordWSBean.
     * 
     * @param error
     */
    public void setError(boolean error) {
        this.error = error;
    }


    /**
     * Gets the message value for this ForgotPasswordWSBean.
     * 
     * @return message
     */
    public java.lang.String getMessage() {
        return message;
    }


    /**
     * Sets the message value for this ForgotPasswordWSBean.
     * 
     * @param message
     */
    public void setMessage(java.lang.String message) {
        this.message = message;
    }


    /**
     * Gets the action value for this ForgotPasswordWSBean.
     * 
     * @return action
     */
    public java.lang.String getAction() {
        return action;
    }


    /**
     * Sets the action value for this ForgotPasswordWSBean.
     * 
     * @param action
     */
    public void setAction(java.lang.String action) {
        this.action = action;
    }


    /**
     * Gets the hint value for this ForgotPasswordWSBean.
     * 
     * @return hint
     */
    public java.lang.String getHint() {
        return hint;
    }


    /**
     * Sets the hint value for this ForgotPasswordWSBean.
     * 
     * @param hint
     */
    public void setHint(java.lang.String hint) {
        this.hint = hint;
    }


    /**
     * Gets the rules value for this ForgotPasswordWSBean.
     * 
     * @return rules
     */
    public java.lang.String getRules() {
        return rules;
    }


    /**
     * Sets the rules value for this ForgotPasswordWSBean.
     * 
     * @param rules
     */
    public void setRules(java.lang.String rules) {
        this.rules = rules;
    }


    /**
     * Gets the chaResInUse value for this ForgotPasswordWSBean.
     * 
     * @return chaResInUse
     */
    public boolean isChaResInUse() {
        return chaResInUse;
    }


    /**
     * Sets the chaResInUse value for this ForgotPasswordWSBean.
     * 
     * @param chaResInUse
     */
    public void setChaResInUse(boolean chaResInUse) {
        this.chaResInUse = chaResInUse;
    }


    /**
     * Gets the locked value for this ForgotPasswordWSBean.
     * 
     * @return locked
     */
    public java.lang.String getLocked() {
        return locked;
    }


    /**
     * Sets the locked value for this ForgotPasswordWSBean.
     * 
     * @param locked
     */
    public void setLocked(java.lang.String locked) {
        this.locked = locked;
    }


    /**
     * Gets the timeout value for this ForgotPasswordWSBean.
     * 
     * @return timeout
     */
    public boolean isTimeout() {
        return timeout;
    }


    /**
     * Sets the timeout value for this ForgotPasswordWSBean.
     * 
     * @param timeout
     */
    public void setTimeout(boolean timeout) {
        this.timeout = timeout;
    }


    /**
     * Gets the loginAttribute value for this ForgotPasswordWSBean.
     * 
     * @return loginAttribute
     */
    public java.lang.String getLoginAttribute() {
        return loginAttribute;
    }


    /**
     * Sets the loginAttribute value for this ForgotPasswordWSBean.
     * 
     * @param loginAttribute
     */
    public void setLoginAttribute(java.lang.String loginAttribute) {
        this.loginAttribute = loginAttribute;
    }

    private java.lang.Object __equalsCalc = null;
    public synchronized boolean equals(java.lang.Object obj) {
        if (!(obj instanceof ForgotPasswordWSBean)) return false;
        ForgotPasswordWSBean other = (ForgotPasswordWSBean) obj;
        if (obj == null) return false;
        if (this == obj) return true;
        if (__equalsCalc != null) {
            return (__equalsCalc == obj);
        }
        __equalsCalc = obj;
        boolean _equals;
        _equals = true && 
            ((this.users==null && other.getUsers()==null) || 
             (this.users!=null &&
              java.util.Arrays.equals(this.users, other.getUsers()))) &&
            ((this.challengeQuestions==null && other.getChallengeQuestions()==null) || 
             (this.challengeQuestions!=null &&
              java.util.Arrays.equals(this.challengeQuestions, other.getChallengeQuestions()))) &&
            ((this.configuredRtnLink==null && other.getConfiguredRtnLink()==null) || 
             (this.configuredRtnLink!=null &&
              this.configuredRtnLink.equals(other.getConfiguredRtnLink()))) &&
            this.showReturnLink == other.isShowReturnLink() &&
            this.showHint == other.isShowHint() &&
            this.showFullDN == other.isShowFullDN() &&
            ((this.userDN==null && other.getUserDN()==null) || 
             (this.userDN!=null &&
              this.userDN.equals(other.getUserDN()))) &&
            ((this.userDisplayDN==null && other.getUserDisplayDN()==null) || 
             (this.userDisplayDN!=null &&
              this.userDisplayDN.equals(other.getUserDisplayDN()))) &&
            ((this.user==null && other.getUser()==null) || 
             (this.user!=null &&
              this.user.equals(other.getUser()))) &&
            this.error == other.isError() &&
            ((this.message==null && other.getMessage()==null) || 
             (this.message!=null &&
              this.message.equals(other.getMessage()))) &&
            ((this.action==null && other.getAction()==null) || 
             (this.action!=null &&
              this.action.equals(other.getAction()))) &&
            ((this.hint==null && other.getHint()==null) || 
             (this.hint!=null &&
              this.hint.equals(other.getHint()))) &&
            ((this.rules==null && other.getRules()==null) || 
             (this.rules!=null &&
              this.rules.equals(other.getRules()))) &&
            this.chaResInUse == other.isChaResInUse() &&
            ((this.locked==null && other.getLocked()==null) || 
             (this.locked!=null &&
              this.locked.equals(other.getLocked()))) &&
            this.timeout == other.isTimeout() &&
            ((this.loginAttribute==null && other.getLoginAttribute()==null) || 
             (this.loginAttribute!=null &&
              this.loginAttribute.equals(other.getLoginAttribute())));
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
        if (getUsers() != null) {
            for (int i=0;
                 i<java.lang.reflect.Array.getLength(getUsers());
                 i++) {
                java.lang.Object obj = java.lang.reflect.Array.get(getUsers(), i);
                if (obj != null &&
                    !obj.getClass().isArray()) {
                    _hashCode += obj.hashCode();
                }
            }
        }
        if (getChallengeQuestions() != null) {
            for (int i=0;
                 i<java.lang.reflect.Array.getLength(getChallengeQuestions());
                 i++) {
                java.lang.Object obj = java.lang.reflect.Array.get(getChallengeQuestions(), i);
                if (obj != null &&
                    !obj.getClass().isArray()) {
                    _hashCode += obj.hashCode();
                }
            }
        }
        if (getConfiguredRtnLink() != null) {
            _hashCode += getConfiguredRtnLink().hashCode();
        }
        _hashCode += (isShowReturnLink() ? Boolean.TRUE : Boolean.FALSE).hashCode();
        _hashCode += (isShowHint() ? Boolean.TRUE : Boolean.FALSE).hashCode();
        _hashCode += (isShowFullDN() ? Boolean.TRUE : Boolean.FALSE).hashCode();
        if (getUserDN() != null) {
            _hashCode += getUserDN().hashCode();
        }
        if (getUserDisplayDN() != null) {
            _hashCode += getUserDisplayDN().hashCode();
        }
        if (getUser() != null) {
            _hashCode += getUser().hashCode();
        }
        _hashCode += (isError() ? Boolean.TRUE : Boolean.FALSE).hashCode();
        if (getMessage() != null) {
            _hashCode += getMessage().hashCode();
        }
        if (getAction() != null) {
            _hashCode += getAction().hashCode();
        }
        if (getHint() != null) {
            _hashCode += getHint().hashCode();
        }
        if (getRules() != null) {
            _hashCode += getRules().hashCode();
        }
        _hashCode += (isChaResInUse() ? Boolean.TRUE : Boolean.FALSE).hashCode();
        if (getLocked() != null) {
            _hashCode += getLocked().hashCode();
        }
        _hashCode += (isTimeout() ? Boolean.TRUE : Boolean.FALSE).hashCode();
        if (getLoginAttribute() != null) {
            _hashCode += getLoginAttribute().hashCode();
        }
        __hashCodeCalc = false;
        return _hashCode;
    }

    // Type metadata
    private static org.apache.axis.description.TypeDesc typeDesc =
        new org.apache.axis.description.TypeDesc(ForgotPasswordWSBean.class, true);

    static {
        typeDesc.setXmlType(new javax.xml.namespace.QName("http://www.novell.com/pwdmgt/service", "ForgotPasswordWSBean"));
        org.apache.axis.description.ElementDesc elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("users");
        elemField.setXmlName(new javax.xml.namespace.QName("http://www.novell.com/pwdmgt/service", "users"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.novell.com/pwdmgt/service", "NameValue"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        elemField.setMaxOccursUnbounded(true);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("challengeQuestions");
        elemField.setXmlName(new javax.xml.namespace.QName("http://www.novell.com/pwdmgt/service", "challengeQuestions"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        elemField.setMaxOccursUnbounded(true);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
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
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("showHint");
        elemField.setXmlName(new javax.xml.namespace.QName("http://www.novell.com/pwdmgt/service", "showHint"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "boolean"));
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("showFullDN");
        elemField.setXmlName(new javax.xml.namespace.QName("http://www.novell.com/pwdmgt/service", "showFullDN"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "boolean"));
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("userDN");
        elemField.setXmlName(new javax.xml.namespace.QName("http://www.novell.com/pwdmgt/service", "userDN"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("userDisplayDN");
        elemField.setXmlName(new javax.xml.namespace.QName("http://www.novell.com/pwdmgt/service", "userDisplayDN"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("user");
        elemField.setXmlName(new javax.xml.namespace.QName("http://www.novell.com/pwdmgt/service", "user"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("error");
        elemField.setXmlName(new javax.xml.namespace.QName("http://www.novell.com/pwdmgt/service", "error"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "boolean"));
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("message");
        elemField.setXmlName(new javax.xml.namespace.QName("http://www.novell.com/pwdmgt/service", "message"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("action");
        elemField.setXmlName(new javax.xml.namespace.QName("http://www.novell.com/pwdmgt/service", "action"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("hint");
        elemField.setXmlName(new javax.xml.namespace.QName("http://www.novell.com/pwdmgt/service", "hint"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("rules");
        elemField.setXmlName(new javax.xml.namespace.QName("http://www.novell.com/pwdmgt/service", "rules"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("chaResInUse");
        elemField.setXmlName(new javax.xml.namespace.QName("http://www.novell.com/pwdmgt/service", "chaResInUse"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "boolean"));
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("locked");
        elemField.setXmlName(new javax.xml.namespace.QName("http://www.novell.com/pwdmgt/service", "locked"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("timeout");
        elemField.setXmlName(new javax.xml.namespace.QName("http://www.novell.com/pwdmgt/service", "timeout"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "boolean"));
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("loginAttribute");
        elemField.setXmlName(new javax.xml.namespace.QName("http://www.novell.com/pwdmgt/service", "loginAttribute"));
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
