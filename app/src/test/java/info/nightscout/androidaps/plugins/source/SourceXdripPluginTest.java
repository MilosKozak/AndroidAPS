package info.nightscout.androidaps.plugins.source;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
public class SourceXdripPluginTest {

    @Test
    public void advancedFilteringSupported() {
        Assert.assertEquals(false, SourceXdripPlugin.INSTANCE.advancedFilteringSupported());
    }
}