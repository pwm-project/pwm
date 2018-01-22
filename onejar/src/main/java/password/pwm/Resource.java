package password.pwm;

import java.util.ResourceBundle;

enum Resource
{
    defaultContext,
    defaultWorkPathName,
    defaultPort,
    defaultLocalAddress,
    defaultWarFileName,;

    String getValue()
    {
        return readResource( this );
    }

    private static String readResource( final Resource resource )
    {
        final ResourceBundle bundle = ResourceBundle.getBundle( Resource.class.getName() );
        return bundle.getString( resource.name() );
    }

}
