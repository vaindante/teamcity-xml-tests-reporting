package jetbrains.buildServer.testReportParserPlugin;

import jetbrains.buildServer.agent.BaseServerLoggerFacade;
import jetbrains.buildServer.testReportParserPlugin.antJUnit.AntJUnitReportParser;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.Sequence;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.Date;


@RunWith(JMock.class)
public class AntJUnitReportParserTest {
    private static final String REPORT_DIR = "TestReportParserPluginTests\\\\src\\\\jetbrains\\\\buildServer\\\\testReportParserPlugin\\\\reports\\\\";
    private static final String SUITE_NAME = "TestCase";
    private static final String CASE_CLASSNAME = "TestCase";
    private static final String CASE_NAME = CASE_CLASSNAME + ".test";

    private static final String FAILURE_MESSAGE = "junit.framework.AssertionFailedError: Assertion message form test";
    private static final String FAILURE_STACK_TRACE = "junit.framework.AssertionFailedError: Assertion message form test\n\tat TestCase.test(Unknown Source)";
    private static final String ERROR_MESSAGE = "java.lang.NullPointerException: Error message from test";
    private static final String ERROR_STACK_TRACE = "java.lang.NullPointerException: Error message from test\n\tat TestCase.test(Unknown Source)";

    private AntJUnitReportParser myParser;
    private BaseServerLoggerFacade myLogger;

    private Mockery myContext;
    private Sequence mySequence;


    private BaseServerLoggerFacade createBaseServerLoggerFacade() {
        return myContext.mock(BaseServerLoggerFacade.class);
    }

    private static File report(String name) {
        return new File(REPORT_DIR + name);
    }

