package password.pwm.util;

import gcardone.junidecode.Junidecode;

import java.io.UnsupportedEncodingException;

import org.apache.commons.text.similarity.LevenshteinDistance;

import password.pwm.util.logging.PwmLogger;

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;


public class AttributeCompareUtility {
    
    private static final PwmLogger LOGGER = PwmLogger.forClass(AttributeCompareUtility.class);

    private static final Integer LEVENSHTEIN_THRESHOLD = 1;
    
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
            if (value1 != null && value2 != null) {
                String preProcessedValue1 = value1.toLowerCase().trim();
                String preProcessedValue2 = value2.toLowerCase().trim();
                //replace all spacing / concatenation signs with one space
                preProcessedValue1 = preProcessedValue1.replaceAll("[\\s-_]+", " ");
                preProcessedValue2 = preProcessedValue2.replaceAll("[\\s-_]+", " ");
                //normalize
                preProcessedValue1 = Junidecode.unidecode(preProcessedValue1);
                preProcessedValue2 = Junidecode.unidecode(preProcessedValue2);
                //remove all spaces
                preProcessedValue1 = preProcessedValue1.replaceAll("[\\s-_]+", "");
                preProcessedValue2 = preProcessedValue2.replaceAll("[\\s-_]+", "");
                if (preProcessedValue1.equals(preProcessedValue2)) {
                    result = true;
                } else  {
                    final LevenshteinDistance levenshteinDistance = new LevenshteinDistance(LEVENSHTEIN_THRESHOLD);
                    result = levenshteinDistance.apply(preProcessedValue1, preProcessedValue2) != -1;
                }
            }
        } catch (Exception e) {
            LOGGER.error("error during param validation of '" + value1 + "' and '" + value2 + "', error: " + e.getMessage());

        }

        return result;
    }

}
