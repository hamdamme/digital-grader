package example;

import org.testng.Assert;
import org.testng.annotations.Test;

public class CalculatorTest {
    @Test public void adds() { Assert.assertEquals(Calculator.add(1, 2), 3); }
    @Test public void deliberatelyFails() { Assert.assertEquals(Calculator.add(2, 2), 5); }
}
