package password.pwm.util;


import password.pwm.AppProperty;
import password.pwm.config.Configuration;
import password.pwm.util.java.StringUtil;

public class ValueObfuscator
{
    private final Configuration configuration;

    public ValueObfuscator( final Configuration configuration )
    {
        this.configuration = configuration;
    }

    public String maskEmail( final String email )
    {
        if ( StringUtil.isEmpty( email ) )
        {
            return "";
        }

        final String regex = configuration.readAppProperty( AppProperty.TOKEN_MASK_EMAIL_REGEX );
        final String replace = configuration.readAppProperty( AppProperty.TOKEN_MASK_EMAIL_REPLACE );
        return email.replaceAll( regex, replace );
    }

    public String maskPhone( final String phone )
    {
        if ( StringUtil.isEmpty( phone ) )
        {
            return "";
        }

        final String regex = configuration.readAppProperty( AppProperty.TOKEN_MASK_SMS_REGEX );
        final String replace = configuration.readAppProperty( AppProperty.TOKEN_MASK_SMS_REPLACE );
        return phone.replaceAll( regex, replace );
    }
}



