package org.example.testng;

import org.example.compressor.CompressionDataDrivenTestNGIT;
import org.testng.TestNG;

/**
 * Запускает TestNG-тесты без принудительного System.exit, чтобы Maven продолжал жизненный цикл.
 */
public final class TestNgLauncher {

    private TestNgLauncher() {
    }

    public static void main(String[] args) {
        TestNG testNg = new TestNG();
        testNg.setTestClasses(new Class[]{CompressionDataDrivenTestNGIT.class});
        if (args != null && args.length > 0 && !args[0].isBlank()) {
            testNg.setGroups(args[0]);
        }
        testNg.setUseDefaultListeners(true);
        testNg.run();

        if (testNg.hasFailure()) {
            throw new IllegalStateException("TestNG run reported failures");
        }
    }
}
