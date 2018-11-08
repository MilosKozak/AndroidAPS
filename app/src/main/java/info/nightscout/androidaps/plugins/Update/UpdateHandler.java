package info.nightscout.androidaps.plugins.Update;

import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.support.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.BuildConfig;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.logging.L;
import info.nightscout.utils.JsonHelper;

import static android.content.Context.CONNECTIVITY_SERVICE;

public class UpdateHandler {

    private static final Logger LOG = LoggerFactory.getLogger(L.CORE);

    public Version currentVerssion() {
        String buildVersion = BuildConfig.VERSION_NAME;

        Version currentVersion = new Version();
        String[] parts = buildVersion.split("-");
        currentVersion.setVersion(parts[0]);

        if (parts.length == 1) {
            currentVersion.setChannel(Version.Channel.stable.name());
        } else {
            if (parts[1].startsWith("releasecandidate")) {
                currentVersion.setChannel(Version.Channel.beta.name());
            } else if (parts[1].startsWith("dev")) {
                currentVersion.setChannel(Version.Channel.dev.name());
            }
        }

        currentVersion.setBranchTag(BuildConfig.BRANCH);

        return currentVersion;
    }


    @Nullable
    public Version getVersionByChannel(String channel, List<Version> versions) {
        for (int i = 0; i < versions.size(); i++) {
            Version v = versions.get(i);
            if (channel.equals(v.getChannel())) {
                return v;
            }
        }

        return null;
    }

    public List<Version> parse(String versionString) {
        List<Version> versions = new ArrayList();

        if (versionString != null) {
            try {
                JSONObject jsObject = new JSONObject(versionString);
                JSONArray versionsJson = jsObject.getJSONArray("versions");

                for (int i = 0; i < versionsJson.length() ; i++) {
                    JSONObject ver = versionsJson.getJSONObject(i);

                    Version version = new Version();
                    version.setChannel(JsonHelper.safeGetString(ver, "channel"));
                    version.setDate(JsonHelper.safeGetDate(ver, "date"));
                    version.setLink(JsonHelper.safeGetString(ver, "link"));;
                    version.setBranchTag(JsonHelper.safeGetString(ver, "branchTag"));
                    version.setVersion(JsonHelper.safeGetString(ver, "version"));

                    versions.add(version);
                }
            } catch (JSONException e) {
                LOG.error("Error parsing given String ", e);
            }
        }

        return versions;
    }

    public String retrieve(String reqUrl) {
        String response = null;
        if (isConnected()) {
            try {
                URL url = new URL(reqUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                // read the response
                InputStream in = new BufferedInputStream(conn.getInputStream());
                response = convertStreamToString(in);
            } catch (IOException e) {
                LOG.error("Exceptoin", e);;
            } catch (Exception e) {
                LOG.error("Exceptoin", e);
            }
        } else {
            LOG.debug("Github master version no checked. No connectivity");
        }

        return response;
    }

    private String convertStreamToString(InputStream in) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        StringBuilder sb = new StringBuilder();

        String line;
        try {
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                in.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return sb.toString();
    }


    // check network connection
    public static boolean isConnected() {
        ConnectivityManager connMgr = (ConnectivityManager) MainApp.instance().getApplicationContext().getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }



}
