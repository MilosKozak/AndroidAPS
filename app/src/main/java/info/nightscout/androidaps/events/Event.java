package info.nightscout.androidaps.events;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;

public abstract class Event {
    @Override
    public String toString() {
        return ReflectionToStringBuilder.toString(this);
    }
}
