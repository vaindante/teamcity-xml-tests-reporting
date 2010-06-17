/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.xmlReportPlugin;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Pattern;
import jetbrains.buildServer.agent.BuildProgressLogger;
import jetbrains.buildServer.util.FileUtil;
import org.jetbrains.annotations.NotNull;

import static jetbrains.buildServer.xmlReportPlugin.XmlReportPlugin.LOG;
import static jetbrains.buildServer.xmlReportPlugin.XmlReportPluginUtil.SUPPORTED_REPORT_TYPES;
import static jetbrains.buildServer.xmlReportPlugin.XmlReportPluginUtil.isInspection;


public class XmlReportDirectoryWatcher extends Thread {
  private static final int SCAN_INTERVAL = 100;

  private final Parameters myParameters;

  private final LinkedBlockingQueue<ReportData> myReportQueue;
  private final ConcurrentMap<String, Set<File>> myPaths;
  private final Map<File, MaskData> myMaskHash;
  private final Map<String, TypeStatistics> myStatistics;

  private volatile boolean myStopSignaled;

  public interface Parameters {
    boolean isVerbose();
    @NotNull BuildProgressLogger getLogger();
    boolean parseOutOfDate();

    long getBuildStartTime();
  }


  public XmlReportDirectoryWatcher(@NotNull final Parameters parameters,
                                   @NotNull final Set<File> input,
                                   @NotNull final String type,
                                   @NotNull final LinkedBlockingQueue<ReportData> queue) {
    super("xml-report-plugin-DirectoryWatcher");

    myParameters = parameters;
    myPaths = new ConcurrentHashMap<String, Set<File>>();
    myReportQueue = queue;
    myMaskHash = new HashMap<File, MaskData>();
    myStatistics = new HashMap<String, TypeStatistics>();
    addPaths(input, type);
  }

  private static boolean isAntMask(File f) {
    final String mask = f.getAbsolutePath();
    return (mask.contains("*") || mask.contains("?"));
  }

  @Override
  public void run() {
    while (!myStopSignaled) {
      try {
        scanInput();
        Thread.sleep(SCAN_INTERVAL);
      } catch (Throwable e) {
        myParameters.getLogger().exception(e);
      }
    }
    scanInput();
  }

  public void signalStop() {
    myStopSignaled = true;
  }

  private void message(String message) {
    myParameters.getLogger().message(message);
    LOG.debug(message);
  }

  private void warning(String message) {
    myParameters.getLogger().warning(message);
    LOG.debug(message);
  }

  private void error(String message) {
    myParameters.getLogger().error(message);
    LOG.debug(message);
  }

  public void addPaths(Set<File> paths, String type) {
    // TODO concurrent porblems here. This is not an atomic operation for myPaths
    if (!myPaths.containsKey(type)) {
      if (isInspection(type) && hasInspections()) {
        warning("Two different inspections can not be processed during one build, skip " + SUPPORTED_REPORT_TYPES.get(type) + " reports");
        if (paths.size() > 0) {
          logPathsInTarget(paths, type, "Skip watching:");
        }
        return;
      }
      myStatistics.put(type, new TypeStatistics());
      myPaths.put(type, paths);
    } else {
      paths.removeAll(myPaths.get(type));
      myPaths.get(type).addAll(paths);
    }
    logWatchingPaths(paths, type);
    checkExistingPaths(paths, type);
  }

  private void logPathsInTarget(Set<File> paths, String type, String header) {
    final String target = startTarget(type);
    warning(header);
    for (File f : paths) {
      warning(f.getAbsolutePath());
    }
    myParameters.getLogger().targetFinished(target);
  }

  private boolean hasInspections() {
    for (String type : myPaths.keySet()) {
      if (isInspection(type)) {
        return true;
      }
    }
    return false;
  }

