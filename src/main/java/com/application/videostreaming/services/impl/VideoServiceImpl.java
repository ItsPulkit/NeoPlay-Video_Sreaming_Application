package com.application.videostreaming.services.impl;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.application.videostreaming.entities.Video;
import com.application.videostreaming.repositories.VideoRepository;
import com.application.videostreaming.services.VideoService;

import jakarta.annotation.PostConstruct;

@Service
public class VideoServiceImpl implements VideoService {

  @PostConstruct
  public void init() {
    File file = new File(DIR);

    try {
      Files.createDirectories(Paths.get(HLS_DIR));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    if (!file.exists()) {
      file.mkdir();
      System.out.println("Video DIR Folder Created");

    } else {
      System.out.println("Video DIR Folder Already Exists");
    }
  }

  @Value("${files.video.hls}")
  String HLS_DIR;
  @Value("${files.video}")
  String DIR;

  @Autowired
  private VideoRepository videoRepository;

  public VideoServiceImpl(VideoRepository videoRepository) {
    this.videoRepository = videoRepository;
  }

  @Override
  public Video createVideo(Video video, MultipartFile file) {

    try {
      String filename = file.getOriginalFilename();
      InputStream fileinputstream = file.getInputStream();
      String contentType = file.getContentType();

      String cleanFolder = StringUtils.cleanPath(DIR);
      String cleanFileName = StringUtils.cleanPath(filename);

      Path path = Paths.get(cleanFolder, cleanFileName);
      System.out.println(cleanFileName);
      System.out.println(cleanFolder);
      System.out.println(contentType);
      System.out.println(path);

      Files.copy(fileinputstream, path, StandardCopyOption.REPLACE_EXISTING);
      video.setContentType(contentType);
      video.setFilePath(path.toString());

      Video savedVideo = videoRepository.save(video);

      processVideo(savedVideo.getVideoId());

      return savedVideo;

    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }

  }

  @Override
  public Video getByTitle(String title) {
    return videoRepository.findByTitle(title).orElseThrow(() -> new RuntimeException("Video Not Found By Title"));

  }

  @Override
  public Video getById(String id) {
    return videoRepository.findById(id).orElseThrow(() -> new RuntimeException("Video Not Found By ID"));
  }

  @Override
  public List<Video> getAll() {
    return videoRepository.findAll();
  }

  @Override
  public String processVideo(String videoId) throws IOException {

    Video v1 = this.getById(videoId);

    String path = v1.getFilePath();
    Path videoPath = Paths.get(path);
    Path outputPath = Paths.get(HLS_DIR, videoId);
    try {
      // Files.createDirectories(Paths.get(output360p));
      // Files.createDirectories(Paths.get(output720p));
      // Files.createDirectories(Paths.get(output1080p));

      // ffmpeg command
      outputPath = Paths.get(HLS_DIR, videoId);

      Files.createDirectories(outputPath);

      String ffmpegCmd = String.format(
          "ffmpeg -i \"%s\" -c:v libx264 -c:a aac -strict -2 -f hls -hls_time 10 -hls_list_size 0 -hls_segment_filename \"%s/segment_%%3d.ts\"  \"%s/master.m3u8\" ",
          videoPath, outputPath, outputPath);

      // StringBuilder ffmpegCmd = new StringBuilder();
      // ffmpegCmd.append("ffmpeg -i ")
      // .append(videoPath.toString())
      // .append(" -c:v libx264 -c:a aac")
      // .append(" ")
      // .append("-map 0:v -map 0:a -s:v:0 640x360 -b:v:0 800k ")
      // .append("-map 0:v -map 0:a -s:v:1 1280x720 -b:v:1 2800k ")
      // .append("-map 0:v -map 0:a -s:v:2 1920x1080 -b:v:2 5000k ")
      // .append("-var_stream_map \"v:0,a:0 v:1,a:0 v:2,a:0\" ")
      // .append("-master_pl_name
      // ").append(HSL_DIR).append(videoId).append("/master.m3u8 ")
      // .append("-f hls -hls_time 10 -hls_list_size 0 ")
      // .append("-hls_segment_filename
      // \"").append(HSL_DIR).append(videoId).append("/v%v/fileSequence%d.ts\" ")
      // .append("\"").append(HSL_DIR).append(videoId).append("/v%v/prog_index.m3u8\"");

      System.out.println(ffmpegCmd);

      // file this command
      ProcessBuilder processBuilder = new ProcessBuilder("cmd", "/c", ffmpegCmd);
      processBuilder.inheritIO();
      Process process = processBuilder.start();
      int exit = process.waitFor();
      if (exit != 0) {
        Files.delete(outputPath);
        throw new RuntimeException("video processing failed!!");

      }

      return videoId;

    } catch (IOException ex) {
      Files.delete(outputPath);
      System.err.println("IOException during video processing: " + ex.getMessage());
      ex.printStackTrace();
      throw new RuntimeException("Video processing failed due to IO error: " + ex.getMessage());
    } catch (InterruptedException e) {
      Files.delete(outputPath);
      System.err.println("InterruptedException during video processing: " +
          e.getMessage());
      e.printStackTrace();
      Thread.currentThread().interrupt(); // Restore the interrupted status
      throw new RuntimeException("Video processing was interrupted: " +
          e.getMessage());
    } catch (Exception e) {
      Files.delete(outputPath);
      System.err.println("Unexpected error during video processing: " + e.getMessage());
      e.printStackTrace();
      throw new RuntimeException("Unexpected error during video processing: " + e.getMessage());
    }

  }

}
