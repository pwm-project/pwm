package password.pwm.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public class AttributeCompareUtilityTest {
    
    @Test
    public void testUsernameRegex() throws Exception {
        String value1 = "Ruckert";
        String value2 = "Rückert";
        assertThat(AttributeCompareUtility.compareNormalizedStringAttributes(value1, value2)).isTrue();
        value1 = "RUckert";
        value2 = "RÜckert";
        assertThat(AttributeCompareUtility.compareNormalizedStringAttributes(value1, value2)).isTrue();
        value1 = "Ruckert";
        value2 = "RÜckert";
        assertThat(AttributeCompareUtility.compareNormalizedStringAttributes(value1, value2)).isTrue();

        value1 = "Saint-Just-en-Chaussée";
        value2 = "saint just en chaussee";
        assertThat(AttributeCompareUtility.compareNormalizedStringAttributes(value1, value2)).isTrue();

        value1 = "Saint-Just-en-Chaussée";
        value2 = "saint   just en chaussee";
        assertThat(AttributeCompareUtility.compareNormalizedStringAttributes(value1, value2)).isTrue();

        value1 = "Strasse";
        value2 = "straße";
        assertThat(AttributeCompareUtility.compareNormalizedStringAttributes(value1, value2)).isTrue();

        value1 = "Darm stadt";
        value2 = "Darmstadt";
        assertThat(AttributeCompareUtility.compareNormalizedStringAttributes(value1, value2)).isTrue();
    }

}