  private void logWatchingPaths(Set<File> paths, String type) {
    if (!SUPPORTED_REPORT_TYPES.containsKey(type)) {
      error("Illegal report type: " + type);
      return;
    }
    final String target = startTarget(type);
    String message = "Watching paths: ";
    if (paths.size() == 0) {
      message += "<no paths>";
      warning(message);
    } else {
      message(message);
      for (File f : paths) {
        message(f.getAbsolutePath());
      }
    }
    myParameters.getLogger().targetFinished(target);
  }

  private void checkExistingPaths(Set<File> paths, String type) {
    final Set<File> existingPaths = new HashSet<File>();
    for (File f : paths) {
      if (f.isFile() && isOutOfDate(f)) {
        existingPaths.add(f);
      } else if (f.isDirectory()) {
        final File[] files = f.listFiles();
        if ((files == null) || (files.length == 0)) return;
        for (File file : files) {
          if (file.isFile() && isOutOfDate(file)) {
            existingPaths.add(file);
          }
        }
      } else if (isAntMask(f)) {
        final MaskData md = getMask(f);
        for (File file : collectFiles(md.getPattern(), md.getBaseDir())) {
          if (isOutOfDate(file)) {
            existingPaths.add(file);
          }
        }
      }
    }
    if (existingPaths.size() > 0) {
      logPathsInTarget(existingPaths, type, "Found existing files:");
    }
  }

  private MaskData getMask(File f) {
    MaskData md;
    if (!myMaskHash.containsKey(f)) {
      final File baseDir = new File(getDirWithoutPattern(f.getAbsolutePath()));
      final Pattern pattern = Pattern.compile(FileUtil.convertAntToRegexp(f.getAbsolutePath().replace(baseDir.getAbsolutePath(), "")));
      md = new MaskData(baseDir, pattern);
      myMaskHash.put(f, md);
    } else {
      md = myMaskHash.get(f);
    }
    return md;
  }

  private void scanInput() {
    for (String type : myPaths.keySet()) {
      final TypeStatistics s = myStatistics.get(type);
      for (File f : myPaths.get(type)) {
        if (isGoodFile(f) && !s.getFiles().contains(f)) {
          s.getFiles().add(f);
          sendToQueue(type, f);
        } else if (f.isDirectory()) {
          final File[] files = f.listFiles();
          final Set<File> filesInDir = new HashSet<File>();
          if ((files != null) && (files.length > 0)) {
            for (File file : files) {
              processFile(type, s, filesInDir, file);
            }
          }
          addToStatistics(s.getDirs(), f, filesInDir);
        } else if (isAntMask(f)) {
          final Set<File> filesForMask = new HashSet<File>();
          final MaskData md = getMask(f);
          for (File file : collectFiles(md.getPattern(), md.getBaseDir())) {
            processFile(type, s, filesForMask, file);
          }
          addToStatistics(s.getMasks(), f, filesForMask);
        }
      }
    }
  }

  private void processFile(String type, TypeStatistics s, Set<File> files, File file) {
    if (isGoodFile(file)) {
      if (!s.getFiles().contains(file)) {
        sendToQueue(type, file);
      }
      s.getFiles().add(file);
      files.add(file);
    }
  }

  private boolean isGoodFile(File f) {
    return f.getName().endsWith(".xml") && f.isFile() && f.canRead() && timeConstraintsSatisfied(f);
  }

  private void sendToQueue(String type, File f) {
    LOG.debug("Sending " + f.getAbsolutePath() + " to report queue");
    try {
      myReportQueue.put(new ReportData(f, type));
    } catch (InterruptedException e) {
      myParameters.getLogger().exception(e);
      LOG.warn(e.toString(), e);
    }
  }

  private void addToStatistics(Map<File, Set<File>> paths, File keyFile, Set<File> files) {
    if (paths.containsKey(keyFile)) {
      paths.get(keyFile).addAll(files);
    } else {
      paths.put(keyFile, files);
    }
  }

