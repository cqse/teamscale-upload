package com.cqse.teamscaleupload;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class TeamscaleUpload {

    public static void main(String[] args) throws IOException {
        Path coverageFile = Paths.get("/home/k/proj/teamscale-upload/teamscaleupload/test.simple");
        String reportContent = new String(Files.readAllBytes(coverageFile));
        String project = "cgeo";
        System.out.println("cov:\n" + reportContent);
    }
}