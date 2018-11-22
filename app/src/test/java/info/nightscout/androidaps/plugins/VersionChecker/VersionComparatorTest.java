package info.nightscout.androidaps.plugins.VersionChecker;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

import info.nightscout.androidaps.plugins.Version.VersionComparator;

public class VersionComparatorTest {

    VersionComparator comp = new VersionComparator();

    @Test
    public void comparatorTest() {

        assertTrue(comp.compare("2.0", "2.1") < 0);
        assertTrue(comp.compare("2.0.1", "2.1") < 0);
        assertTrue(comp.compare("2.0-rc2", "2.1") < 0);
        assertTrue(comp.compare("2.1-rc1", "2.1") < 0);

        assertTrue(comp.compare("2.0i-dev", "2.0f-dev") > 0);
        assertTrue(comp.compare("2.1", "2.0f-dev") > 0);
        assertTrue(comp.compare("2.0.1", "2.0f-dev") > 0);
        assertTrue(comp.compare("2.0.1-rc1", "2.0f-dev") > 0);

        assertTrue(comp.compare("2.0.1", "2.0.1-rc1") > 0);

    }
}
