/*
 * Copyright 2008 JetBrains s.r.o.
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

package jetbrains.buildServer.xmlReportPlugin.integration;

import jetbrains.buildServer.agent.AgentLifeCycleListener;
import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.agent.BaseServerLoggerFacade;
import jetbrains.buildServer.agent.BuildFinishedStatus;
import jetbrains.buildServer.agent.inspections.InspectionReporter;
import jetbrains.buildServer.util.EventDispatcher;
import static jetbrains.buildServer.xmlReportPlugin.TestUtil.*;
import static jetbrains.buildServer.xmlReportPlugin.TestUtil.WORKING_DIR;
import jetbrains.buildServer.xmlReportPlugin.XmlReportDataProcessor;
import jetbrains.buildServer.xmlReportPlugin.XmlReportPlugin;
import jetbrains.buildServer.xmlReportPlugin.XmlReportPluginUtil;
import static jetbrains.buildServer.xmlReportPlugin.integration.ReportFactory.*;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(JMock.class)
public class XmlReportPluginIntegrationTest {
  private static final String REPORTS_DIR = "reportsDir";

  private XmlReportPlugin myPlugin;
  private AgentRunningBuild myRunningBuild;
  private Map<String, String> myRunnerParams;
  private File myWorkingDir;
  private EventDispatcher<AgentLifeCycleListener> myEventDispatcher;
  private BaseServerLoggerFacadeForTesting myTestLogger;
  private List<MethodInvokation> myLogSequence;
  private List<UnexpectedInvokationException> myFailures;

  private Mockery myContext;
  private InspectionReporter myInspectionReporter;

  private InspectionReporter createInspectionReporter() {
    return myContext.mock(InspectionReporter.class);
  }

  private AgentRunningBuild createAgentRunningBuild(final Map<String, String> runParams, final File workingDirFile, final BaseServerLoggerFacade logger) {
    final AgentRunningBuild runningBuild = myContext.mock(AgentRunningBuild.class);
    myContext.checking(new Expectations() {
      {
        allowing(runningBuild).getBuildLogger();
        will(returnValue(logger));
        allowing(runningBuild).getRunnerParameters();
        will(returnValue(runParams));
        allowing(runningBuild).getWorkingDirectory();
        will(returnValue(workingDirFile));
        allowing(runningBuild).getBuildTempDirectory();
        will(returnValue(workingDirFile));
//        allowing(runningBuild).getBuildParameters();
//        will(returnValue(createBuildParametersMap(myRunnerParams)));
        ignoring(runningBuild);
      }
    });
    return runningBuild;
  }

//  private BuildParametersMap createBuildParametersMap(final Map<String, String> systemProperties) {
//    final BuildParametersMap params = myContext.mock(BuildParametersMap.class);
//    myContext.checking(new Expectations() {
//      {
//        oneOf(params).getSystemProperties();
//        will(returnValue(systemProperties));
//      }
//    });
//    return params;
//  }

  @Before
  public void setUp() {
    myContext = new JUnit4Mockery();

    myInspectionReporter = createInspectionReporter();
    myContext.checking(new Expectations() {
      {
        ignoring(myInspectionReporter);
      }
    });

    myLogSequence = new ArrayList<MethodInvokation>();
    myFailures = new ArrayList<UnexpectedInvokationException>();
    myTestLogger = new BaseServerLoggerFacadeForTesting(myFailures);

    myRunnerParams = new HashMap<String, String>();
    myWorkingDir = new File(WORKING_DIR);
    removeDir(myWorkingDir);
    myWorkingDir.mkdir();
    myRunningBuild = createAgentRunningBuild(myRunnerParams, myWorkingDir, myTestLogger);
    myEventDispatcher = EventDispatcher.create(AgentLifeCycleListener.class);
    myPlugin = new XmlReportPlugin(myEventDispatcher, myInspectionReporter);
    ReportFactory.setWorkingDir(WORKING_DIR);
  }

  private void removeDir(File dir) {
    File[] subDirs = dir.listFiles();
    if ((subDirs == null) || (subDirs.length == 0)) {
      dir.delete();
      return;
    }
    for (int i = 0; i < subDirs.length; ++i) {
      removeDir(subDirs[i]);
    }
  }

  private void isSilentWhenDisabled(BuildFinishedStatus status) {
    XmlReportPluginUtil.enableXmlReportParsing(myRunnerParams, EMPTY_REPORT_TYPE);
    XmlReportPluginUtil.setVerboseOutput(myRunnerParams, true);
    myTestLogger.setExpectedSequence(myLogSequence);

    myEventDispatcher.getMulticaster().buildStarted(myRunningBuild);
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    myEventDispatcher.getMulticaster().beforeRunnerStart(myRunningBuild);
    myEventDispatcher.getMulticaster().beforeBuildFinish(status);
    myContext.assertIsSatisfied();
    myTestLogger.checkIfAllExpectedMethodsWereInvoked();

    if (myFailures.size() > 0) {
      throw myFailures.get(0);
    }
  }

  @Test
  public void testIsSilentWhenDisabledFinishedSuccess() {
    isSilentWhenDisabled(BuildFinishedStatus.FINISHED_SUCCESS);
  }

  @Test
  public void testIsSilentWhenDisabledFinishedFailed() {
    isSilentWhenDisabled(BuildFinishedStatus.FINISHED_FAILED);
  }

  @Test
  public void testIsSilentWhenDisabledInterrupted() {
    isSilentWhenDisabled(BuildFinishedStatus.INTERRUPTED);
  }

  @Test
  public void testIsSilentWhenDisabledDoesNotExist() {
    isSilentWhenDisabled(BuildFinishedStatus.DOES_NOT_EXIST);
  }

  @Test
  public void testWarningWhenNoReportDirAppears() {
    XmlReportPluginUtil.enableXmlReportParsing(myRunnerParams, ANT_JUNIT_REPORT_TYPE);
    myRunnerParams.put(XmlReportPluginUtil.REPORT_DIRS, "reports");
    XmlReportPluginUtil.setVerboseOutput(myRunnerParams, true);

    List<Object> params = new ArrayList<Object>();
    params.add(MethodInvokation.ANY_VALUE);
    myLogSequence.add(new MethodInvokation("warning", params));
    myTestLogger.setExpectedSequence(myLogSequence);

    myEventDispatcher.getMulticaster().buildStarted(myRunningBuild);
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    myEventDispatcher.getMulticaster().beforeRunnerStart(myRunningBuild);
    myEventDispatcher.getMulticaster().beforeBuildFinish(BuildFinishedStatus.FINISHED_SUCCESS);
    myContext.assertIsSatisfied();
    myTestLogger.checkIfAllExpectedMethodsWereInvoked();

    if (myFailures.size() > 0) {
      throw myFailures.get(0);
    }
  }

  @Test
  public void testWarningWhenDirectoryWasNotActuallyDirectory() {
    XmlReportPluginUtil.enableXmlReportParsing(myRunnerParams, ANT_JUNIT_REPORT_TYPE);
    myRunnerParams.put(XmlReportPluginUtil.REPORT_DIRS, "reports");
    XmlReportPluginUtil.setVerboseOutput(myRunnerParams, true);

    final List<Object> params = new ArrayList<Object>();
    params.add(MethodInvokation.ANY_VALUE);
    myLogSequence.add(new MethodInvokation("warning", params));
    myTestLogger.setExpectedSequence(myLogSequence);

    myEventDispatcher.getMulticaster().buildStarted(myRunningBuild);
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    myEventDispatcher.getMulticaster().beforeRunnerStart(myRunningBuild);
    ReportFactory.createFile("reports");
    myEventDispatcher.getMulticaster().beforeBuildFinish(BuildFinishedStatus.FINISHED_SUCCESS);
    myContext.assertIsSatisfied();
    myTestLogger.checkIfAllExpectedMethodsWereInvoked();

    if (myFailures.size() > 0) {
      throw myFailures.get(0);
    }
  }

  private void warningWhenNoReportsFoundInDirectory(String reportType) {
    createDir(REPORTS_DIR);
    XmlReportPluginUtil.enableXmlReportParsing(myRunnerParams, reportType);
    myRunnerParams.put(XmlReportPluginUtil.REPORT_DIRS, REPORTS_DIR);
    XmlReportPluginUtil.setVerboseOutput(myRunnerParams, true);

    final List<Object> params = new ArrayList<Object>();
    params.add(MethodInvokation.ANY_VALUE);
    myLogSequence.add(new MethodInvokation("warning", params));
    myTestLogger.setExpectedSequence(myLogSequence);
  }

  private void warningWhenNoReportsFoundInDirectoryOnlyWrong(String reportType) {
    createDir(REPORTS_DIR);
    XmlReportPluginUtil.enableXmlReportParsing(myRunnerParams, reportType);
    myRunnerParams.put(XmlReportPluginUtil.REPORT_DIRS, REPORTS_DIR);
    XmlReportPluginUtil.setVerboseOutput(myRunnerParams, true);

    final List<Object> params = new ArrayList<Object>();
    params.add(MethodInvokation.ANY_VALUE);
    myLogSequence.add(new MethodInvokation("message", params));
    myLogSequence.add(new MethodInvokation("warning", params));
    myLogSequence.add(new MethodInvokation("message", params));
    myTestLogger.setExpectedSequence(myLogSequence);
  }

  @Test
  public void testAntJUnitWarningWhenNoReportsFoundInDirectory() {
    warningWhenNoReportsFoundInDirectory(ANT_JUNIT_REPORT_TYPE);

    myEventDispatcher.getMulticaster().buildStarted(myRunningBuild);
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    myEventDispatcher.getMulticaster().beforeRunnerStart(myRunningBuild);
    myEventDispatcher.getMulticaster().beforeBuildFinish(BuildFinishedStatus.FINISHED_SUCCESS);
    myContext.assertIsSatisfied();
    myTestLogger.checkIfAllExpectedMethodsWereInvoked();

    if (myFailures.size() > 0) {
      throw myFailures.get(0);
    }
  }

  @Test
  public void testAntJUnitWarningWhenNoReportsFoundInDirectoryOnlyWrongFile() {
    warningWhenNoReportsFoundInDirectoryOnlyWrong(ANT_JUNIT_REPORT_TYPE);

    myEventDispatcher.getMulticaster().buildStarted(myRunningBuild);
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    myEventDispatcher.getMulticaster().beforeRunnerStart(myRunningBuild);
    createFile(REPORTS_DIR + "\\somefile");
    myEventDispatcher.getMulticaster().beforeBuildFinish(BuildFinishedStatus.FINISHED_SUCCESS);
    myContext.assertIsSatisfied();
    myTestLogger.checkIfAllExpectedMethodsWereInvoked();

    if (myFailures.size() > 0) {
      throw myFailures.get(0);
    }
  }

  @Test
  public void testNUnitWarningWhenNoReportsFoundInDirectory() {
    warningWhenNoReportsFoundInDirectory(NUNIT_REPORT_TYPE);

    myEventDispatcher.getMulticaster().buildStarted(myRunningBuild);
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    myEventDispatcher.getMulticaster().beforeRunnerStart(myRunningBuild);
    myEventDispatcher.getMulticaster().beforeBuildFinish(BuildFinishedStatus.FINISHED_SUCCESS);
    myContext.assertIsSatisfied();
    myTestLogger.checkIfAllExpectedMethodsWereInvoked();

    if (myFailures.size() > 0) {
      throw myFailures.get(0);
    }
  }

//  @Test
//  public void testNUnitWarningWhenNoReportsFoundInDirectoryOnlyWrongFile() {
//    warningWhenNoReportsFoundInDirectoryOnlyWrong(NUNIT_REPORT_TYPE);
//
//    myEventDispatcher.getMulticaster().buildStarted(myRunningBuild);
//    try {
//      Thread.sleep(1000);
//    } catch (InterruptedException e) {
//      e.printStackTrace();
//    }
//    myEventDispatcher.getMulticaster().beforeRunnerStart(myRunningBuild);
//    createFile(REPORTS_DIR + "\\somefile");
//    myEventDispatcher.getMulticaster().beforeBuildFinish(BuildFinishedStatus.FINISHED_SUCCESS);
//    myContext.assertIsSatisfied();
//    myTestLogger.checkIfAllExpectedMethodsWereInvoked();
//
//    if (myFailures.size() > 0) {
//      throw myFailures.get(0);
//    }
//  }

  @Test
  public void testAntJUnitWarningWhenUnfinishedReportFoundInDirectory() {
    createDir(REPORTS_DIR);
    XmlReportPluginUtil.enableXmlReportParsing(myRunnerParams, ANT_JUNIT_REPORT_TYPE);
    myRunnerParams.put(XmlReportPluginUtil.REPORT_DIRS, REPORTS_DIR);
    XmlReportPluginUtil.setVerboseOutput(myRunnerParams, true);

    final List<Object> params = new ArrayList<Object>();
    params.add(MethodInvokation.ANY_VALUE);

    myLogSequence.add(new MethodInvokation("message", params));
    myLogSequence.add(new MethodInvokation("logSuiteStarted", params));
    myLogSequence.add(new MethodInvokation("logTestStarted", params));
    myLogSequence.add(new MethodInvokation("logTestFailed", params));
    myLogSequence.add(new MethodInvokation("logTestFinished", params));
    myLogSequence.add(new MethodInvokation("logSuiteFinished", params));
    myLogSequence.add(new MethodInvokation("warning", params));
    myLogSequence.add(new MethodInvokation("message", params));
    myTestLogger.setExpectedSequence(myLogSequence);

    myEventDispatcher.getMulticaster().buildStarted(myRunningBuild);
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    myEventDispatcher.getMulticaster().beforeRunnerStart(myRunningBuild);
    createUnfinishedReport(REPORTS_DIR + "\\report", ANT_JUNIT_REPORT_TYPE);
    myEventDispatcher.getMulticaster().beforeBuildFinish(BuildFinishedStatus.FINISHED_SUCCESS);
    myContext.assertIsSatisfied();
    myTestLogger.checkIfAllExpectedMethodsWereInvoked();

    if (myFailures.size() > 0) {
      throw myFailures.get(0);
    }
  }

  @Test
  public void testNotSilentWhenEnabled() {
    XmlReportPluginUtil.enableXmlReportParsing(myRunnerParams, ANT_JUNIT_REPORT_TYPE);
    XmlReportPluginUtil.setVerboseOutput(myRunnerParams, true);

    final List<Object> params = new ArrayList<Object>();
    params.add(MethodInvokation.ANY_VALUE);
    myLogSequence.add(new MethodInvokation("warning", params));
    myTestLogger.setExpectedSequence(myLogSequence);

    myEventDispatcher.getMulticaster().buildStarted(myRunningBuild);
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    myEventDispatcher.getMulticaster().beforeRunnerStart(myRunningBuild);
    myEventDispatcher.getMulticaster().beforeBuildFinish(BuildFinishedStatus.FINISHED_SUCCESS);
    myContext.assertIsSatisfied();
    myTestLogger.checkIfAllExpectedMethodsWereInvoked();

    if (myFailures.size() > 0) {
      throw myFailures.get(0);
    }
  }

  //test for Gradle bug after fixing
  @Test
  public void testLogSuiteWhenAppearsIn2Files() {
    XmlReportPluginUtil.enableXmlReportParsing(myRunnerParams, ANT_JUNIT_REPORT_TYPE);
    XmlReportPluginUtil.setVerboseOutput(myRunnerParams, true);
    XmlReportPluginUtil.setXmlReportDirs(myRunnerParams, "");

    final List<Object> params = new ArrayList<Object>();
    params.add(MethodInvokation.ANY_VALUE);
    myLogSequence.add(new MethodInvokation("logSuiteStarted", params));
    myLogSequence.add(new MethodInvokation("logTestStarted", params));
    myLogSequence.add(new MethodInvokation("logTestFinished", params));
    myLogSequence.add(new MethodInvokation("logSuiteFinished", params));

    myTestLogger.setExpectedSequence(myLogSequence);
    myTestLogger.addNotControlledMethod("message");
    myTestLogger.addNotControlledMethod("warning");

    myEventDispatcher.getMulticaster().buildStarted(myRunningBuild);
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    createFile("suite1", "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n" +
      "<testsuite errors=\"0\" failures=\"0\" hostname=\"ruspd-student3\" name=\"TestCase\" tests=\"1\" time=\"0.031\"\n" +
      "           timestamp=\"2008-10-30T17:11:25\">\n" +
      "  <properties/>\n" +
      "  <testcase classname=\"TestCase\" name=\"test\" time=\"0.031\"/>\n" +
      "</testsuite>");
    myEventDispatcher.getMulticaster().beforeRunnerStart(myRunningBuild);
    createFile("suite2", "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n" +
      "<testsuite errors=\"0\" failures=\"0\" hostname=\"ruspd-student3\" name=\"TestCase\" tests=\"1\" time=\"0.031\"\n" +
      "           timestamp=\"2008-10-30T17:11:25\">\n" +
      "  <properties/>\n" +
      "  <testcase classname=\"TestCase\" name=\"test\" time=\"0.031\"/>\n" +
      "</testsuite>");
    myEventDispatcher.getMulticaster().beforeBuildFinish(BuildFinishedStatus.FINISHED_SUCCESS);
    myContext.assertIsSatisfied();
    myTestLogger.checkIfAllExpectedMethodsWereInvoked();

    if (myFailures.size() > 0) {
      throw myFailures.get(0);
    }
  }

  //test for Gradle bug after fixing
  @Test
  public void testLogSuiteWhenAppearsIn2FilesOthersMustBeLogged() {
    XmlReportPluginUtil.enableXmlReportParsing(myRunnerParams, ANT_JUNIT_REPORT_TYPE);
    XmlReportPluginUtil.setVerboseOutput(myRunnerParams, true);
    XmlReportPluginUtil.setXmlReportDirs(myRunnerParams, "");

    final List<Object> params = new ArrayList<Object>();
    params.add(MethodInvokation.ANY_VALUE);

    final List<Object> param = new ArrayList<Object>();
    param.add("TestCase1");

    myLogSequence.add(new MethodInvokation("logSuiteStarted", param));
    myLogSequence.add(new MethodInvokation("logTestStarted", params));
    myLogSequence.add(new MethodInvokation("logTestFinished", params));
    myLogSequence.add(new MethodInvokation("logSuiteFinished", param));

    param.remove("TestCase1");
    param.add("TestCase2");

    myLogSequence.add(new MethodInvokation("logSuiteStarted", param));
    myLogSequence.add(new MethodInvokation("logTestStarted", params));
    myLogSequence.add(new MethodInvokation("logTestFinished", params));
    myLogSequence.add(new MethodInvokation("logTestStarted", params));
    myLogSequence.add(new MethodInvokation("logTestFailed", params));
    myLogSequence.add(new MethodInvokation("logTestFinished", params));
    myLogSequence.add(new MethodInvokation("logSuiteFinished", param));

    param.remove("TestCase2");
    param.add("TestCase3");

    myLogSequence.add(new MethodInvokation("logSuiteStarted", param));
    myLogSequence.add(new MethodInvokation("logTestStarted", params));
    myLogSequence.add(new MethodInvokation("logTestFinished", params));
    myLogSequence.add(new MethodInvokation("logTestStarted", params));
    myLogSequence.add(new MethodInvokation("logTestFailed", params));
    myLogSequence.add(new MethodInvokation("logTestFinished", params));
    myLogSequence.add(new MethodInvokation("logSuiteFinished", param));

    myTestLogger.setExpectedSequence(myLogSequence);
    myTestLogger.addNotControlledMethod("message");
    myTestLogger.addNotControlledMethod("warning");

    myEventDispatcher.getMulticaster().buildStarted(myRunningBuild);
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    createFile("suite1", "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n" +
      "<testsuite errors=\"0\" failures=\"0\" hostname=\"ruspd-student3\" name=\"TestCase1\" tests=\"1\" time=\"0.031\"\n" +
      "           timestamp=\"2008-10-30T17:11:25\">\n" +
      "  <properties/>\n" +
      "  <testcase classname=\"TestCase\" name=\"test\" time=\"0.031\"/>\n" +
      "</testsuite>");
    myEventDispatcher.getMulticaster().beforeRunnerStart(myRunningBuild);
    createFile("suite2", "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n" +
      "<testsuites>" +
      "<testsuite errors=\"0\" failures=\"0\" hostname=\"ruspd-student3\" name=\"TestCase2\" tests=\"2\" time=\"0.062\"\n" +
      "           timestamp=\"2008-10-30T17:11:25\">\n" +
      "  <properties/>\n" +
      "  <testcase classname=\"TestCase2\" name=\"test1\" time=\"0.031\"/>\n" +
      "  <testcase classname=\"TestCase2\" name=\"test2\" time=\"0.031\">\n" +
      "    <failure message=\"Assertion message form test\" type=\"junit.framework.AssertionFailedError\">\n" +
      "      junit.framework.AssertionFailedError: Assertion message form test\n" +
      "      at TestCase.test(Unknown Source)\n" +
      "    </failure>\n" +
      "  </testcase>\n" +
      "</testsuite>" +
      "<testsuite errors=\"0\" failures=\"0\" hostname=\"ruspd-student3\" name=\"TestCase1\" tests=\"1\" time=\"0.031\"\n" +
      "           timestamp=\"2008-10-30T17:11:25\">\n" +
      "  <properties/>\n" +
      "  <testcase classname=\"TestCase1\" name=\"test\" time=\"0.031\"/>\n" +
      "</testsuite>" +
      "<testsuite errors=\"0\" failures=\"0\" hostname=\"ruspd-student3\" name=\"TestCase3\" tests=\"2\" time=\"0.062\"\n" +
      "           timestamp=\"2008-10-30T17:11:25\">\n" +
      "  <properties/>\n" +
      "  <testcase classname=\"TestCase3\" name=\"test1\" time=\"0.031\"/>\n" +
      "  <testcase classname=\"TestCase3\" name=\"test2\" time=\"0.031\">\n" +
      "    <failure message=\"Assertion message form test\" type=\"junit.framework.AssertionFailedError\">\n" +
      "      junit.framework.AssertionFailedError: Assertion message form test\n" +
      "      at TestCase.test(Unknown Source)\n" +
      "    </failure>\n" +
      "  </testcase>\n" +
      "</testsuite>" +
      "</testsuites>");
    myEventDispatcher.getMulticaster().beforeBuildFinish(BuildFinishedStatus.FINISHED_SUCCESS);
    myContext.assertIsSatisfied();
    myTestLogger.checkIfAllExpectedMethodsWereInvoked();

    if (myFailures.size() > 0) {
      throw myFailures.get(0);
    }
  }

  //test for Gradle bug after fixing
  @Test
  public void testLogSuiteWhenAppearsIn2FilesOthersMustBeLoggedInTwoTries() {
    XmlReportPluginUtil.enableXmlReportParsing(myRunnerParams, ANT_JUNIT_REPORT_TYPE);
    XmlReportPluginUtil.setVerboseOutput(myRunnerParams, true);
    XmlReportPluginUtil.setXmlReportDirs(myRunnerParams, "");

    final List<Object> params = new ArrayList<Object>();
    params.add(MethodInvokation.ANY_VALUE);

    final List<Object> param = new ArrayList<Object>();
    param.add("TestCase1");

    myLogSequence.add(new MethodInvokation("logSuiteStarted", param));
    myLogSequence.add(new MethodInvokation("logTestStarted", params));
    myLogSequence.add(new MethodInvokation("logTestFinished", params));
    myLogSequence.add(new MethodInvokation("logSuiteFinished", param));

    param.remove("TestCase1");
    param.add("TestCase2");

    myLogSequence.add(new MethodInvokation("logSuiteStarted", param));
    myLogSequence.add(new MethodInvokation("logTestStarted", params));
    myLogSequence.add(new MethodInvokation("logTestFinished", params));
    myLogSequence.add(new MethodInvokation("logTestStarted", params));
    myLogSequence.add(new MethodInvokation("logTestFailed", params));
    myLogSequence.add(new MethodInvokation("logTestFinished", params));
    myLogSequence.add(new MethodInvokation("logSuiteFinished", param));

    param.remove("TestCase2");
    param.add("TestCase3");

    myLogSequence.add(new MethodInvokation("logSuiteStarted", param));
    myLogSequence.add(new MethodInvokation("logTestStarted", params));
    myLogSequence.add(new MethodInvokation("logTestFinished", params));
    myLogSequence.add(new MethodInvokation("logTestStarted", params));
    myLogSequence.add(new MethodInvokation("logTestFailed", params));
    myLogSequence.add(new MethodInvokation("logTestFinished", params));
    myLogSequence.add(new MethodInvokation("logSuiteFinished", param));

    myTestLogger.setExpectedSequence(myLogSequence);
    myTestLogger.addNotControlledMethod("message");
    myTestLogger.addNotControlledMethod("warning");

    myEventDispatcher.getMulticaster().buildStarted(myRunningBuild);
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    createFile("suite1", "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n" +
      "<testsuite errors=\"0\" failures=\"0\" hostname=\"ruspd-student3\" name=\"TestCase1\" tests=\"1\" time=\"0.031\"\n" +
      "           timestamp=\"2008-10-30T17:11:25\">\n" +
      "  <properties/>\n" +
      "  <testcase classname=\"TestCase\" name=\"test\" time=\"0.031\"/>\n" +
      "</testsuite>");
    myEventDispatcher.getMulticaster().beforeRunnerStart(myRunningBuild);
    createFile("suite2", "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n" +
      "<testsuites>" +
      "<testsuite errors=\"0\" failures=\"0\" hostname=\"ruspd-student3\" name=\"TestCase2\" tests=\"2\" time=\"0.062\"\n" +
      "           timestamp=\"2008-10-30T17:11:25\">\n" +
      "  <properties/>\n" +
      "  <testcase classname=\"TestCase2\" name=\"test1\" time=\"0.031\"/>\n" +
      "  <testcase classname=\"TestCase2\" name=\"test2\" time=\"0.031\">\n" +
      "    <failure message=\"Assertion message form test\" type=\"junit.framework.AssertionFailedError\">\n" +
      "      junit.framework.AssertionFailedError: Assertion message form test\n" +
      "      at TestCase.test(Unknown Source)\n" +
      "    </failure>\n" +
      "  </testcase>\n" +
      "</testsuite>" +
      "<testsuite errors=\"0\" failures=\"0\" hostname=\"ruspd-student3\" name=\"TestCase1\" tests=\"1\" time=\"0.031\"\n" +
      "           timestamp=\"2008-10-30T17:11:25\">\n" +
      "  <properties/>\n" +
      "  <testcase ");

    createFile("suite2", "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n" +
      "<testsuites>" +
      "<testsuite errors=\"0\" failures=\"0\" hostname=\"ruspd-student3\" name=\"TestCase2\" tests=\"2\" time=\"0.062\"\n" +
      "           timestamp=\"2008-10-30T17:11:25\">\n" +
      "  <properties/>\n" +
      "  <testcase classname=\"TestCase2\" name=\"test1\" time=\"0.031\"/>\n" +
      "  <testcase classname=\"TestCase2\" name=\"test2\" time=\"0.031\">\n" +
      "    <failure message=\"Assertion message form test\" type=\"junit.framework.AssertionFailedError\">\n" +
      "      junit.framework.AssertionFailedError: Assertion message form test\n" +
      "      at TestCase.test(Unknown Source)\n" +
      "    </failure>\n" +
      "  </testcase>\n" +
      "</testsuite>" +
      "<testsuite errors=\"0\" failures=\"0\" hostname=\"ruspd-student3\" name=\"TestCase1\" tests=\"1\" time=\"0.031\"\n" +
      "           timestamp=\"2008-10-30T17:11:25\">\n" +
      "  <properties/>\n" +
      "  <testcase classname=\"TestCase1\" name=\"test\" time=\"0.031\"/>\n" +
      "</testsuite>" +
      "<testsuite errors=\"0\" failures=\"0\" hostname=\"ruspd-student3\" name=\"TestCase3\" tests=\"2\" time=\"0.062\"\n" +
      "           timestamp=\"2008-10-30T17:11:25\">\n" +
      "  <properties/>\n" +
      "  <testcase classname=\"TestCase3\" name=\"test1\" time=\"0.031\"/>\n" +
      "  <testcase classname=\"TestCase3\" name=\"test2\" time=\"0.031\">\n" +
      "    <failure message=\"Assertion message form test\" type=\"junit.framework.AssertionFailedError\">\n" +
      "      junit.framework.AssertionFailedError: Assertion message form test\n" +
      "      at TestCase.test(Unknown Source)\n" +
      "    </failure>\n" +
      "  </testcase>\n" +
      "</testsuite>" +
      "</testsuites>");

    myEventDispatcher.getMulticaster().beforeBuildFinish(BuildFinishedStatus.FINISHED_SUCCESS);
    myEventDispatcher.getMulticaster().buildFinished(BuildFinishedStatus.FINISHED_SUCCESS);
    myContext.assertIsSatisfied();
    myTestLogger.checkIfAllExpectedMethodsWereInvoked();

    if (myFailures.size() > 0) {
      throw myFailures.get(0);
    }
  }

  @Test
  public void testSkipsOldFiles() {
    XmlReportPluginUtil.enableXmlReportParsing(myRunnerParams, ANT_JUNIT_REPORT_TYPE);
    XmlReportPluginUtil.setVerboseOutput(myRunnerParams, true);
    XmlReportPluginUtil.setXmlReportDirs(myRunnerParams, "");
    XmlReportPluginUtil.setParseOutOfDateReports(myRunnerParams, false);

    final List<Object> params = new ArrayList<Object>();
    params.add(MethodInvokation.ANY_VALUE);
    myLogSequence.add(new MethodInvokation("message", params));
    myLogSequence.add(new MethodInvokation("warning", params));
    myTestLogger.setExpectedSequence(myLogSequence);

    createFile("suite1", "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n" +
      "<testsuite errors=\"0\" failures=\"0\" hostname=\"ruspd-student3\" name=\"TestCase1\" tests=\"1\" time=\"0.031\"\n" +
      "           timestamp=\"2008-10-30T17:11:25\">\n" +
      "  <properties/>\n" +
      "  <testcase classname=\"TestCase\" name=\"test\" time=\"0.031\"/>\n" +
      "</testsuite>");

    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    myEventDispatcher.getMulticaster().buildStarted(myRunningBuild);
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    myEventDispatcher.getMulticaster().beforeRunnerStart(myRunningBuild);
    myEventDispatcher.getMulticaster().beforeBuildFinish(BuildFinishedStatus.FINISHED_SUCCESS);
    myEventDispatcher.getMulticaster().buildFinished(BuildFinishedStatus.FINISHED_SUCCESS);

    myContext.assertIsSatisfied();
    myTestLogger.checkIfAllExpectedMethodsWereInvoked();

    if (myFailures.size() > 0) {
      throw myFailures.get(0);
    }
  }

  @Test
  public void testNotSkipsOldFiles() {
    XmlReportPluginUtil.enableXmlReportParsing(myRunnerParams, ANT_JUNIT_REPORT_TYPE);
    XmlReportPluginUtil.setVerboseOutput(myRunnerParams, true);
    XmlReportPluginUtil.setXmlReportDirs(myRunnerParams, "");
    XmlReportPluginUtil.setParseOutOfDateReports(myRunnerParams, true);

    final List<Object> params = new ArrayList<Object>();
    params.add(MethodInvokation.ANY_VALUE);
    myLogSequence.add(new MethodInvokation("message", params));

    final List<Object> param = new ArrayList<Object>();
    param.add(MethodInvokation.ANY_VALUE);
    param.add("TestCase1");

    myLogSequence.add(new MethodInvokation("logSuiteStarted", param));
    myLogSequence.add(new MethodInvokation("logTestStarted", params));
    myLogSequence.add(new MethodInvokation("logTestFinished", params));
    myLogSequence.add(new MethodInvokation("logSuiteFinished", param));
    myLogSequence.add(new MethodInvokation("message", params));
    myLogSequence.add(new MethodInvokation("message", params));
    myTestLogger.setExpectedSequence(myLogSequence);

    createFile("suite1", "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n" +
      "<testsuite errors=\"0\" failures=\"0\" hostname=\"ruspd-student3\" name=\"TestCase1\" tests=\"1\" time=\"0.031\"\n" +
      "           timestamp=\"2008-10-30T17:11:25\">\n" +
      "  <properties/>\n" +
      "  <testcase classname=\"TestCase\" name=\"test\" time=\"0.031\"/>\n" +
      "</testsuite>");

    myEventDispatcher.getMulticaster().buildStarted(myRunningBuild);
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    myEventDispatcher.getMulticaster().beforeRunnerStart(myRunningBuild);
    myEventDispatcher.getMulticaster().beforeBuildFinish(BuildFinishedStatus.FINISHED_SUCCESS);
    myEventDispatcher.getMulticaster().buildFinished(BuildFinishedStatus.FINISHED_SUCCESS);

    myContext.assertIsSatisfied();
    myTestLogger.checkIfAllExpectedMethodsWereInvoked();

    if (myFailures.size() > 0) {
      throw myFailures.get(0);
    }
  }

  @Test
  public void testParsingFromServiceMessage() {
    XmlReportPluginUtil.enableXmlReportParsing(myRunnerParams, "");

    final List<Object> params = new ArrayList<Object>();
    params.add(MethodInvokation.ANY_VALUE);
    myLogSequence.add(new MethodInvokation("message", params));

    final List<Object> param = new ArrayList<Object>();
    param.add(MethodInvokation.ANY_VALUE);
    param.add("TestCase1");

    myLogSequence.add(new MethodInvokation("logSuiteStarted", param));
    myLogSequence.add(new MethodInvokation("logTestStarted", params));
    myLogSequence.add(new MethodInvokation("logTestFinished", params));
    myLogSequence.add(new MethodInvokation("logSuiteFinished", param));
    myLogSequence.add(new MethodInvokation("message", params));
    myLogSequence.add(new MethodInvokation("message", params));
    myTestLogger.setExpectedSequence(myLogSequence);

    myEventDispatcher.getMulticaster().buildStarted(myRunningBuild);
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    myEventDispatcher.getMulticaster().beforeRunnerStart(myRunningBuild);

    createFile("suite1", "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n" +
      "<testsuite errors=\"0\" failures=\"0\" hostname=\"ruspd-student3\" name=\"TestCase1\" tests=\"1\" time=\"0.031\"\n" +
      "           timestamp=\"2008-10-30T17:11:25\">\n" +
      "  <properties/>\n" +
      "  <testcase classname=\"TestCase\" name=\"test\" time=\"0.031\"/>\n" +
      "</testsuite>");

    final XmlReportDataProcessor dataProcessor = new XmlReportDataProcessor.JUnitDataProcessor(myPlugin);
    final Map<String, String> args = new HashMap<String, String>();
    args.put(XmlReportDataProcessor.VERBOSE_ARGUMENT, "true");
    dataProcessor.processData(new File(WORKING_DIR), args);

    myEventDispatcher.getMulticaster().beforeBuildFinish(BuildFinishedStatus.FINISHED_SUCCESS);
    myEventDispatcher.getMulticaster().buildFinished(BuildFinishedStatus.FINISHED_SUCCESS);

    myContext.assertIsSatisfied();
    myTestLogger.checkIfAllExpectedMethodsWereInvoked();

    if (myFailures.size() > 0) {
      throw myFailures.get(0);
    }
  }

  @Test
  public void testParsingFromServiceMessageSkipOld() {
    XmlReportPluginUtil.enableXmlReportParsing(myRunnerParams, "");

    final List<Object> params = new ArrayList<Object>();
    params.add(MethodInvokation.ANY_VALUE);
    myLogSequence.add(new MethodInvokation("message", params));
    myLogSequence.add(new MethodInvokation("warning", params));
    myTestLogger.setExpectedSequence(myLogSequence);

    createFile("suite1", "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n" +
      "<testsuite errors=\"0\" failures=\"0\" hostname=\"ruspd-student3\" name=\"TestCase1\" tests=\"1\" time=\"0.031\"\n" +
      "           timestamp=\"2008-10-30T17:11:25\">\n" +
      "  <properties/>\n" +
      "  <testcase classname=\"TestCase\" name=\"test\" time=\"0.031\"/>\n" +
      "</testsuite>");
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    myEventDispatcher.getMulticaster().buildStarted(myRunningBuild);
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    myEventDispatcher.getMulticaster().beforeRunnerStart(myRunningBuild);

    final XmlReportDataProcessor dataProcessor = new XmlReportDataProcessor.JUnitDataProcessor(myPlugin);
    final Map<String, String> args = new HashMap<String, String>();
    args.put(XmlReportDataProcessor.VERBOSE_ARGUMENT, "true");
    dataProcessor.processData(new File(WORKING_DIR), args);

    myEventDispatcher.getMulticaster().beforeBuildFinish(BuildFinishedStatus.FINISHED_SUCCESS);
    myEventDispatcher.getMulticaster().buildFinished(BuildFinishedStatus.FINISHED_SUCCESS);

    myContext.assertIsSatisfied();
    myTestLogger.checkIfAllExpectedMethodsWereInvoked();

    if (myFailures.size() > 0) {
      throw myFailures.get(0);
    }
  }

  @Test
  public void testParsingFromServiceMessageNotSkipOld() {
    XmlReportPluginUtil.enableXmlReportParsing(myRunnerParams, "");

    final List<Object> params = new ArrayList<Object>();
    params.add(MethodInvokation.ANY_VALUE);
    myLogSequence.add(new MethodInvokation("message", params));

    final List<Object> param = new ArrayList<Object>();
    param.add(MethodInvokation.ANY_VALUE);
    param.add("TestCase1");

    myLogSequence.add(new MethodInvokation("logSuiteStarted", param));
    myLogSequence.add(new MethodInvokation("logTestStarted", params));
    myLogSequence.add(new MethodInvokation("logTestFinished", params));
    myLogSequence.add(new MethodInvokation("logSuiteFinished", param));
    myLogSequence.add(new MethodInvokation("message", params));
    myLogSequence.add(new MethodInvokation("message", params));
    myTestLogger.setExpectedSequence(myLogSequence);

    createFile("suite1", "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n" +
      "<testsuite errors=\"0\" failures=\"0\" hostname=\"ruspd-student3\" name=\"TestCase1\" tests=\"1\" time=\"0.031\"\n" +
      "           timestamp=\"2008-10-30T17:11:25\">\n" +
      "  <properties/>\n" +
      "  <testcase classname=\"TestCase\" name=\"test\" time=\"0.031\"/>\n" +
      "</testsuite>");
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    myEventDispatcher.getMulticaster().buildStarted(myRunningBuild);
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    myEventDispatcher.getMulticaster().beforeRunnerStart(myRunningBuild);

    final XmlReportDataProcessor dataProcessor = new XmlReportDataProcessor.JUnitDataProcessor(myPlugin);
    final Map<String, String> args = new HashMap<String, String>();
    args.put(XmlReportDataProcessor.VERBOSE_ARGUMENT, "true");
    args.put(XmlReportDataProcessor.PARSE_OUT_OF_DATE_ARGUMENT, "true");
    dataProcessor.processData(new File(WORKING_DIR), args);

    myEventDispatcher.getMulticaster().beforeBuildFinish(BuildFinishedStatus.FINISHED_SUCCESS);
    myEventDispatcher.getMulticaster().buildFinished(BuildFinishedStatus.FINISHED_SUCCESS);

    myContext.assertIsSatisfied();
    myTestLogger.checkIfAllExpectedMethodsWereInvoked();

    if (myFailures.size() > 0) {
      throw myFailures.get(0);
    }
  }
}