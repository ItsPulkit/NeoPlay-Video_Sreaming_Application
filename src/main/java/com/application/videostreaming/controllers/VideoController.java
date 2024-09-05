package com.application.videostreaming.controllers;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.application.videostreaming.entities.Video;
import com.application.videostreaming.payloads.CustomMessage;
import com.application.videostreaming.services.VideoService;

@RestController
@RequestMapping("/api/v1/videos")
public class VideoController {

  private static final long chunksize = 1024 * 1024;

  @Autowired
  private VideoService videoService;

  @Value("${files.video.hls}")
  String HLS_DIR;

  @PostMapping
  public ResponseEntity<?> create(@RequestParam("file") MultipartFile file,
      @RequestParam("title") String title,
      @RequestParam("description") String description) {

    Video video = new Video();
    video.setDescription(description);
    video.setTitle(title);
    video.setVideoId(UUID.randomUUID().toString());

    Video savedVideo = videoService.createVideo(video, file);
    if (savedVideo != null) {
      return ResponseEntity.status(HttpStatus.OK).body(savedVideo);
    } else {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(CustomMessage.builder()
          .message("Video Not Uploaded")
          .success(false)
          .build());
    }

  }

  @GetMapping
  public ResponseEntity<List<Video>> getAll() {

    return ResponseEntity.ok(videoService.getAll());

  }

  @GetMapping("/stream/{videoId}")
  public ResponseEntity<Resource> stream(@PathVariable("videoId") String videoId) {

    Video v1 = videoService.getById(videoId);
    String contentType = v1.getContentType();
    String path = v1.getFilePath();
    if (contentType == null)

    {
      contentType = "application/octet-stream";

    }
    Resource resource = new FileSystemResource(path);

    return ResponseEntity.ok().contentType(MediaType.parseMediaType(contentType)).body(resource);

  }

  @GetMapping("/stream/range/{videoId}")
  public ResponseEntity<Resource> streamRange(@PathVariable("videoId") String videoId,
      @RequestHeader(value = "Range", required = false) String range) {
    System.out.println("Range is " + range);
    Video v1 = videoService.getById(videoId);

    Path path = Paths.get(v1.getFilePath());

    String contentType = v1.getContentType();

    if (contentType == null)

    {
      contentType = "application/octet-stream";

    }
    Resource resource = new FileSystemResource(path);

    long fileLength = path.toFile().length();

    if (range == null) {
      return ResponseEntity.ok().contentType(MediaType.parseMediaType(contentType)).body(resource);
    }

    String[] ranges = range.replace("bytes=", "").split("-");

    long startRange = Long.parseLong(ranges[0]);
    long endRange;

    endRange = startRange + chunksize - 1;

    if (endRange > fileLength - 1) {
      endRange = fileLength - 1;

    }

    // if (ranges.length > 1) {
    // endRange = Long.parseLong(ranges[1]);
    // } else {
    // endRange = fileLength - 1;
    // }

    // if (endRange > fileLength - 1) {
    // endRange = fileLength - 1;

    // }
    System.out.println("Start: " + startRange);
    System.out.println("End: " + endRange);
    InputStream inputStream;
    try {
      inputStream = Files.newInputStream(path);
      inputStream.skip(startRange);

      long contentLength = endRange - startRange + 1;
      System.out.println("Content Length :" + contentLength);

      byte[] data = new byte[(int) contentLength];
      int read = inputStream.read(data, 0, data.length);
      System.out.println("Byte Data : " + read);
      HttpHeaders headers = new HttpHeaders();
      // headers.set("Content-Type", contentType);
      // headers.set("Content-Length", String.valueOf(contentLength));

      // headers.set("Accept-Ranges", "bytes");

      headers.set("Content-Range", "bytes " + startRange + "-" + endRange + "/" + fileLength);
      headers.set("Pragma", "no-cache");
      headers.set("X-Content-Type-Options", "nosniff");
      headers.add("Expires", "0");
      headers.add("Cache-Control", "no-cache, no-store, must-revalidate");
      headers.setContentLength(contentLength);

      return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).headers(headers)
          .contentType(MediaType.parseMediaType(contentType)).body(new ByteArrayResource(data));

    } catch (IOException ex) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

  }

  @GetMapping("/{videoId}/master.m3u8")
  public ResponseEntity<Resource> serveMasterFile(@PathVariable("videoId") String videoId) {

    Path path = Paths.get(HLS_DIR, videoId, "master.m3u8");
    System.out.println("Master File Path" + path);

    if (!Files.exists(path)) {
      return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }

    Resource resource = new FileSystemResource(path);

    return ResponseEntity.ok().header(HttpHeaders.CONTENT_TYPE, "application/vnd.apple.mpegurl").body(resource);

  }

  @GetMapping("/{videoId}/{segment}.ts")
  public ResponseEntity<Resource> serveSegmentFile(@PathVariable("videoId") String videoId,
      @PathVariable("segment") String segment) {

    Path path = Paths.get(HLS_DIR, videoId, segment + ".ts");
    System.out.println("Segment File Path" + path);

    if (!Files.exists(path)) {
      return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }

    Resource resource = new FileSystemResource(path);

    return ResponseEntity.ok().header(HttpHeaders.CONTENT_TYPE, "video/mp2t").body(resource);

  }

}