  private boolean timeConstraintsSatisfied(File file) {
    return myParameters.parseOutOfDate() || !isOutOfDate(file);
  }

  private boolean isOutOfDate(File k) {
    return k.lastModified() < myParameters.getBuildStartTime();
  }

  public void logTotals() {
    for (String type : myPaths.keySet()) {
      final TypeStatistics s = myStatistics.get(type);
      final String target = startTarget(type);
      if (s.getFiles().size() > 0) {
        message(s.getFiles().size() + " file(s) found");
      } else {
        warning("no files found");
      }
      for (File d : s.getDirs().keySet()) {
        logFiles(s, d, s.getDirs().get(d));
        if (myParameters.isVerbose()) {
          for (File f : s.getDirs().get(d)) {
            message(f.getAbsolutePath() + " found");
          }
        }
        myPaths.get(type).removeAll(s.getDirs().get(d));
        myPaths.get(type).remove(d);
      }
      for (File m : s.getMasks().keySet()) {
        logFiles(s, m, s.getMasks().get(m));
        if (myParameters.isVerbose()) {
          for (File f : s.getMasks().get(m)) {
            message(f.getAbsolutePath() + " found");
          }
        }
        myPaths.get(type).removeAll(s.getMasks().get(m));
        myPaths.get(type).remove(m);
      }
      for (File f : s.getFiles()) {
        if (myParameters.isVerbose()) {
          message(f.getAbsolutePath() + " found");
        }
        myPaths.get(type).remove(f);
      }
      for (File f : myPaths.get(type)) {
        warning(f.getAbsolutePath() + " couldn't find any matching files");
      }
      myParameters.getLogger().targetFinished(target);
    }
  }

  private String startTarget(String type) {
    final String target = SUPPORTED_REPORT_TYPES.get(type) + " report watcher";
    myParameters.getLogger().targetStarted(target);
    return target;
  }

  private void logFiles(TypeStatistics s, File d, Set<File> files) {
    if (files.size() > 0) {
      if (myParameters.isVerbose()) {
        message(d.getAbsolutePath() + ": " + files.size() + " file(s) found");
      }
      s.getFiles().removeAll(files);
    } else {
      warning(d.getAbsolutePath() + ": no files found");
    }
  }

  private static String getDirWithoutPattern(String pathWithWildCard) {
    String t = pathWithWildCard.replace('\\', '/');
    final int firstStar = t.indexOf('*');
    final int firstQuest = t.indexOf('?');
    int mark = firstStar < 0 ? firstQuest :
      ((firstStar < firstQuest || firstQuest < 0) ? firstStar : firstQuest);

    final int lastSlash = t.lastIndexOf('/', mark);
    return lastSlash > 0 ? pathWithWildCard.substring(0, lastSlash) : "";
  }

  private static ArrayList<File> collectFiles(final Pattern pattern, final File basePatternDir) {
    final ArrayList<File> files = new ArrayList<File>();
    FileUtil.collectMatchedFiles(basePatternDir, pattern, files);
    return files;
  }

  private static class TypeStatistics {
    private final Set<File> myFiles;
    private final Map<File, Set<File>> myDirs;
    private final Map<File, Set<File>> myMasks;

    public TypeStatistics() {
      myFiles = new HashSet<File>();
      myDirs = new HashMap<File, Set<File>>();
      myMasks = new HashMap<File, Set<File>>();
    }

    public Set<File> getFiles() {
      return myFiles;
    }

    public Map<File, Set<File>> getDirs() {
      return myDirs;
    }

    public Map<File, Set<File>> getMasks() {
      return myMasks;
    }
  }

  private static class MaskData {
    private final File myBaseDir;
    private final Pattern myPattern;

    public MaskData(File baseDir, Pattern pattern) {
      myBaseDir = baseDir;
      myPattern = pattern;
    }

    public File getBaseDir() {
      return myBaseDir;
    }

    public Pattern getPattern() {
      return myPattern;
    }
  }
}