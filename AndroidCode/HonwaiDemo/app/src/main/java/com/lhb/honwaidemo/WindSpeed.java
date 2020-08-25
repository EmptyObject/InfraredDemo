package com.lhb.honwaidemo;

public class WindSpeed {
    private String windSpeedStr;
    private int  windSpeedCode;
    private int imagePath;

    public String getWindSpeedStr() {
        return windSpeedStr;
    }

    public int getWindSpeedCode() {
        return windSpeedCode;
    }

    public int getImagePath() {
        return imagePath;
    }

    public void setWindSpeedStr(String windSpeedStr) {
        this.windSpeedStr = windSpeedStr;
    }

    public void setWindSpeedCode(int windSpeedCode) {
        this.windSpeedCode = windSpeedCode;
    }

    public void setImagePath(int imagePath) {
        this.imagePath = imagePath;
    }

    public WindSpeed(String windSpeedStr, int windSpeedCode, int imagePath) {
        this.windSpeedStr = windSpeedStr;
        this.windSpeedCode = windSpeedCode;
        this.imagePath = imagePath;
    }
}
