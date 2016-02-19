package com.cyanogenmod.updater.misc;

import org.json.JSONException;
import org.json.JSONObject;

public class FlashableZip {
    private String filePath;
    private String fileName;
    
    private String beforeInstall;
    private String afterInstall;
    
    public FlashableZip(String filePath) {
        setFilePath(filePath);
        this.beforeInstall = "";
        this.afterInstall = "";
    }
    
    public FlashableZip(JSONObject zipJson) {
        setFilePath(zipJson.optString("filePath"));
        this.beforeInstall = zipJson.optString("beforeInstall");
        this.afterInstall = zipJson.optString("afterInstall");
    }

    public String getFilePath() {
        return filePath;
    }
    
    public String getConvertedPath() {
        String convertedPath = filePath;
        for (String [] replacement : Constants.FILE_PATH_REPLACEMENTS) {
            convertedPath = convertedPath.replace(replacement[0], replacement[1]);
        }
        return convertedPath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
        this.fileName = filePath.substring(filePath.lastIndexOf('/')+1);
    }

    public String getFileName() {
        return fileName;
    }

    public String getBeforeInstall() {
        return beforeInstall;
    }

    public void setBeforeInstall(String beforeInstall) {
        this.beforeInstall = beforeInstall;
    }

    public String getAfterInstall() {
        return afterInstall;
    }

    public void setAfterInstall(String afterInstall) {
        this.afterInstall = afterInstall;
    }    
    
    public JSONObject toJSON() throws JSONException {
        JSONObject zipJson = new JSONObject();
        zipJson.put("filePath", filePath);
        zipJson.put("beforeInstall", beforeInstall);
        zipJson.put("afterInstall", afterInstall);
        return zipJson;
    }
}
