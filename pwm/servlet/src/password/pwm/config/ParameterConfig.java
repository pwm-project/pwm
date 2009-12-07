/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
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

package password.pwm.config;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.Helper;
import password.pwm.PwmSession;
import password.pwm.Validator;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmException;
import password.pwm.error.ValidationException;
import password.pwm.util.PwmLogger;

import java.io.Serializable;
import java.util.StringTokenizer;

/**
 * Stores a parameter, its properties and possibly its value.  Suitable for use
 * in forms.
 * <p/>
 * Takes a parameter configuration string in the following form:
 * <p/>
 * <i>attributeName:label:type:minimumLength;maximumLength;required:confirm</i>
 * <p/>
 * <table border="1">
 * <tr><td>attributeName</td><td>Name of ldap attribute</td></tr>
 * <tr><td>label</td><td>Label to show to user (in error messages)</td></tr>
 * <tr><td>type</td><td>One of the following strings:
 * <ul>
 * <li>int - interger value</li>
 * <li>str - normal string (default)</li>
 * <li>email - normal string with email validation</li>
 * </ul>
 * <tr><td>minimumLength</td><td>Minimum length</td></tr>
 * <tr><td>maximumLength</td><td>Maximum length</td></tr>
 * <tr><td>required</td><td>Parameter is required (true/false)</td></tr>
 * <tr><td>confirm</td><td>Parameter requires confirmation (true/false)</td></tr>
 * </td></tr>
 * </table>
 * <br/><br/>
 * Example: <i>givenName:First Name:str:1:40:true:false:</i>
 *
 * @author Jason D. Rivard
 */
public class ParameterConfig implements Serializable {
// ------------------------------ FIELDS ------------------------------

    public enum Type { STRING, EMAIL, INT, PASSWORD }

    private static final PwmLogger LOGGER = PwmLogger.getLogger(ParameterConfig.class);

    private int minimumLength = 0;
    private int maximumLength = 40;
    private Type type = Type.STRING;
    private boolean required = false;
    private boolean confirmationRequired = false;
    private String label;
    private String attributeName;

    private String value;

// -------------------------- STATIC METHODS --------------------------

    public static ParameterConfig parseConfigString(final String config)
    {
        if (config == null) {
            throw new NullPointerException();
        }

        final ParameterConfig newConfig = new ParameterConfig();


        final StringTokenizer st = new StringTokenizer(config, ":");

        {  // attribute name
            newConfig.attributeName = st.nextToken();
        }

        // label
        if (st.hasMoreTokens()) {
            newConfig.label = st.nextToken();
        } else {
            newConfig.label = newConfig.attributeName;
        }

        //type
        if (st.hasMoreTokens()) {
            final String type = st.nextToken();
            if (type.equalsIgnoreCase("str")) {
                newConfig.type = Type.STRING;
            } else if (type.equalsIgnoreCase("int")) {
                newConfig.type = Type.INT;
            } else if (type.equalsIgnoreCase("mail") || type.equalsIgnoreCase("email")) {
                newConfig.type = Type.EMAIL;
            } else if (type.equalsIgnoreCase("pwd") || type.equalsIgnoreCase("password")) {
                newConfig.type = Type.PASSWORD;
            }
        }

        //mimimum length
        if (st.hasMoreTokens()) {
            final String minLengthStr = st.nextToken();
            try {
                newConfig.minimumLength = Integer.parseInt(minLengthStr);
            } catch (NumberFormatException e) {
                LOGGER.warn("error reading param config minimum length for attribute " + newConfig.attributeName);
            }
        }

        //maximum length
        if (st.hasMoreTokens()) {
            final String maxLengthStr = st.nextToken();
            try {
                newConfig.maximumLength = Integer.parseInt(maxLengthStr);
            } catch (NumberFormatException e) {
                LOGGER.warn("error reading param config maximum length for attribute " + newConfig.attributeName);
            }
        }

        //required
        if (st.hasMoreTokens()) {
            final String required = st.nextToken();
            if (required.equalsIgnoreCase("true")) {
                newConfig.required = true;
            }
        }

        //confirmation
        if (st.hasMoreTokens()) {
            final String confirmationRequired = st.nextToken();
            if (confirmationRequired.equalsIgnoreCase("true")) {
                newConfig.confirmationRequired = true;
            }
        }


        return newConfig;
    }

// --------------------------- CONSTRUCTORS ---------------------------

