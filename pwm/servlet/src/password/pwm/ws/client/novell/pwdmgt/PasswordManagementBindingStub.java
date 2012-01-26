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
 * PasswordManagementBindingStub.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis 1.4 Apr 22, 2006 (06:55:48 PDT) WSDL2Java emitter.
 */

package password.pwm.ws.client.novell.pwdmgt;

public class PasswordManagementBindingStub extends org.apache.axis.client.Stub implements password.pwm.ws.client.novell.pwdmgt.PasswordManagement {
    private java.util.Vector cachedSerClasses = new java.util.Vector();
    private java.util.Vector cachedSerQNames = new java.util.Vector();
    private java.util.Vector cachedSerFactories = new java.util.Vector();
    private java.util.Vector cachedDeserFactories = new java.util.Vector();

    static org.apache.axis.description.OperationDesc [] _operations;

    static {
        _operations = new org.apache.axis.description.OperationDesc[4];
        _initOperationDesc1();
    }

    private static void _initOperationDesc1(){
        org.apache.axis.description.OperationDesc oper;
        org.apache.axis.description.ParameterDesc param;
        oper = new org.apache.axis.description.OperationDesc();
        oper.setName("processForgotConf");
        param = new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("http://www.novell.com/pwdmgt/service", "processForgotConfRequest"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName("http://www.novell.com/pwdmgt/service", "processForgotConfRequest"), password.pwm.ws.client.novell.pwdmgt.ProcessForgotConfRequest.class, false, false);
        oper.addParameter(param);
        oper.setReturnType(new javax.xml.namespace.QName("http://www.novell.com/pwdmgt/service", "ForgotPasswordConfWSBean"));
        oper.setReturnClass(password.pwm.ws.client.novell.pwdmgt.ForgotPasswordConfWSBean.class);
        oper.setReturnQName(new javax.xml.namespace.QName("http://www.novell.com/pwdmgt/service", "processForgotConfResponse"));
        oper.setStyle(org.apache.axis.constants.Style.DOCUMENT);
        oper.setUse(org.apache.axis.constants.Use.LITERAL);
        _operations[0] = oper;

        oper = new org.apache.axis.description.OperationDesc();
        oper.setName("processUser");
        param = new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("http://www.novell.com/pwdmgt/service", "processUserRequest"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName("http://www.novell.com/pwdmgt/service", "processUserRequest"), password.pwm.ws.client.novell.pwdmgt.ProcessUserRequest.class, false, false);
        oper.addParameter(param);
        oper.setReturnType(new javax.xml.namespace.QName("http://www.novell.com/pwdmgt/service", "ForgotPasswordWSBean"));
        oper.setReturnClass(password.pwm.ws.client.novell.pwdmgt.ForgotPasswordWSBean.class);
        oper.setReturnQName(new javax.xml.namespace.QName("http://www.novell.com/pwdmgt/service", "processUserResponse"));
        oper.setStyle(org.apache.axis.constants.Style.DOCUMENT);
        oper.setUse(org.apache.axis.constants.Use.LITERAL);
        _operations[1] = oper;

        oper = new org.apache.axis.description.OperationDesc();
        oper.setName("processChaRes");
        param = new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("http://www.novell.com/pwdmgt/service", "processChaResRequest"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName("http://www.novell.com/pwdmgt/service", "processChaResRequest"), password.pwm.ws.client.novell.pwdmgt.ProcessChaResRequest.class, false, false);
        oper.addParameter(param);
        oper.setReturnType(new javax.xml.namespace.QName("http://www.novell.com/pwdmgt/service", "ForgotPasswordWSBean"));
        oper.setReturnClass(password.pwm.ws.client.novell.pwdmgt.ForgotPasswordWSBean.class);
        oper.setReturnQName(new javax.xml.namespace.QName("http://www.novell.com/pwdmgt/service", "processChaResResponse"));
        oper.setStyle(org.apache.axis.constants.Style.DOCUMENT);
        oper.setUse(org.apache.axis.constants.Use.LITERAL);
        _operations[2] = oper;

        oper = new org.apache.axis.description.OperationDesc();
        oper.setName("processChgPwd");
        param = new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("http://www.novell.com/pwdmgt/service", "processChgPwdRequest"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName("http://www.novell.com/pwdmgt/service", "processChgPwdRequest"), password.pwm.ws.client.novell.pwdmgt.ProcessChgPwdRequest.class, false, false);
        oper.addParameter(param);
        oper.setReturnType(new javax.xml.namespace.QName("http://www.novell.com/pwdmgt/service", "ForgotPasswordWSBean"));
        oper.setReturnClass(password.pwm.ws.client.novell.pwdmgt.ForgotPasswordWSBean.class);
        oper.setReturnQName(new javax.xml.namespace.QName("http://www.novell.com/pwdmgt/service", "processChgPwdResponse"));
        oper.setStyle(org.apache.axis.constants.Style.DOCUMENT);
        oper.setUse(org.apache.axis.constants.Use.LITERAL);
        _operations[3] = oper;

    }

