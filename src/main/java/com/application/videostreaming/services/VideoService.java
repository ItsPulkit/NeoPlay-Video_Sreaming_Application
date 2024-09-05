package com.application.videostreaming.services;

import java.io.IOException;
import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.application.videostreaming.entities.Video;

public interface VideoService {

  Video createVideo(Video video, MultipartFile file);

  Video getByTitle(String title);

  Video getById(String id);

  List<Video> getAll();

  String processVideo(String videoId) throws IOException, RuntimeException;
}
