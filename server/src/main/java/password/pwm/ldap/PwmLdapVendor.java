package password.pwm.ldap;

import com.novell.ldapchai.provider.DirectoryVendor;

public enum PwmLdapVendor {
    ACTIVE_DIRECTORY(DirectoryVendor.ACTIVE_DIRECTORY, "MICROSOFT_ACTIVE_DIRECTORY"),
    EDIRECTORY(DirectoryVendor.EDIRECTORY, "NOVELL_EDIRECTORY"),
    OPEN_LDAP(DirectoryVendor.OPEN_LDAP),
    DIRECTORY_SERVER_389(DirectoryVendor.DIRECTORY_SERVER_389),
    ORACLE_DS(DirectoryVendor.ORACLE_DS),
    GENERIC(DirectoryVendor.GENERIC),

    ;

    private final DirectoryVendor chaiVendor;
    private final String[] otherNames;

    PwmLdapVendor( final DirectoryVendor directoryVendor, final String... otherNames ) {
        this.chaiVendor = directoryVendor;
        this.otherNames = otherNames;
    }

    public static PwmLdapVendor fromString(final String input) {
        if (input == null) {
            return null;
        }

        for (PwmLdapVendor vendor : PwmLdapVendor.values()) {
            if ( vendor.name().equals( input ) ) {
                return vendor;
            }

            if (vendor.otherNames != null) {
                for (final String otherName : vendor.otherNames) {
                    if (otherName.equals( input )) {
                        return vendor;
                    }
                }
            }
        }

        return null;
    }

    public static PwmLdapVendor fromChaiVendor(final DirectoryVendor directoryVendor) {
        for (PwmLdapVendor vendor : PwmLdapVendor.values()) {
            if (vendor.chaiVendor == directoryVendor) {
                return vendor;
            }
        }
        return null;
    }
}
