package password.pwm.util;

import static org.assertj.core.api.Assertions.*;
import static password.pwm.util.PwmPasswordRuleValidator.*;

import org.junit.Test;

public class PwmPasswordRuleValidatorTest {
    @Test
    public void testContainsDisallowedValue() throws Exception {
        // containsDisallowedValue([new password], [disallowed value], [character match threshold])

        assertThat(containsDisallowedValue("n", "n", 0)).isTrue();
        assertThat(containsDisallowedValue("N", "n", 0)).isTrue();
        assertThat(containsDisallowedValue("n", "N", 0)).isTrue();
        assertThat(containsDisallowedValue("novell", "N", 0)).isTrue();
        assertThat(containsDisallowedValue("novell", "o", 0)).isTrue();
        assertThat(containsDisallowedValue("novell", "V", 0)).isTrue();
        assertThat(containsDisallowedValue("novell", "e", 0)).isTrue();
        assertThat(containsDisallowedValue("novell", "l", 0)).isTrue();
        assertThat(containsDisallowedValue("n", "n", 10)).isFalse(); // TODO: Need to verify this with Jason

        assertThat(containsDisallowedValue("novell", "novell", 0)).isTrue();
        assertThat(containsDisallowedValue("novell", "novell", 5)).isTrue();
        assertThat(containsDisallowedValue("novell", "novell", 6)).isTrue();
        assertThat(containsDisallowedValue("novell", "novell", 7)).isFalse(); // TODO: Need to verify this with Jason
        assertThat(containsDisallowedValue("novell", "foo", 0)).isFalse();
        assertThat(containsDisallowedValue("novell", "", 0)).isFalse();

        assertThat(containsDisallowedValue("love", "novell", 1)).isTrue();
        assertThat(containsDisallowedValue("love", "novell", 2)).isTrue();
        assertThat(containsDisallowedValue("love", "novell", 3)).isTrue();
        assertThat(containsDisallowedValue("love", "novell", 4)).isFalse();
        assertThat(containsDisallowedValue("love", "novell", 5)).isFalse();
        assertThat(containsDisallowedValue("love", "novell", 6)).isFalse();

        // Case shouldn't matter
        assertThat(containsDisallowedValue("LOVE", "novell", 1)).isTrue();
        assertThat(containsDisallowedValue("LOVE", "novell", 2)).isTrue();
        assertThat(containsDisallowedValue("LOVE", "novell", 3)).isTrue();
        assertThat(containsDisallowedValue("LOVE", "novell", 4)).isFalse();
        assertThat(containsDisallowedValue("LOVE", "novell", 5)).isFalse();
        assertThat(containsDisallowedValue("LOVE", "novell", 6)).isFalse();
        assertThat(containsDisallowedValue("love", "NOVELL", 1)).isTrue();
        assertThat(containsDisallowedValue("love", "NOVELL", 2)).isTrue();
        assertThat(containsDisallowedValue("love", "NOVELL", 3)).isTrue();
        assertThat(containsDisallowedValue("love", "NOVELL", 4)).isFalse();
        assertThat(containsDisallowedValue("love", "NOVELL", 5)).isFalse();
        assertThat(containsDisallowedValue("love", "NOVELL", 6)).isFalse();

        // Play around the threshold boundaries
        assertThat(containsDisallowedValue("foo-nove-bar", "novell", 4)).isTrue();
        assertThat(containsDisallowedValue("foo-ovel-bar", "novell", 4)).isTrue();
        assertThat(containsDisallowedValue("foo-vell-bar", "novell", 4)).isTrue();
        assertThat(containsDisallowedValue("foo-nove", "novell", 4)).isTrue();
        assertThat(containsDisallowedValue("foo-ovel", "novell", 4)).isTrue();
        assertThat(containsDisallowedValue("foo-vell", "novell", 4)).isTrue();
        assertThat(containsDisallowedValue("nove-bar", "novell", 4)).isTrue();
        assertThat(containsDisallowedValue("ovel-bar", "novell", 4)).isTrue();
        assertThat(containsDisallowedValue("vell-bar", "novell", 4)).isTrue();
    }
}
