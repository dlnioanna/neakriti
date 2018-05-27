package eu.anifantakis.neakriti.data.feed.gson;

import android.os.Parcel;
import android.os.Parcelable;

public class ImgAttributes implements Parcelable{
    String url;

    public ImgAttributes(String url){
        this.url = url;
    }

    protected ImgAttributes(Parcel in) {
        url = in.readString();
    }

    public static final Creator<ImgAttributes> CREATOR = new Creator<ImgAttributes>() {
        @Override
        public ImgAttributes createFromParcel(Parcel in) {
            return new ImgAttributes(in);
        }

        @Override
        public ImgAttributes[] newArray(int size) {
            return new ImgAttributes[size];
        }
    };

    public String getUrl(){
        if (url == null)
            url = "";

        return url;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(url);
    }
}
