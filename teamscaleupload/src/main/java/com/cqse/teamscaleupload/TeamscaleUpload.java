package com.cqse.teamscaleupload;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import okhttp3.Credentials;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class TeamscaleUpload {

    public static void main(String[] args) throws IOException {
        Path coverageFile = Paths.get("/home/k/proj/teamscale-upload/teamscaleupload/test.simple");
        String project = "cgeo";
        String userName = "msailer";
        String accessKey = "7mmMTVbuQIWkwZlF0mHHoZmG9AR5RwOW";

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("report", coverageFile.getFileName().toString(),
                        RequestBody.create(MediaType.get("application/octet-stream"), coverageFile.toFile()))
                .build();

        HttpUrl url = new HttpUrl.Builder().scheme("https").host("demo.teamscale.com")
                .addPathSegments("api/projects").addPathSegment(project).addPathSegments("external-analysis/session/auto-create/report")
                .addQueryParameter("t", "master:HEAD")
                .addQueryParameter("partition", "part")
                .addQueryParameter("format", "SIMPLE")
                .build();

        Request request = new Request.Builder()
                .header("Authorization", Credentials.basic(userName, accessKey))
                .url(url)
                .post(requestBody)
                .build();

        OkHttpClient client = new OkHttpClient.Builder().build();

        Response response = client.newCall(request).execute();
        if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);

        System.out.println(response.body().string());
    }
}