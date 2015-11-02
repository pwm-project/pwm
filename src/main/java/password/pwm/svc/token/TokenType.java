package password.pwm.svc.token;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public enum TokenType {
    FORGOTTEN_PW("password.pwm.servlet.ForgottenPasswordServlet"),
    ACTIVATION("password.pwm.servlet.ActivateUserServlet"),
    NEWUSER_SMS("password.pwm.servlet.NewUserServlet_SMS"),
    NEWUSER_EMAIL("password.pwm.servlet.NewUserServlet_EMAIL"),

    ;

    private final Set<String> otherNames;

    TokenType(final String... otherNames) {
        final Set<String> otherNamesSet = new HashSet<>();
        if (otherNames != null) {
            otherNamesSet.addAll(Arrays.asList(otherNames));
        }
        otherNamesSet.add(getName());
        this.otherNames = Collections.unmodifiableSet(otherNamesSet);
    }

    public String getName() {
        return this.toString();
    }

    public boolean matchesName(final String input) {
        return otherNames.contains(input);
    }
}