    public PasswordManagementBindingStub() throws org.apache.axis.AxisFault {
         this(null);
    }

    public PasswordManagementBindingStub(java.net.URL endpointURL, javax.xml.rpc.Service service) throws org.apache.axis.AxisFault {
         this(service);
         super.cachedEndpoint = endpointURL;
    }

    public PasswordManagementBindingStub(javax.xml.rpc.Service service) throws org.apache.axis.AxisFault {
        if (service == null) {
            super.service = new org.apache.axis.client.Service();
        } else {
            super.service = service;
        }
        ((org.apache.axis.client.Service)super.service).setTypeMappingVersion("1.1");
            java.lang.Class cls;
            javax.xml.namespace.QName qName;
            javax.xml.namespace.QName qName2;
            java.lang.Class beansf = org.apache.axis.encoding.ser.BeanSerializerFactory.class;
            java.lang.Class beandf = org.apache.axis.encoding.ser.BeanDeserializerFactory.class;
            java.lang.Class enumsf = org.apache.axis.encoding.ser.EnumSerializerFactory.class;
            java.lang.Class enumdf = org.apache.axis.encoding.ser.EnumDeserializerFactory.class;
            java.lang.Class arraysf = org.apache.axis.encoding.ser.ArraySerializerFactory.class;
            java.lang.Class arraydf = org.apache.axis.encoding.ser.ArrayDeserializerFactory.class;
            java.lang.Class simplesf = org.apache.axis.encoding.ser.SimpleSerializerFactory.class;
            java.lang.Class simpledf = org.apache.axis.encoding.ser.SimpleDeserializerFactory.class;
            java.lang.Class simplelistsf = org.apache.axis.encoding.ser.SimpleListSerializerFactory.class;
            java.lang.Class simplelistdf = org.apache.axis.encoding.ser.SimpleListDeserializerFactory.class;
            qName = new javax.xml.namespace.QName("http://www.novell.com/pwdmgt/service", "ForgotPasswordConfWSBean");
            cachedSerQNames.add(qName);
            cls = password.pwm.ws.client.novell.pwdmgt.ForgotPasswordConfWSBean.class;
            cachedSerClasses.add(cls);
            cachedSerFactories.add(beansf);
            cachedDeserFactories.add(beandf);

            qName = new javax.xml.namespace.QName("http://www.novell.com/pwdmgt/service", "ForgotPasswordWSBean");
            cachedSerQNames.add(qName);
            cls = password.pwm.ws.client.novell.pwdmgt.ForgotPasswordWSBean.class;
            cachedSerClasses.add(cls);
            cachedSerFactories.add(beansf);
            cachedDeserFactories.add(beandf);

            qName = new javax.xml.namespace.QName("http://www.novell.com/pwdmgt/service", "NameValue");
            cachedSerQNames.add(qName);
            cls = password.pwm.ws.client.novell.pwdmgt.NameValue.class;
            cachedSerClasses.add(cls);
            cachedSerFactories.add(beansf);
            cachedDeserFactories.add(beandf);

            qName = new javax.xml.namespace.QName("http://www.novell.com/pwdmgt/service", "processChaResRequest");
            cachedSerQNames.add(qName);
            cls = password.pwm.ws.client.novell.pwdmgt.ProcessChaResRequest.class;
            cachedSerClasses.add(cls);
            cachedSerFactories.add(beansf);
            cachedDeserFactories.add(beandf);

            qName = new javax.xml.namespace.QName("http://www.novell.com/pwdmgt/service", "processChgPwdRequest");
            cachedSerQNames.add(qName);
            cls = password.pwm.ws.client.novell.pwdmgt.ProcessChgPwdRequest.class;
            cachedSerClasses.add(cls);
            cachedSerFactories.add(beansf);
            cachedDeserFactories.add(beandf);

            qName = new javax.xml.namespace.QName("http://www.novell.com/pwdmgt/service", "processForgotConfRequest");
            cachedSerQNames.add(qName);
            cls = password.pwm.ws.client.novell.pwdmgt.ProcessForgotConfRequest.class;
            cachedSerClasses.add(cls);
            cachedSerFactories.add(beansf);
            cachedDeserFactories.add(beandf);

            qName = new javax.xml.namespace.QName("http://www.novell.com/pwdmgt/service", "processUserRequest");
            cachedSerQNames.add(qName);
            cls = password.pwm.ws.client.novell.pwdmgt.ProcessUserRequest.class;
            cachedSerClasses.add(cls);
            cachedSerFactories.add(beansf);
            cachedDeserFactories.add(beandf);

    }

