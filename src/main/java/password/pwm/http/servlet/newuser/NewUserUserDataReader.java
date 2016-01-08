package password.pwm.http.servlet.newuser;

import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.ldap.UserDataReader;

import java.util.*;

class NewUserUserDataReader implements UserDataReader {
    private final Map<String, String> attributeData;

    NewUserUserDataReader(final Map<String, String> attributeData)
    {
        this.attributeData = attributeData;
    }

    @Override
    public String getUserDN()
    {
        return null;
    }

    @Override
    public String readStringAttribute(String attribute)
            throws ChaiUnavailableException, ChaiOperationException
    {
        return readStringAttribute(attribute, false);
    }

    @Override
    public String readStringAttribute(
            String attribute,
            boolean ignoreCache
    )
            throws ChaiUnavailableException, ChaiOperationException
    {
        return attributeData.get(attribute);
    }

    @Override
    public Date readDateAttribute(String attribute)
            throws ChaiUnavailableException, ChaiOperationException
    {
        return null;
    }

    @Override
    public Map<String, String> readStringAttributes(Collection<String> attributes)
            throws ChaiUnavailableException, ChaiOperationException
    {
        return readStringAttributes(attributes, false);
    }

    @Override
    public Map<String, String> readStringAttributes(
            Collection<String> attributes,
            boolean ignoreCache
    )
            throws ChaiUnavailableException, ChaiOperationException
    {
        final Map<String, String> returnObj = new LinkedHashMap<>();
        for (final String key : attributes) {
            returnObj.put(key, readStringAttribute(key));
        }
        return Collections.unmodifiableMap(returnObj);
    }
}
