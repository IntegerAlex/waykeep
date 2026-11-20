/*
 * Copyright 2013 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.waykeep.app.io.file.exporter;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.waykeep.app.data.ContentProviderUtils;
import com.waykeep.app.data.MarkerIterator;
import com.waykeep.app.data.RunMemoryStore;
import com.waykeep.app.data.backup.MemoriesCodec;
import com.waykeep.app.data.models.Marker;
import com.waykeep.app.data.models.RunMemory;
import com.waykeep.app.data.models.Track;
import com.waykeep.app.ui.markers.MarkerUtils;
import com.waykeep.app.util.FileUtils;

/**
 * KMZ track exporter.
 *
 * @author Jimmy Shih
 */
public class KMZTrackExporter implements TrackExporter {

    private static final String KMZ_IMAGES_DIR = "images";
    public static final String KMZ_KML_FILE = "doc.kml";

    private static final String TAG = KMZTrackExporter.class.getSimpleName();
    private static final int BUFFER_SIZE = 4096;

    private final ContentProviderUtils contentProviderUtils;
    private final KMLTrackExporter fileTrackExporter;

    private final Context context;

    public KMZTrackExporter(Context context, ContentProviderUtils contentProviderUtils, boolean exportPhotos) {
        this.context = context;
        this.contentProviderUtils = contentProviderUtils;
        this.fileTrackExporter =  new KMLTrackExporter(context, contentProviderUtils, exportPhotos);
    }

    @Override
    public boolean writeTrack(List<Track> tracks, @NonNull OutputStream outputStream) {
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            // Add kml file
            ZipEntry zipEntry = new ZipEntry(KMZ_KML_FILE);
            zipOutputStream.putNextEntry(zipEntry);

            boolean success = fileTrackExporter.writeTrack(tracks, zipOutputStream);
            zipOutputStream.closeEntry();
            if (!success) {
                Log.e(TAG, "Unable to write kml in kmz");
                return false;
            }

            // Add photos
            if (fileTrackExporter.isExportPhotos()) addImages(context, tracks ,zipOutputStream);

            // Add run memories: moods always, photos only when photos are exported.
            addMemories(tracks, zipOutputStream);
            return true;
        } catch (InterruptedException | IOException e) {
            Log.e(TAG, "Unable to write track", e);
            return false;
        }
    }

    private void addImages(Context context, List<Track> tracks, ZipOutputStream zipOutputStream) throws InterruptedException, IOException {
        for (Track track : tracks) {
            try (MarkerIterator markerIterator = contentProviderUtils.getMarkerIterator(track.id())) {
                while (markerIterator.hasNext()) {
                    if (Thread.interrupted()) throw new InterruptedException();

                    Marker marker = markerIterator.next();
                    if (marker.photoUrl() != null) {
                        Uri uriPhoto = marker.photoUrl();
                        boolean existsPhoto = MarkerUtils.buildInternalPhotoFile(context, track.id(), uriPhoto) != null;
                        if (existsPhoto) {
                            addImage(context, zipOutputStream, uriPhoto, marker);
                        }
                    }
                }
            }
        }
    }

    private void addImage(Context context, ZipOutputStream zipOutputStream, Uri uri, Marker marker) throws IOException {
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri)) {
            ZipEntry zipEntry = new ZipEntry(buildKmzImageFilePath(marker));
            zipOutputStream.putNextEntry(zipEntry);

            if (inputStream == null) throw new FileNotFoundException();

            readToOutputStream(inputStream, zipOutputStream);
            zipOutputStream.closeEntry();

            Log.i(TAG, "added an image to zip");
        } catch (FileNotFoundException e) {
            Log.e(TAG, "could not get image via FileProvider via uri " + uri);
        }
    }

    private void readToOutputStream(InputStream inputStream, OutputStream outputStream) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int byteCount;
        while ((byteCount = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, byteCount);
        }
    }

    /**
     * Builds and returns the path for the image that will be saved inside KMZ_IMAGES_DIR for the marker.
     */
    public static String buildKmzImageFilePath(Marker marker) {
        String ext = FileUtils.getExtension(marker.photoUrl());
        ext = ext == null ? "" : "." + ext;
        return KMZ_IMAGES_DIR + File.separatorChar + FileUtils.sanitizeFileName(marker.id().id() + ext);
    }

    private static final String KMZ_MEMORIES_DIR = "memories";
    private static final String KMZ_MEMORIES_MANIFEST = "memories.json";

    /**
     * Writes run memories (mood per track + optional photo) as a self-contained
     * {@code memories.json} plus {@code memories/<uuid>.<ext>} files. Older app
     * versions ignore both, and old backups without them import unchanged.
     */
    private void addMemories(List<Track> tracks, ZipOutputStream zipOutputStream) throws IOException {
        RunMemoryStore memoryStore = new RunMemoryStore(context);
        List<MemoriesCodec.Entry> entries = new ArrayList<>();
        for (Track track : tracks) {
            RunMemory memory = memoryStore.get(track.id());
            if (memory == null) {
                continue;
            }
            String photoName = null;
            if (memory.photoName() != null) {
                File photoFile = new File(FileUtils.getPhotoDir(context, track.id()), memory.photoName());
                if (fileTrackExporter.isExportPhotos() && photoFile.isFile()) {
                    String ext = memory.photoName().lastIndexOf('.') >= 0
                            ? memory.photoName().substring(memory.photoName().lastIndexOf('.')) : ".jpg";
                    String kmzPath = KMZ_MEMORIES_DIR + File.separatorChar + track.uuid() + ext;
                    addFile(zipOutputStream, photoFile, kmzPath);
                    photoName = kmzPath;
                }
            }
            entries.add(new MemoriesCodec.Entry(track.uuid().toString(), memory.mood(), photoName));
        }

        if (entries.isEmpty()) {
            return;
        }
        zipOutputStream.putNextEntry(new ZipEntry(KMZ_MEMORIES_MANIFEST));
        zipOutputStream.write(MemoriesCodec.encode(entries).getBytes(StandardCharsets.UTF_8));
        zipOutputStream.closeEntry();
    }

    private void addFile(ZipOutputStream zipOutputStream, File file, String zipPath) throws IOException {
        try (InputStream inputStream = new FileInputStream(file)) {
            zipOutputStream.putNextEntry(new ZipEntry(zipPath));
            readToOutputStream(inputStream, zipOutputStream);
            zipOutputStream.closeEntry();
        } catch (FileNotFoundException e) {
            Log.e(TAG, "could not add file to zip: " + zipPath);
        }
    }
}