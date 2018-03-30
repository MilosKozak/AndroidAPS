package info.nightscout.androidaps.plugins.NSClientInternal.events;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.events.Event;

/**
 * Created by mike on 15.02.2017.
 */

public class EventNSClientRestart extends Event {

    public static void emit() {
        MainApp.bus().post(new EventNSClientStatus());
    }
}
