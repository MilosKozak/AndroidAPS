package info.nightscout.androidaps.plugins.Update;

import java.util.Date;

public class Version {

    enum Channel {
        stable,
        beta,
        dev
    }

    private String channel;
    private Date date;
    private String version;
    private String branchTag;
    private String link;

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getBranchTag() {
        return branchTag;
    }

    public void setBranchTag(String branchTag) {
        this.branchTag = branchTag;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }
}
