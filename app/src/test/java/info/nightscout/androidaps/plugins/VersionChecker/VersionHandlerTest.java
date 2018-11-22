package info.nightscout.androidaps.plugins.VersionChecker;

import org.apache.commons.lang3.StringUtils;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import info.nightscout.androidaps.BuildConfig;
import info.nightscout.androidaps.plugins.Version.VersionHandler;
import info.nightscout.androidaps.plugins.Version.Version;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class VersionHandlerTest extends VersionHandler {


    @Test
    public void parseTest()  throws Exception {
        String jsonString = readTestJson();

        VersionHandler handler = new VersionHandler();
        List<Version> versions = handler.parse(jsonString);

        assertEquals(3, versions.size());

        assertEquals("master", versions.get(0).getBranchTag());
        assertEquals("2.1_rc1", versions.get(1).getBranchTag());
    }

    @Test
    public void getVersionByChannelTest() throws Exception {
        String jsonString = readTestJson();

        VersionHandler handler = new VersionHandler();
        List<Version> versions = handler.parse(jsonString);

        Version version = handler.getVersionByChannel(Version.Channel.beta.name(), versions);
        assertNotNull(version);
        assertEquals("2.1_rc1", version.getBranchTag());

        version = handler.getVersionByChannel(Version.Channel.stable.name(), versions);
        assertNotNull(version);
        assertEquals("master", version.getBranchTag());
    }

    @Test
    public void getCurrentVersionTest() {
        VersionHandler handler = new VersionHandler();

        Version currentVersion = handler.currentVerssion();

        assertEquals(BuildConfig.BRANCH, currentVersion.getBranchTag());
    }

    @Test
    public void retrieveTest() {
        VersionHandlerTest sut = new VersionHandlerTest();

        String license = sut.retrieve("https://raw.githubusercontent.com/MilosKozak/AndroidAPS/master/LICENSE.txt");

        assertTrue(license.contains("Preamble"));
    }

    private static String readTestJson ()   throws IOException {
        File fileToRead = new File("src/test/res/version.json");
        List<String> fileLines = Files.readAllLines(fileToRead.toPath());
        return StringUtils.join(fileLines, StringUtils.EMPTY);
    }

    public boolean isConnected() {
        return true;
    }


}
