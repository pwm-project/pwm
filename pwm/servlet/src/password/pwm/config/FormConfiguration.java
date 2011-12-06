/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2011 The PWM Project
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
import password.pwm.error.*;
import password.pwm.util.Helper;
import password.pwm.util.PwmLogger;

import java.io.Serializable;
import java.math.BigInteger;
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
 * <li>randomstring - normal string prefilled with a random value from password generator</li>
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
public class FormConfiguration implements Serializable {
// ------------------------------ FIELDS ------------------------------

    public enum Type {TEXT, EMAIL, NUMBER, PASSWORD, RANDOM, READONLY}

    private static final PwmLogger LOGGER = PwmLogger.getLogger(FormConfiguration.class);

    private final int minimumLength;
    private final int maximumLength;
    private final Type type;
    private final boolean required;
    private final boolean confirmationRequired;
    private final String label;
    private final String attributeName;

// -------------------------- STATIC METHODS --------------------------

    public static FormConfiguration parseConfigString(final String config)
            throws PwmOperationalException
    {
        if (config == null) {
            throw new PwmOperationalException(new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR,"input cannot be null"));
        }

        final StringTokenizer st = new StringTokenizer(config, ":");

        // attribute name
        final String attributeName = st.nextToken();

        // label
        final String label = st.nextToken();

        // type
        final Type type;
        {
            final String typeStr = st.nextToken();
            try {
                type = Type.valueOf(typeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new PwmOperationalException(new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR,"unknown type for form config: " + typeStr));
            }
        }

        //minimum length
        final int minimumLength;
        try {
            minimumLength = Integer.parseInt(st.nextToken());
        } catch (NumberFormatException e) {
            throw new PwmOperationalException(new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR,"invalid minimum length type for form config: " + e.getMessage()));
        }

        //maximum length
        final int maximumLength;
        try {
            maximumLength = Integer.parseInt(st.nextToken());
        } catch (NumberFormatException e) {
            throw new PwmOperationalException(new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR,"invalid maximum length type for form config: " + e.getMessage()));
        }

        //required
        final boolean required = Boolean.TRUE.toString().equalsIgnoreCase(st.nextToken());

        //confirmation
        final boolean confirmationRequired = Boolean.TRUE.toString().equalsIgnoreCase(st.nextToken());

        return new FormConfiguration(
                minimumLength,
                maximumLength,
                type,
                required,
                confirmationRequired,
                label,
                attributeName
        );
    }

// --------------------------- CONSTRUCTORS ---------------------------

    public FormConfiguration(final int minimumLength, final int maximumLength, final Type type, final boolean required, final boolean confirmationRequired, final String label, final String attributeName) {
        this.minimumLength = minimumLength;
        this.maximumLength = maximumLength;
        this.type = type;
        this.required = required;
        this.confirmationRequired = confirmationRequired;
        this.label = label;
        this.attributeName = attributeName;
    }


// --------------------- GETTER / SETTER METHODS ---------------------

    public String getAttributeName() {
        return attributeName;
    }

    public String getLabel() {
        return label;
    }

    public int getMaximumLength() {
        return maximumLength;
    }

    public int getMinimumLength() {
        return minimumLength;
    }

    public Type getType() {
        return type;
    }

    public boolean isConfirmationRequired() {
        return confirmationRequired;
    }

    public boolean isRequired() {
        return required;
    }

// ------------------------ CANONICAL METHODS ------------------------

    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FormConfiguration)) {
            return false;
        }

        final FormConfiguration parameterConfig = (FormConfiguration) o;

        return !(attributeName != null ? !attributeName.equals(parameterConfig.attributeName) : parameterConfig.attributeName != null);
    }

    public int hashCode() {
        return (attributeName != null ? attributeName.hashCode() : 0);
    }

    public String toString() {
        final StringBuilder sb = new StringBuilder();

        sb.append("FormConfiguration (attrName=").append(this.getAttributeName());
        sb.append(", label=").append(this.getLabel());
        sb.append(", type=").append(this.getType());
        sb.append(", minLength=").append(this.getMinimumLength());
        sb.append(", maxLength=").append(this.getMaximumLength());
        sb.append(", confirm=").append(String.valueOf(this.isConfirmationRequired()));
        sb.append(", required=").append(String.valueOf(this.isRequired()));
        sb.append(")");

        return sb.toString();
    }

// -------------------------- OTHER METHODS --------------------------

    public void checkValue(final String value)
            throws PwmDataValidationException, ChaiUnavailableException, PwmUnrecoverableException {
        //check if value is missing and required.
        if (required && (value == null || value.length() < 1)) {
            final ErrorInformation error = new ErrorInformation(PwmError.ERROR_FIELD_REQUIRED, null, this.label);
            throw new PwmDataValidationException(error);
        }

        switch (type) {
            case NUMBER:
                if (value != null && value.length() > 0) {
                    try {
                        new BigInteger(value);
                    } catch (NumberFormatException e) {
                        final ErrorInformation error = new ErrorInformation(PwmError.ERROR_FIELD_NOT_A_NUMBER, null, this.label);
                        throw new PwmDataValidationException(error);
                    }
                }
                break;


            case EMAIL:
                if (value != null && value.length() > 0) {
                    if (!Helper.testEmailAddress(value)) {
                        final ErrorInformation error = new ErrorInformation(PwmError.ERROR_FIELD_INVALID_EMAIL, null, this.label);
                        throw new PwmDataValidationException(error);
                    }
                }
                break;
        }

        if ((this.minimumLength > 0) && (value.length() > 0) && (value.length() < this.minimumLength)) {
            final ErrorInformation error = new ErrorInformation(PwmError.ERROR_FIELD_TOO_SHORT, null, this.label);
            throw new PwmDataValidationException(error);
        }

        if (value != null && value.length() > this.maximumLength) {
            final ErrorInformation error = new ErrorInformation(PwmError.ERROR_FIELD_TOO_LONG, null, this.label);
            throw new PwmDataValidationException(error);
        }
    }
}

