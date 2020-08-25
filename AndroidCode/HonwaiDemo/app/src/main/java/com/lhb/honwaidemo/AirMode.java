package com.lhb.honwaidemo;

public class AirMode {
    private String modeStr;
    private String modeNum;
    private int modeCode;

    public String getModeStr() {
        return modeStr;
    }

    public String getModeNum() {
        return modeNum;
    }

    public int getModeCode() {
        return modeCode;
    }

    public void setModeStr(String modeStr) {
        this.modeStr = modeStr;
    }

    public void setModeNum(String modeNum) {
        this.modeNum = modeNum;
    }

    public void setModeCode(int modeCode) {
        this.modeCode = modeCode;
    }

    public AirMode(String modeStr, String modeNum, int modeCode) {
        this.modeStr = modeStr;
        this.modeNum = modeNum;
        this.modeCode = modeCode;
    }


    public AirMode(){

    }


}
