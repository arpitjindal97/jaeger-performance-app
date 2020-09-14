package com.sap;

import org.apache.jmeter.assertions.JSONPathAssertion;
import org.apache.jmeter.assertions.gui.JSONPathAssertionGui;
import org.apache.jmeter.config.Argument;
import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.config.gui.ArgumentsPanel;
import org.apache.jmeter.control.LoopController;
import org.apache.jmeter.control.WhileController;
import org.apache.jmeter.control.gui.LoopControlPanel;
import org.apache.jmeter.control.gui.WhileControllerGui;
import org.apache.jmeter.extractor.json.jsonpath.JSONPostProcessor;
import org.apache.jmeter.control.gui.TestPlanGui;
import org.apache.jmeter.engine.StandardJMeterEngine;
import org.apache.jmeter.extractor.json.jsonpath.gui.JSONPostProcessorGui;
import org.apache.jmeter.modifiers.CounterConfig;
import org.apache.jmeter.modifiers.gui.CounterConfigGui;
import org.apache.jmeter.protocol.http.control.gui.HttpTestSampleGui;
import org.apache.jmeter.protocol.http.sampler.HTTPSamplerProxy;
import org.apache.jmeter.reporters.ResultCollector;
import org.apache.jmeter.reporters.Summariser;
import org.apache.jmeter.save.SaveService;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.testelement.TestPlan;
import org.apache.jmeter.threads.ThreadGroup;
import org.apache.jmeter.threads.gui.ThreadGroupGui;
import org.apache.jmeter.util.JMeterUtils;
import org.apache.jmeter.visualizers.ViewResultsFullVisualizer;
import org.apache.jorphan.collections.HashTree;
import org.apache.jorphan.collections.ListedHashTree;

import java.io.File;
import java.io.FileOutputStream;


public class App
{

    public static void main(String[] argv) throws Exception {

        String jmeterPropertiesPath = "lib/jmeter.properties";
        String jmeterSavePropPath = "lib/saveservice.properties";
        String jmeterHomePath = System.getProperty("user.dir");
        String csvResultPath = "result.csv";
        String jmxOutputPath = "testing.jmx";

        File jmeterProperties = new File(jmeterPropertiesPath);
        StandardJMeterEngine jmeter = new StandardJMeterEngine();

        JMeterUtils.setJMeterHome(jmeterHomePath);
        JMeterUtils.loadJMeterProperties(jmeterProperties.getPath());
        JMeterUtils.setProperty("saveservice_properties",jmeterSavePropPath);
        JMeterUtils.initLocale();


        //add Summarizer output to get test progress in stdout like:
        // summary =      2 in   1.3s =    1.5/s Avg:   631 Min:   290 Max:   973 Err:     0 (0.00%)
        Summariser summer = null;
        String summariserName = JMeterUtils.getPropDefault("summariser.name", "summary");
        if (summariserName.length() > 0) {
            summer = new Summariser(summariserName);
        }
        ResultCollector logger = new ResultCollector(summer);
        logger.setProperty(TestElement.TEST_CLASS, ResultCollector.class.getName());
        logger.setProperty(TestElement.GUI_CLASS, ViewResultsFullVisualizer.class.getName());
        logger.setName("View Results tree");
        logger.setFilename(csvResultPath);

        // Test Plan
        TestPlan testPlan = new TestPlan("Create JMeter Script From Java Code");
        testPlan.setProperty(TestElement.TEST_CLASS, TestPlan.class.getName());
        testPlan.setProperty(TestElement.GUI_CLASS, TestPlanGui.class.getName());
        testPlan.setUserDefinedVariables((Arguments) new ArgumentsPanel().createTestElement());


        ListedHashTree testPlanTree = new ListedHashTree();
        testPlanTree.add(testPlan);
        addTenantThreadGroup(testPlanTree,testPlan,"cust1","ingress.mon-team.perf-load.shoot.dev.k8s-hana.ondemand.com");
        addTenantThreadGroup(testPlanTree,testPlan,"cust2","ingress.mon-team.perf-load.shoot.dev.k8s-hana.ondemand.com");
        //testPlanTree.add(testPlan, logger);
        testPlanTree.get(testPlan).add(logger);

        // save generated test plan to JMeter's .jmx file format

        SaveService.saveTree(testPlanTree, new FileOutputStream(jmxOutputPath));

        // Run Test Plan
        jmeter.configure(testPlanTree);
        jmeter.run();

        System.exit(0);

    }

