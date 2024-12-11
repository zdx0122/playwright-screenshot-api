package ren.itest.screenshot.enums;

public enum FormatEnum {

    PNG(".png","png格式"),
    JPG(".jpg","jpg格式"),
    webp(".webp","webp格式");


    private String code;
    private String desc;
    FormatEnum(String code , String desc){
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    public static String getDescByCode(String code) {
        for (FormatEnum refer : FormatEnum.values())
            if (code == refer.getCode())
                return refer.getDesc();
        return null;
    }
}
