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

    @Test
    public void testTooManyConsecutiveChars() {
        assertThat(tooManyConsecutiveChars(null, 4)).isFalse();
        assertThat(tooManyConsecutiveChars("", 4)).isFalse();

        assertThat(tooManyConsecutiveChars("12345678", 0)).isFalse();
        assertThat(tooManyConsecutiveChars("novell", 0)).isFalse();

        assertThat(tooManyConsecutiveChars("novell", 1)).isFalse();
        assertThat(tooManyConsecutiveChars("novell", 2)).isTrue(); // 'n' and 'o' are consecutive
        assertThat(tooManyConsecutiveChars("novell", 3)).isFalse();
        assertThat(tooManyConsecutiveChars("novell", 4)).isFalse();
        assertThat(tooManyConsecutiveChars("novell", 5)).isFalse();
        assertThat(tooManyConsecutiveChars("novell", 6)).isFalse();

        assertThat(tooManyConsecutiveChars("xyznovell", 3)).isTrue();
        assertThat(tooManyConsecutiveChars("novellabc", 3)).isTrue();
        assertThat(tooManyConsecutiveChars("novfghell", 3)).isTrue();

        assertThat(tooManyConsecutiveChars("Novell1235", 4)).isFalse();
        assertThat(tooManyConsecutiveChars("Novell1234", 4)).isTrue();
        assertThat(tooManyConsecutiveChars("1234Novell", 4)).isTrue();
        assertThat(tooManyConsecutiveChars("Nov1234ell", 4)).isTrue();

        assertThat(tooManyConsecutiveChars("123novabcellxyz", 4)).isFalse();
        assertThat(tooManyConsecutiveChars("123novabcellxyz", 3)).isTrue();

        assertThat(tooManyConsecutiveChars("abcdefghijklmnopqrstuvwxyz", -1)).isFalse();
        assertThat(tooManyConsecutiveChars("abcdefghijklmnopqrstuvwxyz", 0)).isFalse();
        assertThat(tooManyConsecutiveChars("abcdefghijklmnopqrstuvwxyz", 1)).isFalse();
        assertThat(tooManyConsecutiveChars("abcdefghijklmnopqrstuvwxyz", 27)).isFalse();
        assertThat(tooManyConsecutiveChars("abcdefghijklmnopqrstuvwxyz", 26)).isTrue();
        assertThat(tooManyConsecutiveChars("abcdefghijklmnopqrstuvwxyz", 25)).isTrue();
        assertThat(tooManyConsecutiveChars("abcdefghijklmnopqrstuvwxyz", 2)).isTrue();
    }
}
