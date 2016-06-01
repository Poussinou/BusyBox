/*
 * Copyright (C) 2016 Jared Rummler <jared.rummler@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.jrummyapps.busybox.utils;

import com.jrummyapps.android.app.App;
import com.jrummyapps.android.io.common.Assets;
import com.jrummyapps.android.io.common.FileUtils;
import com.jrummyapps.android.io.common.IOUtils;
import com.jrummyapps.android.io.permissions.FilePermission;
import com.jrummyapps.android.shell.tools.BusyBox;
import com.jrummyapps.busybox.signing.ZipSigner;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipOutputStream;

public class BusyBoxZipHelper {

  public static final String UPDATE_BINARY = "update-binary";
  public static final String UPDATE_BINARY_PATH = "/META-INF/com/google/android/update-binary";
  public static final String UPDATE_SCRIPT_PATH = "/META-INF/com/google/android/updater-script";

  public static void createBusyboxRecoveryZip(BusyBox busybox, String installPath, File destination)
      throws IOException {
    List<String[]> paths = new ArrayList<>();
    paths.add(new String[]{busybox.path, installPath});
    File updaterScript = new File(App.getContext().getFilesDir(), "updater-script");
    createBusyBoxUpdaterScript(busybox, installPath, updaterScript);
    createUpdatePackage(destination, updaterScript, paths);
  }

  public static void createUpdatePackage(File destination, File script, List<String[]> paths) throws IOException {
    final File temp = new File(App.getContext().getFilesDir(), "tmp.zip");
    final File updateBinary = new File(App.getContext().getFilesDir(), UPDATE_BINARY);

    // Add the update-binary and updater-script to the files that will be zipped
    List<String[]> files = new ArrayList<>();
    files.addAll(paths);
    files.add(new String[]{updateBinary.getAbsolutePath(), UPDATE_BINARY_PATH});
    files.add(new String[]{script.getAbsolutePath(), UPDATE_SCRIPT_PATH});

    if (!updateBinary.exists()) {
      //noinspection OctalInteger
      Assets.transferAsset("signing/update-binary", "update-binary", FilePermission.MODE_0755);
    }

    // create the ZIP
    BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(temp), 4096);
    ZipOutputStream zos = new ZipOutputStream(bos);
    try {
      for (int i = 0; i < files.size(); i++) {
        String[] arr = files.get(i);
        zipFile(arr[0], zos, arr[1]);
      }
    } finally {
      IOUtils.closeQuietly(zos);
    }

    // sign the package
    ZipSigner.signZip(temp, destination);

    //noinspection ResultOfMethodCallIgnored
    temp.delete();
  }

  private static void zipFile(String path, ZipOutputStream out, String zipPath) throws IOException {
    final byte[] buffer = new byte[1024 * 4];
    File file = new File(path);
    if (file.isFile()) {
      FileInputStream in = new FileInputStream(file.getAbsolutePath());
      try {
        if (zipPath.startsWith(File.separator)) {
          zipPath = zipPath.replaceFirst(File.separator, "");
        }
        out.putNextEntry(new ZipEntry(zipPath));
        int len;
        while ((len = in.read(buffer)) > 0) {
          out.write(buffer, 0, len);
        }
        out.closeEntry();
      } catch (ZipException e) {
        e.printStackTrace();
      } finally {
        IOUtils.closeQuietly(in);
      }
    } else {
      String[] files = file.list();
      if (files != null && files.length > 0) {
        for (String filepath : files) {
          String relPath = new File(zipPath).getParent();
          if (relPath == null) relPath = "";
          zipFile(path + "/" + filepath, out, relPath + file.getName() + "/");
        }
      }
    }
  }

  private static void createBusyBoxUpdaterScript(BusyBox busybox, String installPath, File script) {
    StringBuilder sb = new StringBuilder();
    sb.append("ui_print(\"****************************************\");\n");
    sb.append("ui_print(\"* Script generated by JRummy Apps Inc. *\");\n");
    sb.append("ui_print(\"*        Follow us on Facebook!        *\");\n");
    sb.append("ui_print(\"*  http://www.facebook.com/JRummyApps  *\");\n");
    sb.append("ui_print(\"****************************************\");\n");
    sb.append("run_program(\"/sbin/sleep\", \"1\");\n");
    sb.append("ui_print(\"\");\n");
    sb.append("ui_print(\"\");\n");
    sb.append("\n");
    sb.append("show_progress(1.000000, 0);\n");
    sb.append("\n");
    sb.append("ui_print(\"mounting system read/write...\");\n");
    sb.append("run_program(\"/sbin/busybox\", \"mount\", \"/system\");\n");
    sb.append("set_progress(0.100000);\n");
    sb.append("\n");
    sb.append("ui_print(\"extracting files...\");\n");
    sb.append("package_extract_dir(\"system\", \"/system\");\n");
    sb.append("set_progress(0.300000);\n");
    sb.append("\n");
    sb.append("ui_print(\"setting permissions...\");\n");
    sb.append("set_progress(0.500000);\n");
    sb.append(String.format("set_perm(0, 0, 0755, \"%s\");\n", installPath));
    sb.append("\n");
    sb.append("ui_print(\"creating symlinks...\");\n");
    sb.append("symlink(\"").append(installPath).append("\"");
    List<String> applets = busybox.getApplets();
    String parent = new File(installPath).getParent();
    int n = 2;
    for (String applet : applets) {
      sb.append(",");
      if (n >= 3) {
        n = 0;
        sb.append("\n        ");
      } else {
        sb.append(' ');
      }
      sb.append("\"").append(parent).append('/').append(applet).append("\"");
      n++;
    }
    sb.append(");\n");
    sb.append("\n");
    sb.append("set_progress(0.700000);\n");
    sb.append("run_program(\"/sbin/busybox\", \"umount\", \"/system\");\n");
    sb.append("set_progress(1.000000);\n");
    sb.append("\n");
    sb.append("ui_print(\"****************************************\");\n");
    sb.append("ui_print(\"*          Install Complete!           *\");\n");
    sb.append("ui_print(\"****************************************\");\n");
    try {
      FileUtils.write(script, sb.toString());
    } catch (IOException ignored) {
    }
  }

}