    public static void addTenantThreadGroup(ListedHashTree rootTree, TestPlan testPlan , String customer, String domain){

        // Thread Group
        ThreadGroup threadGroup = new ThreadGroup();
        threadGroup.setName(customer + " thread group");
        threadGroup.setNumThreads(100);
        threadGroup.setRampUp(1);
        threadGroup.setIsSameUserOnNextIteration(false);
        threadGroup.setProperty(TestElement.TEST_CLASS, ThreadGroup.class.getName());
        threadGroup.setProperty(TestElement.GUI_CLASS, ThreadGroupGui.class.getName());

        // Loop Controller
        LoopController loopController = new LoopController();
        loopController.setLoops(1);
        loopController.setFirst(true);
        loopController.setProperty(TestElement.TEST_CLASS, LoopController.class.getName());
        loopController.setProperty(TestElement.GUI_CLASS, LoopControlPanel.class.getName());
        loopController.initialize();

        threadGroup.setSamplerController(loopController);

        // First HTTP Sampler - open example.com
        HTTPSamplerProxy traceCreation = new HTTPSamplerProxy();
        traceCreation.setDomain(customer + "."+domain);
        traceCreation.setPort(443);
        traceCreation.setPath("hotrod/dispatch");
        traceCreation.setMethod("GET");
        traceCreation.setProtocol("https");
        traceCreation.setName(customer + " creating a Trace");
        traceCreation.setEnabled(true);
        traceCreation.setProperty(TestElement.TEST_CLASS, HTTPSamplerProxy.class.getName());
        traceCreation.setProperty(TestElement.GUI_CLASS, HttpTestSampleGui.class.getName());
        traceCreation.addArgument("customer","123");
        traceCreation.addArgument("nonse","0.7856101739739808");

        JSONPostProcessor jsonPostProcessor = new JSONPostProcessor();
        jsonPostProcessor.setRefNames("driver");
        jsonPostProcessor.setJsonPathExpressions("$.Driver");
        jsonPostProcessor.setMatchNumbers("1");
        jsonPostProcessor.setName("JSON Extractor");
        jsonPostProcessor.setEnabled(true);
        jsonPostProcessor.setProperty(TestElement.TEST_CLASS, JSONPostProcessor.class.getName());
        jsonPostProcessor.setProperty(TestElement.GUI_CLASS, JSONPostProcessorGui.class.getName());

        HTTPSamplerProxy queryTrace = new HTTPSamplerProxy();
        queryTrace.setDomain(customer + "."+domain);
        queryTrace.setPort(443);
        queryTrace.setProtocol("https");
        queryTrace.setPath("api/traces");
        queryTrace.setMethod("GET");
        queryTrace.setEnabled(true);
        queryTrace.setName(customer + " querying the trace");
        queryTrace.setProperty(TestElement.TEST_CLASS, HTTPSamplerProxy.class.getName());
        queryTrace.setProperty(TestElement.GUI_CLASS, HttpTestSampleGui.class.getName());
        queryTrace.addArgument("service","frontend","=");
        queryTrace.addArgument("tags","{\"driver\":\"${driver}\"}","=");

        JSONPostProcessor queryTraceJsonPostProcessor = new JSONPostProcessor();
        queryTraceJsonPostProcessor.setRefNames("data");
        queryTraceJsonPostProcessor.setJsonPathExpressions("$.data");
        queryTraceJsonPostProcessor.setMatchNumbers("1");
        queryTraceJsonPostProcessor.setName("query Trace JSON Extractor");
        queryTraceJsonPostProcessor.setProperty(TestElement.TEST_CLASS, JSONPostProcessor.class.getName());
        queryTraceJsonPostProcessor.setProperty(TestElement.GUI_CLASS, JSONPostProcessorGui.class.getName());

        JSONPathAssertion queryTraceAssertion = new JSONPathAssertion();
        queryTraceAssertion.setJsonPath("$.data");
        queryTraceAssertion.setExpectedValue("[]");
        queryTraceAssertion.setIsRegex(false);
        queryTraceAssertion.setExpectNull(false);
        queryTraceAssertion.setJsonValidationBool(true);
        queryTraceAssertion.setInvert(true);
        queryTraceAssertion.setName("JSON assertion");
        queryTraceAssertion.setProperty(TestElement.TEST_CLASS, JSONPathAssertion.class.getName());
        queryTraceAssertion.setProperty(TestElement.GUI_CLASS, JSONPathAssertionGui.class.getName());

        // While Controller
        WhileController whileController = new WhileController();
        whileController.setName("while Controller");
        whileController.setEnabled(true);
        whileController.setCondition("${__groovy(${counter} < 5 && vars.get(\"data\").equals(\"[]\") )}");

        whileController.setProperty(TestElement.TEST_CLASS, WhileController.class.getName());
        whileController.setProperty(TestElement.GUI_CLASS, WhileControllerGui.class.getName());

        CounterConfig counterConfig = new CounterConfig();
        counterConfig.setName("counter");
        counterConfig.setStart("1");
        counterConfig.setIncrement("1");
        counterConfig.setVarName("counter");
        counterConfig.setIsPerUser(true);
        counterConfig.setResetOnThreadGroupIteration(true);
        counterConfig.setProperty(TestElement.TEST_CLASS, CounterConfig.class.getName());
        counterConfig.setProperty(TestElement.GUI_CLASS, CounterConfigGui.class.getName());

        Arguments arguments = new Arguments();
        Argument arg1 = new Argument();
        arg1.setName("data");
        arg1.setValue("[]");
        arg1.setMetaData("=");
        Argument arg2 = new Argument();
        arg2.setName("counter");
        arg2.setValue("1");
        arg2.setMetaData("=");
        arguments.setName("User Defined Variables");
        arguments.setEnabled(true);
        arguments.addArgument(arg2);
        arguments.addArgument(arg1);
        arguments.setProperty(TestElement.TEST_CLASS, Argument.class.getName());
        arguments.setProperty(TestElement.GUI_CLASS, ArgumentsPanel.class.getName());


        ListedHashTree threadGroupHashTree = new ListedHashTree();

        //threadGroupHashTree.add(new Object[]{arguments,whileController,traceCreation});
        threadGroupHashTree.add(traceCreation);
        threadGroupHashTree.add(whileController);
        threadGroupHashTree.add(arguments);
        threadGroupHashTree.get(traceCreation).add(jsonPostProcessor);
        threadGroupHashTree.get(whileController).add(new Object[]{queryTrace,counterConfig});
        threadGroupHashTree.get(whileController).get(queryTrace).add(queryTraceJsonPostProcessor);
        threadGroupHashTree.get(whileController).get(queryTrace).add(queryTraceAssertion);

        rootTree.get(testPlan).add(threadGroup,threadGroupHashTree);
    }


}
