package xyz.jienan.xkcd;


import io.objectbox.annotation.Entity;
import io.objectbox.annotation.Id;

/**
 * Created by jienanzhang on 09/07/2017.
 */

@Entity
public class XkcdPic {
    public String year;
    public String month;
    public String day;
    @Id(assignable = true)
    public long num;
    private String title;
    private String img;
    public String alt;
    public boolean large = false;
    public boolean special = false;

    public String getImg() {
        return XkcdSideloadUtils.sideload(this).img;
    }

    public String getRawImg() {
        return img;
    }

    public void setImg(String img) {
        this.img = img;
    }

    public String getRawTitle() {
        return title;
    }

    public String getTitle() {
        return XkcdSideloadUtils.sideload(this).title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public XkcdPic clone() {
        XkcdPic clone = new XkcdPic();
        clone.year = year;
        clone.month = month;
        clone.day = day;
        clone.num = num;
        clone.title = title;
        clone.img = img;
        clone.alt = alt;
        return clone;
    }
}
