package info.nightscout.androidaps.plugins.pump.insight.app_layer.history.history_events;

public abstract class HistoryEventWithStart extends HistoryEvent {

    public abstract int getStartHour();

    public abstract int getStartMinute();

    public abstract int getStartSecond();

}