    protected org.apache.axis.client.Call createCall() throws java.rmi.RemoteException {
        try {
            org.apache.axis.client.Call _call = super._createCall();
            if (super.maintainSessionSet) {
                _call.setMaintainSession(super.maintainSession);
            }
            if (super.cachedUsername != null) {
                _call.setUsername(super.cachedUsername);
            }
            if (super.cachedPassword != null) {
                _call.setPassword(super.cachedPassword);
            }
            if (super.cachedEndpoint != null) {
                _call.setTargetEndpointAddress(super.cachedEndpoint);
            }
            if (super.cachedTimeout != null) {
                _call.setTimeout(super.cachedTimeout);
            }
            if (super.cachedPortName != null) {
                _call.setPortName(super.cachedPortName);
            }
            java.util.Enumeration keys = super.cachedProperties.keys();
            while (keys.hasMoreElements()) {
                java.lang.String key = (java.lang.String) keys.nextElement();
                _call.setProperty(key, super.cachedProperties.get(key));
            }
            // All the type mapping information is registered
            // when the first call is made.
            // The type mapping information is actually registered in
            // the TypeMappingRegistry of the service, which
            // is the reason why registration is only needed for the first call.
            synchronized (this) {
                if (firstCall()) {
                    // must set encoding style before registering serializers
                    _call.setEncodingStyle(null);
                    for (int i = 0; i < cachedSerFactories.size(); ++i) {
                        java.lang.Class cls = (java.lang.Class) cachedSerClasses.get(i);
                        javax.xml.namespace.QName qName =
                                (javax.xml.namespace.QName) cachedSerQNames.get(i);
                        java.lang.Object x = cachedSerFactories.get(i);
                        if (x instanceof Class) {
                            java.lang.Class sf = (java.lang.Class)
                                 cachedSerFactories.get(i);
                            java.lang.Class df = (java.lang.Class)
                                 cachedDeserFactories.get(i);
                            _call.registerTypeMapping(cls, qName, sf, df, false);
                        }
                        else if (x instanceof javax.xml.rpc.encoding.SerializerFactory) {
                            org.apache.axis.encoding.SerializerFactory sf = (org.apache.axis.encoding.SerializerFactory)
                                 cachedSerFactories.get(i);
                            org.apache.axis.encoding.DeserializerFactory df = (org.apache.axis.encoding.DeserializerFactory)
                                 cachedDeserFactories.get(i);
                            _call.registerTypeMapping(cls, qName, sf, df, false);
                        }
                    }
                }
            }
            return _call;
        }
        catch (java.lang.Throwable _t) {
            throw new org.apache.axis.AxisFault("Failure trying to get the Call object", _t);
        }
    }