    @Before
    public void setUp() {
        myContext = new JUnit4Mockery() {
            {
                setImposteriser(ClassImposteriser.INSTANCE);
            }
        };
        myLogger = createBaseServerLoggerFacade();
        myParser = new AntJUnitReportParser(myLogger);
        mySequence = myContext.sequence("Log Sequence");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFirstNullArgument() {
        myParser = new AntJUnitReportParser(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullReport() {
        myParser.parse(null, 0);
    }

    @Test
    public void testUnexistingReport() {
        myContext.checking(new Expectations() {
            {
                oneOf(myLogger).warning(with(any(String.class)));
            }
        });

        myParser.parse(new File("unexisting"), 0);
        myContext.assertIsSatisfied();
    }

    @Test
    public void testNoCases() {
        myContext.checking(new Expectations() {
            {
                oneOf(myLogger).logSuiteStarted(with(SUITE_NAME), with(any(Date.class)));
                inSequence(mySequence);
                oneOf(myLogger).logSuiteFinished(with(SUITE_NAME), with(any(Date.class)));
                inSequence(mySequence);
            }
        });
        myParser.parse(report("noCase.xml"), 0);
        myContext.assertIsSatisfied();
    }

    @Test
    public void testSingleCaseSuccess() {
        myContext.checking(new Expectations() {
            {
                oneOf(myLogger).logSuiteStarted(with(SUITE_NAME), with(any(Date.class)));
                inSequence(mySequence);
                oneOf(myLogger).logTestStarted(with(CASE_NAME), with(any(Date.class)));
                inSequence(mySequence);
                oneOf(myLogger).logTestFinished(with(CASE_NAME), with(any(Date.class)));
                inSequence(mySequence);
                oneOf(myLogger).logSuiteFinished(with(SUITE_NAME), with(any(Date.class)));
                inSequence(mySequence);
            }
        });
        myParser.parse(report("singleCaseSuccess.xml"), 0);
        myContext.assertIsSatisfied();
    }

    @Test
    public void testSingleCaseFailure() {
        myContext.checking(new Expectations() {
            {
                oneOf(myLogger).logSuiteStarted(with(SUITE_NAME), with(any(Date.class)));
                inSequence(mySequence);
                oneOf(myLogger).logTestStarted(with(CASE_NAME), with(any(Date.class)));
                inSequence(mySequence);
                oneOf(myLogger).logTestFailed(with(CASE_NAME), with(FAILURE_MESSAGE), with(FAILURE_STACK_TRACE));
                inSequence(mySequence);
                oneOf(myLogger).logTestFinished(with(CASE_NAME), with(any(Date.class)));
                inSequence(mySequence);
                oneOf(myLogger).logSuiteFinished(with(SUITE_NAME), with(any(Date.class)));
                inSequence(mySequence);
            }
        });
        myParser.parse(report("singleCaseFailure.xml"), 0);
        myContext.assertIsSatisfied();
    }

    @Test
    public void testSingleCaseError() {
        myContext.checking(new Expectations() {
            {
                oneOf(myLogger).logSuiteStarted(with(SUITE_NAME), with(any(Date.class)));
                inSequence(mySequence);
                oneOf(myLogger).logTestStarted(with(CASE_NAME), with(any(Date.class)));
                inSequence(mySequence);
                oneOf(myLogger).logTestFailed(with(CASE_NAME), with(ERROR_MESSAGE), with(ERROR_STACK_TRACE));
                inSequence(mySequence);
                oneOf(myLogger).logTestFinished(with(CASE_NAME), with(any(Date.class)));
                inSequence(mySequence);
                oneOf(myLogger).logSuiteFinished(with(SUITE_NAME), with(any(Date.class)));
                inSequence(mySequence);
            }
        });
        myParser.parse(report("singleCaseError.xml"), 0);
        myContext.assertIsSatisfied();
    }

    @Test
    public void test2CasesSuccess() {
        myContext.checking(new Expectations() {
            {
                oneOf(myLogger).logSuiteStarted(with(SUITE_NAME), with(any(Date.class)));
                inSequence(mySequence);
                oneOf(myLogger).logTestStarted(with(CASE_NAME + "1"), with(any(Date.class)));
                inSequence(mySequence);
                oneOf(myLogger).logTestFinished(with(CASE_NAME + "1"), with(any(Date.class)));
                inSequence(mySequence);
                oneOf(myLogger).logTestStarted(with(CASE_NAME + "2"), with(any(Date.class)));
                inSequence(mySequence);
                oneOf(myLogger).logTestFinished(with(CASE_NAME + "2"), with(any(Date.class)));
                inSequence(mySequence);
                oneOf(myLogger).logSuiteFinished(with(SUITE_NAME), with(any(Date.class)));
                inSequence(mySequence);
            }
        });
        myParser.parse(report("twoCasesSuccess.xml"), 0);
        myContext.assertIsSatisfied();
    }

    @Test
    public void test2CasesFirstSuccess() {
        myContext.checking(new Expectations() {
            {
                oneOf(myLogger).logSuiteStarted(with(SUITE_NAME), with(any(Date.class)));
                inSequence(mySequence);
                oneOf(myLogger).logTestStarted(with(CASE_NAME + "1"), with(any(Date.class)));
                inSequence(mySequence);
                oneOf(myLogger).logTestFinished(with(CASE_NAME + "1"), with(any(Date.class)));
                inSequence(mySequence);
                oneOf(myLogger).logTestStarted(with(CASE_NAME + "2"), with(any(Date.class)));
                inSequence(mySequence);
                oneOf(myLogger).logTestFailed(with(CASE_NAME + "2"), with(FAILURE_MESSAGE), with(FAILURE_STACK_TRACE));
                inSequence(mySequence);
                oneOf(myLogger).logTestFinished(with(CASE_NAME + "2"), with(any(Date.class)));
                inSequence(mySequence);
                oneOf(myLogger).logSuiteFinished(with(SUITE_NAME), with(any(Date.class)));
                inSequence(mySequence);
            }
        });
        myParser.parse(report("twoCasesFirstSuccess.xml"), 0);
        myContext.assertIsSatisfied();
    }

    @Test
    public void test2CasesSecondSuccess() {
        myContext.checking(new Expectations() {
            {
                oneOf(myLogger).logSuiteStarted(with(SUITE_NAME), with(any(Date.class)));
                inSequence(mySequence);
                oneOf(myLogger).logTestStarted(with(CASE_NAME + "1"), with(any(Date.class)));
                inSequence(mySequence);
                oneOf(myLogger).logTestFailed(with(CASE_NAME + "1"), with(FAILURE_MESSAGE), with(FAILURE_STACK_TRACE));
                inSequence(mySequence);
                oneOf(myLogger).logTestFinished(with(CASE_NAME + "1"), with(any(Date.class)));
                inSequence(mySequence);
                oneOf(myLogger).logTestStarted(with(CASE_NAME + "2"), with(any(Date.class)));
                inSequence(mySequence);
                oneOf(myLogger).logTestFinished(with(CASE_NAME + "2"), with(any(Date.class)));
                inSequence(mySequence);
                oneOf(myLogger).logSuiteFinished(with(SUITE_NAME), with(any(Date.class)));
                inSequence(mySequence);
            }
        });
        myParser.parse(report("twoCasesSecondSuccess.xml"), 0);
        myContext.assertIsSatisfied();
    }

    @Test
    public void test2CasesFailed() {
        myContext.checking(new Expectations() {
            {
                oneOf(myLogger).logSuiteStarted(with(SUITE_NAME), with(any(Date.class)));
                inSequence(mySequence);
                oneOf(myLogger).logTestStarted(with(CASE_NAME + "1"), with(any(Date.class)));
                inSequence(mySequence);
                oneOf(myLogger).logTestFailed(with(CASE_NAME + "1"), with(FAILURE_MESSAGE), with(FAILURE_STACK_TRACE));
                inSequence(mySequence);
                oneOf(myLogger).logTestFinished(with(CASE_NAME + "1"), with(any(Date.class)));
                inSequence(mySequence);
                oneOf(myLogger).logTestStarted(with(CASE_NAME + "2"), with(any(Date.class)));
                inSequence(mySequence);
                oneOf(myLogger).logTestFailed(with(CASE_NAME + "2"), with(ERROR_MESSAGE), with(ERROR_STACK_TRACE));
                inSequence(mySequence);
                oneOf(myLogger).logTestFinished(with(CASE_NAME + "2"), with(any(Date.class)));
                inSequence(mySequence);
                oneOf(myLogger).logSuiteFinished(with(SUITE_NAME), with(any(Date.class)));
                inSequence(mySequence);
            }
        });
        myParser.parse(report("twoCasesFailed.xml"), 0);
        myContext.assertIsSatisfied();
    }

    private void twoCasesIn2PartsBothFrom2TrySuiteFrom1(String unfinishedReportName) {
        myContext.checking(new Expectations() {
            {
                oneOf(myLogger).logSuiteStarted(with(SUITE_NAME), with(any(Date.class)));
                inSequence(mySequence);
            }
        });
        long testsLogged = myParser.parse(report(unfinishedReportName), 0);
        myContext.checking(new Expectations() {
            {
                oneOf(myLogger).logTestStarted(with(CASE_NAME + "1"), with(any(Date.class)));
                inSequence(mySequence);
                oneOf(myLogger).logTestFailed(with(CASE_NAME + "1"), with(FAILURE_MESSAGE), with(FAILURE_STACK_TRACE));
                inSequence(mySequence);
                oneOf(myLogger).logTestFinished(with(CASE_NAME + "1"), with(any(Date.class)));
                inSequence(mySequence);
                oneOf(myLogger).logTestStarted(with(CASE_NAME + "2"), with(any(Date.class)));
                inSequence(mySequence);
                oneOf(myLogger).logTestFailed(with(CASE_NAME + "2"), with(ERROR_MESSAGE), with(ERROR_STACK_TRACE));
                inSequence(mySequence);
                oneOf(myLogger).logTestFinished(with(CASE_NAME + "2"), with(any(Date.class)));
                inSequence(mySequence);
                oneOf(myLogger).logSuiteFinished(with(SUITE_NAME), with(any(Date.class)));
                inSequence(mySequence);
            }
        });
        myParser.parse(report("twoCasesFailed.xml"), testsLogged);
        myContext.assertIsSatisfied();
    }

    @Test
    public void test2CasesIn2PartsFirstBreakBetweenAttrs() {
        twoCasesIn2PartsBothFrom2TrySuiteFrom1("twoCasesFirstBreakBetweenAttrs.xml");
    }

    @Test
    public void test2CasesIn2PartsFirstBreakFailureST() {
        twoCasesIn2PartsBothFrom2TrySuiteFrom1("twoCasesFirstBreakFailureST.xml");
    }

    @Test
    public void test2CasesIn2PartsFirstBreakAfterFailure() {
        twoCasesIn2PartsBothFrom2TrySuiteFrom1("twoCasesFirstBreakAfterFailure.xml");
    }

    private void twoCasesIn2PartsSecondFrom2Try(String unfinishedReportName) {
        myContext.checking(new Expectations() {
            {
                oneOf(myLogger).logSuiteStarted(with(SUITE_NAME), with(any(Date.class)));
                inSequence(mySequence);
                oneOf(myLogger).logTestStarted(with(CASE_NAME + "1"), with(any(Date.class)));
                inSequence(mySequence);
                oneOf(myLogger).logTestFailed(with(CASE_NAME + "1"), with(FAILURE_MESSAGE), with(FAILURE_STACK_TRACE));
                inSequence(mySequence);
                oneOf(myLogger).logTestFinished(with(CASE_NAME + "1"), with(any(Date.class)));
                inSequence(mySequence);
            }
        });
        long testsLogged = myParser.parse(report(unfinishedReportName), 0);
        myContext.checking(new Expectations() {
            {
                oneOf(myLogger).logTestStarted(with(CASE_NAME + "2"), with(any(Date.class)));
                inSequence(mySequence);
                oneOf(myLogger).logTestFailed(with(CASE_NAME + "2"), with(ERROR_MESSAGE), with(ERROR_STACK_TRACE));
                inSequence(mySequence);
                oneOf(myLogger).logTestFinished(with(CASE_NAME + "2"), with(any(Date.class)));
                inSequence(mySequence);
                oneOf(myLogger).logSuiteFinished(with(SUITE_NAME), with(any(Date.class)));
                inSequence(mySequence);
            }
        });
        myParser.parse(report("twoCasesFailed.xml"), testsLogged);
        myContext.assertIsSatisfied();
    }

    @Test
    public void test2CasesIn2PartsBreakAfterFirst() {
        twoCasesIn2PartsSecondFrom2Try("twoCasesBreakAfterFirst.xml");
    }

    @Test
    public void test2CasesIn2PartsSecondBreakBetweenAttrs() {
        twoCasesIn2PartsSecondFrom2Try("twoCasesSecondBreakBetweenAttrs.xml");
    }

    @Test
    public void test2CasesIn2PartsSecondBreakFailureMessage() {
        twoCasesIn2PartsSecondFrom2Try("twoCasesSecondBreakErrorMessage.xml");
    }

    @Test
    public void test2CasesIn2PartsSecondBreakFailureST() {
        twoCasesIn2PartsSecondFrom2Try("twoCasesSecondBreakErrorST.xml");
    }

    @Test
    public void test2CasesIn2PartsSecondBreakErrorClosing() {
        twoCasesIn2PartsSecondFrom2Try("twoCasesSecondBreakErrorClosing.xml");
    }

    @Test
    public void test2CasesIn2PartsSecondBreakClosing() {
        twoCasesIn2PartsSecondFrom2Try("twoCasesSecondBreakClosing.xml");
    }

    @Test
    public void test2CasesIn2PartsBothFrom1TrySuiteFrom2() {
        myContext.checking(new Expectations() {
            {
                oneOf(myLogger).logSuiteStarted(with(SUITE_NAME), with(any(Date.class)));
                inSequence(mySequence);
                oneOf(myLogger).logTestStarted(with(CASE_NAME + "1"), with(any(Date.class)));
                inSequence(mySequence);
                oneOf(myLogger).logTestFailed(with(CASE_NAME + "1"), with(FAILURE_MESSAGE), with(FAILURE_STACK_TRACE));
                inSequence(mySequence);
                oneOf(myLogger).logTestFinished(with(CASE_NAME + "1"), with(any(Date.class)));
                inSequence(mySequence);
                oneOf(myLogger).logTestStarted(with(CASE_NAME + "2"), with(any(Date.class)));
                inSequence(mySequence);
                oneOf(myLogger).logTestFailed(with(CASE_NAME + "2"), with(ERROR_MESSAGE), with(ERROR_STACK_TRACE));
                inSequence(mySequence);
                oneOf(myLogger).logTestFinished(with(CASE_NAME + "2"), with(any(Date.class)));
                inSequence(mySequence);
            }
        });
        long testsLogged = myParser.parse(report("twoCasesBreakAfterSecond.xml"), 0);
        myContext.checking(new Expectations() {
            {
                oneOf(myLogger).logSuiteFinished(with(SUITE_NAME), with(any(Date.class)));
                inSequence(mySequence);
            }
        });
        myParser.parse(report("twoCasesFailed.xml"), testsLogged);
        myContext.assertIsSatisfied();
    }

    private void twoCasesIn2PartsBothAndSuiteFrom2Try(String unfinishedReportName) {
        long testsLogged = myParser.parse(report(unfinishedReportName), 0);
        myContext.checking(new Expectations() {
            {
                oneOf(myLogger).logSuiteStarted(with(SUITE_NAME), with(any(Date.class)));
                inSequence(mySequence);
                oneOf(myLogger).logTestStarted(with(CASE_NAME + "1"), with(any(Date.class)));
                inSequence(mySequence);
                oneOf(myLogger).logTestFailed(with(CASE_NAME + "1"), with(FAILURE_MESSAGE), with(FAILURE_STACK_TRACE));
                inSequence(mySequence);
                oneOf(myLogger).logTestFinished(with(CASE_NAME + "1"), with(any(Date.class)));
                inSequence(mySequence);
                oneOf(myLogger).logTestStarted(with(CASE_NAME + "2"), with(any(Date.class)));
                inSequence(mySequence);
                oneOf(myLogger).logTestFailed(with(CASE_NAME + "2"), with(ERROR_MESSAGE), with(ERROR_STACK_TRACE));
                inSequence(mySequence);
                oneOf(myLogger).logTestFinished(with(CASE_NAME + "2"), with(any(Date.class)));
                inSequence(mySequence);
                oneOf(myLogger).logSuiteFinished(with(SUITE_NAME), with(any(Date.class)));
                inSequence(mySequence);
            }
        });
        myParser.parse(report("twoCasesFailed.xml"), testsLogged);
        myContext.assertIsSatisfied();
    }

    @Test
    public void test2CasesIn2PartsBreakHeading() {
        twoCasesIn2PartsBothAndSuiteFrom2Try("twoCasesBreakHeading.xml");
    }

    @Test
    public void test2CasesIn2PartsBreakTestSuite() {
        twoCasesIn2PartsBothAndSuiteFrom2Try("twoCasesBreakTestSuite.xml");
    }

    @Test
    public void test2CasesIn2PartsBreakTestSuiteInAttr() {
        twoCasesIn2PartsBothAndSuiteFrom2Try("twoCasesBreakTestSuiteInAttr.xml");
    }
//    private void twoCasesIn2PartsBothAndSuiteFrom2Try(String unfinishedReportName) {
//        long testsLogged = myParser.parse(report(unfinishedReportName), 0);
//        myContext.checking(new Expectations() {
//            {
//                oneOf(myLogger).logSuiteStarted(with(SUITE_NAME), with(any(Date.class))); inSequence(mySequence);
//                oneOf(myLogger).logTestStarted(with(CASE_NAME + "1"), with(any(Date.class))); inSequence(mySequence);
//                oneOf(myLogger).logTestFailed(with(CASE_NAME + "1"), with(FAILURE_MESSAGE), with(FAILURE_STACK_TRACE)); inSequence(mySequence);
//                oneOf(myLogger).logTestFinished(with(CASE_NAME + "1"), with(any(Date.class))); inSequence(mySequence);
//                oneOf(myLogger).logTestStarted(with(CASE_NAME + "2"), with(any(Date.class))); inSequence(mySequence);
//                oneOf(myLogger).logTestFailed(with(CASE_NAME + "2"), with(ERROR_MESSAGE), with(ERROR_STACK_TRACE)); inSequence(mySequence);
//                oneOf(myLogger).logTestFinished(with(CASE_NAME + "2"), with(any(Date.class))); inSequence(mySequence);
//                oneOf(myLogger).logSuiteFinished(with(SUITE_NAME), with(any(Date.class))); inSequence(mySequence);
//            }
//        });
//        myParser.parse(report("twoCasesFailed.xml"), testsLogged);
//        myContext.assertIsSatisfied();
//    }

////    @Test
////    public void test2CasesSuccessSecond() {
//        myContext.checking(new Expectations() {
//            {
//                oneOf(myLogger).logSuiteStarted(with(SUITE_NAME), with(any(Date.class))); inSequence(mySequence);
//                oneOf(myLogger).logTestStarted(with(CASE_NAME + "1"), with(any(Date.class))); inSequence(mySequence);
//                oneOf(myLogger).logTestFinished(with(CASE_NAME + "1"), with(any(Date.class))); inSequence(mySequence);
//                oneOf(myLogger).logTestStarted(with(CASE_NAME + "2"), with(any(Date.class))); inSequence(mySequence);
//                oneOf(myLogger).logTestFinished(with(CASE_NAME + "2"), with(any(Date.class))); inSequence(mySequence);
//                oneOf(myLogger).logSuiteFinished(with(SUITE_NAME), with(any(Date.class))); inSequence(mySequence);
//            }
//        });
//        myParser.parse(report("report3.xml"), 0);
//        myContext.assertIsSatisfied();
//    }
}