    private ParameterConfig()
    {
        super();

        // set defaults;
        this.attributeName = "";
        this.confirmationRequired = false;
        this.required = false;
        this.label = "";
        this.maximumLength = 40;
        this.minimumLength = 0;
    }

// --------------------- GETTER / SETTER METHODS ---------------------

    public String getAttributeName()
    {
        return attributeName;
    }

    public String getLabel()
    {
        return label;
    }

    public int getMaximumLength()
    {
        return maximumLength;
    }

    public int getMinimumLength()
    {
        return minimumLength;
    }

    public Type getType()
    {
        return type;
    }

    public String getValue()
    {
        return value;
    }

    public void setValue(final String value)
    {
        this.value = value;
    }

    public boolean isConfirmationRequired()
    {
        return confirmationRequired;
    }

    public boolean isRequired()
    {
        return required;
    }

// ------------------------ CANONICAL METHODS ------------------------

    public boolean equals(final Object o)
    {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ParameterConfig)) {
            return false;
        }

        final ParameterConfig parameterConfig = (ParameterConfig) o;

        return !(attributeName != null ? !attributeName.equals(parameterConfig.attributeName) : parameterConfig.attributeName != null);
    }

    public int hashCode()
    {
        return (attributeName != null ? attributeName.hashCode() : 0);
    }

    public String toString()
    {
        final StringBuilder sb = new StringBuilder();

        sb.append("ParameterConfig (attrName=").append(this.getAttributeName());
        sb.append(", label=").append(this.getLabel());
        sb.append(", minLength=").append(this.getMinimumLength());
        sb.append(", maxLength=").append(this.getMaximumLength());
        sb.append(", confirm=").append(String.valueOf(this.isConfirmationRequired()));
        sb.append(", required=").append(String.valueOf(this.isRequired()));
        sb.append(")");
        sb.append(" value=");
        sb.append(this.getValue());

        return sb.toString();
    }

// -------------------------- OTHER METHODS --------------------------

    public void valueIsValid(final PwmSession pwmSession)
            throws PwmException, ChaiUnavailableException
    {
        //check if value is missing and required.
        if (required && (value == null || value.length() < 1)) {
            final ErrorInformation error = new ErrorInformation(Message.ERROR_FIELD_REQUIRED, null, this.label);
            throw ValidationException.createValidationException(error);
        }

        switch (type) {
            case INT:
                try {
                    Integer.parseInt(this.getValue());
                } catch (NumberFormatException e) {
                    final ErrorInformation error = new ErrorInformation(Message.ERROR_FIELD_NOT_A_NUMBER, null, this.label);
                    throw ValidationException.createValidationException(error);
                }
                break;


            case EMAIL:
                if (!Helper.testEmailAddress(this.getValue())) {
                    final ErrorInformation error = new ErrorInformation(Message.ERROR_FIELD_INVALID_EMAIL, null, this.label);
                    throw ValidationException.createValidationException(error);
                }
                break;

            case PASSWORD:
                Validator.testPasswordAgainstPolicy(this.getValue(), pwmSession, true);
                break;
        }

        if ((this.minimumLength > 0) && (value.length() > 0) && (value.length() < this.minimumLength)) {
            final ErrorInformation error = new ErrorInformation(Message.ERROR_FIELD_TOO_SHORT, null, this.label);
            throw ValidationException.createValidationException(error);
        }

        if (value.length() > this.maximumLength) {
            final ErrorInformation error = new ErrorInformation(Message.ERROR_FIELD_TOO_LONG, null, this.label);
            throw ValidationException.createValidationException(error);
        }
    }
}

