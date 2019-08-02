package info.nightscout.androidaps.interfaces;

import info.nightscout.androidaps.plugins.general.nsclient.UploadService;

/**
 *
 */
public interface NightscoutPlugin {

    UploadService getUploader();

    boolean isEnabled();

    void resend(String newdata);
}
