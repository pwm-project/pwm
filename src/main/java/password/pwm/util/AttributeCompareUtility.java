package password.pwm.util;

import gcardone.junidecode.Junidecode;
import password.pwm.util.logging.PwmLogger;

import java.io.UnsupportedEncodingException;

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;


public class AttributeCompareUtility {
    
    private static final PwmLogger LOGGER = PwmLogger.forClass(AttributeCompareUtility.class);

    /**
     * Compares a pre-existing attribute value in the ldap directory. 
     * @param theUser The user to read the attribute from
     * @param attributeName  A valid attribute for the entry
     * @param attributeValue A string value to be tested against the ldap entry
     * @return true if the value exists in the ldap directory
     * @throws ChaiOperationException   If there is an error during the operation
     * @throws ChaiUnavailableException If the directory server(s) are unavailable
     */
    public static final boolean compareNormalizedStringAttributes(final ChaiUser theUser, final String attributeName, final String attributeValue) throws ChaiOperationException, ChaiUnavailableException {
        final String ldapValue = theUser.readStringAttribute(attributeName);
        return AttributeCompareUtility.compareNormalizedStringAttributes(ldapValue, attributeValue);
    }
    
    public static final boolean compareNormalizedStringAttributes(final String value1, final String value2) 
            
    {

        final byte[] ba;
        final byte[] ba2;
        //check encoding
        try {
            ba = value1.getBytes("UTF-8");
            ba2 = value2.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new UnsupportedOperationException(e);
        }

        boolean result = false;
        try {
            if (value1 != null && value2 != null && Junidecode.unidecode(value1).equals(Junidecode.unidecode(value2))) {
                result = true;
            }
        } catch (Exception e) {
            LOGGER.error("error during param validation of '" + value1 + "' and '" + value2 + "', error: " + e.getMessage());

        }

        return result;
    }

}
