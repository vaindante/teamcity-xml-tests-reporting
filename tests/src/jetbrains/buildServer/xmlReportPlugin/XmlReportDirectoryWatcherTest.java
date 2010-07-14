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
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import jetbrains.buildServer.TempFiles;
import jetbrains.buildServer.agent.BuildProgressLogger;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static jetbrains.buildServer.xmlReportPlugin.TestUtil.getAbsoluteTestDataPath;
import static jetbrains.buildServer.xmlReportPlugin.TestUtil.readFile;

@SuppressWarnings({"ResultOfMethodCallIgnored"})
public class XmlReportDirectoryWatcherTest extends TestCase {
  private TempFiles myTempFiles;
  private File myWorkDir;

  private XmlReportDirectoryWatcher.Parameters createParameters(final BuildProgressLogger logger) {

    return new XmlReportDirectoryWatcher.Parameters() {
      public boolean isVerbose() {
        return true;
      }

      @NotNull
      public BuildProgressLogger getLogger() {
        return logger;
      }

      public boolean parseOutOfDate() {
        return false;
      }

      public long getBuildStartTime() {
        return 0;
      }

      @NotNull
      public List<String> getPathsToExclude() {
        return Collections.emptyList();
      }
    };
  }

  @Override
  @Before
  public void setUp() throws IOException {
    myTempFiles = new TempFiles();
    myWorkDir = myTempFiles.createTempDir();
  }

  @Override
  @After
  public void tearDown() throws Exception {
    myTempFiles.cleanup();
    super.tearDown();
  }

  private static class LinkedBlockingQueueMock<E> extends LinkedBlockingQueue<E> {
    private final StringBuilder myBuffer;

    public LinkedBlockingQueueMock(StringBuilder b) {
      myBuffer = b;
    }

    @Override
    public void put(E o) throws InterruptedException {
      final File f;
      if (o instanceof ReportData) {
        final ReportData d = (ReportData) o;
        f = d.getFile();
      } else {
        myBuffer.append("Trying to put illegal object to queue: ").append(o.toString());
        return;
      }
      myBuffer.append(f.getAbsolutePath()).append("\n");
    }
  }

  private File getFile(String name) {
    return new File(myWorkDir, name);
  }

  private File createFile(String name) {
    final File f = getFile(name);
    try {
      f.createNewFile();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return f;
  }

  private File createDir(String name) {
    final File f = getFile(name);
    f.mkdir();
    return f;
  }

  private void runTest(final String fileName, Set<File> input) throws Exception {
    final String expectedFile = getAbsoluteTestDataPath(fileName + ".gold", "watcher");
    final String resultsFile = expectedFile.replace(".gold", ".tmp");

    new File(resultsFile).delete();

    final StringBuilder results = new StringBuilder();
    final BuildLoggerForTesting logger = new BuildLoggerForTesting(results);
    final XmlReportDirectoryWatcher.Parameters parameters = createParameters(logger);
    final LinkedBlockingQueue<ReportData> queue = new LinkedBlockingQueueMock<ReportData>(results);

    final XmlReportDirectoryWatcher watcher = new XmlReportDirectoryWatcher(parameters, input, "junit", queue);
    watcher.signalStop();

//    final Thread stopper = new Thread(new Runnable() {
//      public void run() {
//        try {
//          Thread.sleep(2000);
//        } catch (InterruptedException e) {
//          e.printStackTrace();
//        }
//        myContext.checking(new Expectations() {
//          {
//            allowing(plugin).isStopped();
//            will(returnValue(true));
//          }
//        });
//      }
//    });
//    stopper.start();
    watcher.run();
    watcher.logTotals();

    final File expected = new File(expectedFile);
    String baseDir = myWorkDir.getCanonicalPath();
    String actual = results.toString().replace(baseDir, "##BASE_DIR##").replace("/", File.separator).replace("\\", File.separator).trim();
    String expectedContent = readFile(expected, true).trim();
    if (!expectedContent.equals(actual)) {
      final FileWriter resultsWriter = new FileWriter(resultsFile);
      resultsWriter.write(actual);
      resultsWriter.close();

      assertEquals(actual, expectedContent, actual);
    }
  }

  @Test
  public void testEmpty() throws Exception {
    runTest("empty", new HashSet<File>());
  }

  @Test
  public void testOneFile() throws Exception {
    final Set<File> files = new HashSet<File>();
    final File f = createFile("file.xml");
    files.add(f);
    runTest("oneFile", files);
    f.delete();
  }

  @Test
  public void testOneEmptyDir() throws Exception {
    final Set<File> files = new HashSet<File>();
    final File f = createDir("dir");
    files.add(f);
    runTest("oneEmptyDir", files);
    f.delete();
  }

  @Test
  public void testOneNotExists() throws Exception {
    final Set<File> files = new HashSet<File>();
    final File f = getFile("smth");
    files.add(f);
    runTest("oneNotExists", files);
    f.delete();
  }

  @Test
  public void testOneEmptyMask() throws Exception {
    final Set<File> files = new HashSet<File>();
    final File f = getFile("mask*");
    files.add(f);
    runTest("oneEmptyMask", files);
    f.delete();
  }

  /*
  @Test
  public void testOneDirWithFiles() throws Exception {
    final Set<File> files = new HashSet<File>();
    final File f = createDir("dir");
    files.add(f);
    final File f1 = createFileInDir(f, "f1");
    final File f2 = createFileInDir(f, "f2");
    final File f3 = createFileInDir(f, "f3");
    runTest("oneDirWithFiles", files);
    f1.delete();
    f2.delete();
    f3.delete();
    f.delete();
  }

  private static File createFileInDir(File dir, String file) {
    final File f = new File(dir, file);
    try {
      f.createNewFile();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return f;
  }

  */
}