    public password.pwm.ws.client.novell.pwdmgt.ForgotPasswordConfWSBean processForgotConf(password.pwm.ws.client.novell.pwdmgt.ProcessForgotConfRequest bodyIn) throws java.rmi.RemoteException {
        if (super.cachedEndpoint == null) {
            throw new org.apache.axis.NoEndPointException();
        }
        org.apache.axis.client.Call _call = createCall();
        _call.setOperation(_operations[0]);
        _call.setUseSOAPAction(true);
        _call.setSOAPActionURI("http://www.novell.com/pwdmgt/service/processForgotConf");
        _call.setEncodingStyle(null);
        _call.setProperty(org.apache.axis.client.Call.SEND_TYPE_ATTR, Boolean.FALSE);
        _call.setProperty(org.apache.axis.AxisEngine.PROP_DOMULTIREFS, Boolean.FALSE);
        _call.setSOAPVersion(org.apache.axis.soap.SOAPConstants.SOAP11_CONSTANTS);
        _call.setOperationName(new javax.xml.namespace.QName("", "processForgotConf"));

        setRequestHeaders(_call);
        setAttachments(_call);
 try {        java.lang.Object _resp = _call.invoke(new java.lang.Object[] {bodyIn});

        if (_resp instanceof java.rmi.RemoteException) {
            throw (java.rmi.RemoteException)_resp;
        }
        else {
            extractAttachments(_call);
            try {
                return (password.pwm.ws.client.novell.pwdmgt.ForgotPasswordConfWSBean) _resp;
            } catch (java.lang.Exception _exception) {
                return (password.pwm.ws.client.novell.pwdmgt.ForgotPasswordConfWSBean) org.apache.axis.utils.JavaUtils.convert(_resp, password.pwm.ws.client.novell.pwdmgt.ForgotPasswordConfWSBean.class);
            }
        }
  } catch (org.apache.axis.AxisFault axisFaultException) {
  throw axisFaultException;
}
    }

    public password.pwm.ws.client.novell.pwdmgt.ForgotPasswordWSBean processUser(password.pwm.ws.client.novell.pwdmgt.ProcessUserRequest bodyIn) throws java.rmi.RemoteException {
        if (super.cachedEndpoint == null) {
            throw new org.apache.axis.NoEndPointException();
        }
        org.apache.axis.client.Call _call = createCall();
        _call.setOperation(_operations[1]);
        _call.setUseSOAPAction(true);
        _call.setSOAPActionURI("http://www.novell.com/pwdmgt/service/processUser");
        _call.setEncodingStyle(null);
        _call.setProperty(org.apache.axis.client.Call.SEND_TYPE_ATTR, Boolean.FALSE);
        _call.setProperty(org.apache.axis.AxisEngine.PROP_DOMULTIREFS, Boolean.FALSE);
        _call.setSOAPVersion(org.apache.axis.soap.SOAPConstants.SOAP11_CONSTANTS);
        _call.setOperationName(new javax.xml.namespace.QName("", "processUser"));

        setRequestHeaders(_call);
        setAttachments(_call);
 try {        java.lang.Object _resp = _call.invoke(new java.lang.Object[] {bodyIn});

        if (_resp instanceof java.rmi.RemoteException) {
            throw (java.rmi.RemoteException)_resp;
        }
        else {
            extractAttachments(_call);
            try {
                return (password.pwm.ws.client.novell.pwdmgt.ForgotPasswordWSBean) _resp;
            } catch (java.lang.Exception _exception) {
                return (password.pwm.ws.client.novell.pwdmgt.ForgotPasswordWSBean) org.apache.axis.utils.JavaUtils.convert(_resp, password.pwm.ws.client.novell.pwdmgt.ForgotPasswordWSBean.class);
            }
        }
  } catch (org.apache.axis.AxisFault axisFaultException) {
  throw axisFaultException;
}
    }

