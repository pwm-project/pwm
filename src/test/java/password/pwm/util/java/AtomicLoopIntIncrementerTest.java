package password.pwm.util.java;

import org.junit.Assert;
import org.junit.Test;

public class AtomicLoopIntIncrementerTest {

    @Test
    public void testIncrementer() {
        AtomicLoopIntIncrementer atomicLoopIntIncrementer = new AtomicLoopIntIncrementer(5);
        for (int i = 0; i < 5; i++) {
            int next = atomicLoopIntIncrementer.next();
            Assert.assertEquals(i, next);
        }

        Assert.assertEquals(atomicLoopIntIncrementer.next(), 0);

        for (int i = 0; i < 5; i++) {
            atomicLoopIntIncrementer.next();
        }

        Assert.assertEquals(atomicLoopIntIncrementer.next(), 1);
    }
}