    public password.pwm.ws.client.novell.pwdmgt.ForgotPasswordWSBean processChaRes(password.pwm.ws.client.novell.pwdmgt.ProcessChaResRequest bodyIn) throws java.rmi.RemoteException {
        if (super.cachedEndpoint == null) {
            throw new org.apache.axis.NoEndPointException();
        }
        org.apache.axis.client.Call _call = createCall();
        _call.setOperation(_operations[2]);
        _call.setUseSOAPAction(true);
        _call.setSOAPActionURI("http://www.novell.com/pwdmgt/service/processChaRes");
        _call.setEncodingStyle(null);
        _call.setProperty(org.apache.axis.client.Call.SEND_TYPE_ATTR, Boolean.FALSE);
        _call.setProperty(org.apache.axis.AxisEngine.PROP_DOMULTIREFS, Boolean.FALSE);
        _call.setSOAPVersion(org.apache.axis.soap.SOAPConstants.SOAP11_CONSTANTS);
        _call.setOperationName(new javax.xml.namespace.QName("", "processChaRes"));

        setRequestHeaders(_call);
        setAttachments(_call);
 try {        java.lang.Object _resp = _call.invoke(new java.lang.Object[] {bodyIn});

        if (_resp instanceof java.rmi.RemoteException) {
            throw (java.rmi.RemoteException)_resp;
        }
        else {
            extractAttachments(_call);
            try {
                return (password.pwm.ws.client.novell.pwdmgt.ForgotPasswordWSBean) _resp;
            } catch (java.lang.Exception _exception) {
                return (password.pwm.ws.client.novell.pwdmgt.ForgotPasswordWSBean) org.apache.axis.utils.JavaUtils.convert(_resp, password.pwm.ws.client.novell.pwdmgt.ForgotPasswordWSBean.class);
            }
        }
  } catch (org.apache.axis.AxisFault axisFaultException) {
  throw axisFaultException;
}
    }

    public password.pwm.ws.client.novell.pwdmgt.ForgotPasswordWSBean processChgPwd(password.pwm.ws.client.novell.pwdmgt.ProcessChgPwdRequest bodyIn) throws java.rmi.RemoteException {
        if (super.cachedEndpoint == null) {
            throw new org.apache.axis.NoEndPointException();
        }
        org.apache.axis.client.Call _call = createCall();
        _call.setOperation(_operations[3]);
        _call.setUseSOAPAction(true);
        _call.setSOAPActionURI("http://www.novell.com/pwdmgt/service/processChgPwd");
        _call.setEncodingStyle(null);
        _call.setProperty(org.apache.axis.client.Call.SEND_TYPE_ATTR, Boolean.FALSE);
        _call.setProperty(org.apache.axis.AxisEngine.PROP_DOMULTIREFS, Boolean.FALSE);
        _call.setSOAPVersion(org.apache.axis.soap.SOAPConstants.SOAP11_CONSTANTS);
        _call.setOperationName(new javax.xml.namespace.QName("", "processChgPwd"));

        setRequestHeaders(_call);
        setAttachments(_call);
 try {        java.lang.Object _resp = _call.invoke(new java.lang.Object[] {bodyIn});

        if (_resp instanceof java.rmi.RemoteException) {
            throw (java.rmi.RemoteException)_resp;
        }
        else {
            extractAttachments(_call);
            try {
                return (password.pwm.ws.client.novell.pwdmgt.ForgotPasswordWSBean) _resp;
            } catch (java.lang.Exception _exception) {
                return (password.pwm.ws.client.novell.pwdmgt.ForgotPasswordWSBean) org.apache.axis.utils.JavaUtils.convert(_resp, password.pwm.ws.client.novell.pwdmgt.ForgotPasswordWSBean.class);
            }
        }
  } catch (org.apache.axis.AxisFault axisFaultException) {
  throw axisFaultException;
}
    }

}
