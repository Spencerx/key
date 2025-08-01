/* This file is part of KeY - https://key-project.org
 * KeY is licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package de.uka.ilkd.key.symbolic_execution.testcase;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import javax.xml.parsers.ParserConfigurationException;

import de.uka.ilkd.key.control.DefaultUserInterfaceControl;
import de.uka.ilkd.key.control.KeYEnvironment;
import de.uka.ilkd.key.java.*;
import de.uka.ilkd.key.java.Services.ITermProgramVariableCollectorFactory;
import de.uka.ilkd.key.java.statement.Try;
import de.uka.ilkd.key.logic.JTerm;
import de.uka.ilkd.key.logic.op.IProgramMethod;
import de.uka.ilkd.key.proof.Goal;
import de.uka.ilkd.key.proof.Node;
import de.uka.ilkd.key.proof.Proof;
import de.uka.ilkd.key.proof.TermProgramVariableCollectorKeepUpdatesForBreakpointconditions;
import de.uka.ilkd.key.proof.init.FunctionalOperationContractPO;
import de.uka.ilkd.key.proof.init.ProofInputException;
import de.uka.ilkd.key.proof.init.ProofOblInput;
import de.uka.ilkd.key.proof.io.ProblemLoaderException;
import de.uka.ilkd.key.proof.io.ProofSaver;
import de.uka.ilkd.key.settings.ChoiceSettings;
import de.uka.ilkd.key.settings.ProofSettings;
import de.uka.ilkd.key.speclang.Contract;
import de.uka.ilkd.key.speclang.FunctionalOperationContract;
import de.uka.ilkd.key.symbolic_execution.ExecutionNodePreorderIterator;
import de.uka.ilkd.key.symbolic_execution.ExecutionNodeReader;
import de.uka.ilkd.key.symbolic_execution.ExecutionNodeWriter;
import de.uka.ilkd.key.symbolic_execution.SymbolicExecutionTreeBuilder;
import de.uka.ilkd.key.symbolic_execution.SymbolicExecutionTreeBuilder.SymbolicExecutionCompletions;
import de.uka.ilkd.key.symbolic_execution.model.*;
import de.uka.ilkd.key.symbolic_execution.po.ProgramMethodPO;
import de.uka.ilkd.key.symbolic_execution.po.ProgramMethodSubsetPO;
import de.uka.ilkd.key.symbolic_execution.profile.SymbolicExecutionJavaProfile;
import de.uka.ilkd.key.symbolic_execution.strategy.*;
import de.uka.ilkd.key.symbolic_execution.util.SymbolicExecutionEnvironment;
import de.uka.ilkd.key.symbolic_execution.util.SymbolicExecutionUtil;
import de.uka.ilkd.key.util.HelperClassForTests;
import de.uka.ilkd.key.util.KeYConstants;

import org.key_project.logic.Choice;
import org.key_project.prover.sequent.Sequent;
import org.key_project.util.collection.DefaultImmutableSet;
import org.key_project.util.collection.ImmutableArray;
import org.key_project.util.collection.ImmutableList;
import org.key_project.util.collection.ImmutableSet;
import org.key_project.util.helper.FindResources;
import org.key_project.util.java.CollectionUtil;
import org.key_project.util.java.StringUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Provides the basic functionality of TestCases which tests the symbolic execution features.
 *
 * @author Martin Hentschel
 */
public abstract class AbstractSymbolicExecutionTestCase {
    private static final Logger LOGGER =
        LoggerFactory.getLogger(AbstractSymbolicExecutionTestCase.class);

    /**
     * <p>
     * If this constant is {@code true} a temporary directory is created with new oracle files. The
     * developer has then to copy the new required files into the plug-in so that they are used
     * during next test execution.
     * </p>
     * <p>
     * <b>Attention: </b> It is strongly required that new test scenarios are verified with the SED
     * application. If everything is fine a new test method can be added to this class and the first
     * test execution can be used to generate the required oracle file. Existing oracle files should
     * only be replaced if the functionality of the Symbolic Execution Debugger has changed so that
     * they are outdated.
     * </p>
     */
    public static final boolean CREATE_NEW_ORACLE_FILES_IN_TEMP_DIRECTORY =
        Boolean.getBoolean("UPDATE_TEST_ORACLE");


    static {
        LOGGER.warn("UPDATE_TEST_ORACLE is set to {}", CREATE_NEW_ORACLE_FILES_IN_TEMP_DIRECTORY);
    }

    /**
     * If the fast mode is enabled the stepwise creation of models is disabled.
     */
    public static final boolean FAST_MODE = true;

    /**
     * Number of executed SET nodes to execute all in one.
     */
    public static final int ALL_IN_ONE_RUN =
        ExecutedSymbolicExecutionTreeNodesStopCondition.MAXIMAL_NUMBER_OF_SET_NODES_TO_EXECUTE_PER_GOAL_IN_COMPLETE_RUN;

    /**
     * Number of executed SET nodes for only one SET node per auto mode run.
     */
    public static final int SINGLE_SET_NODE_RUN =
        ExecutedSymbolicExecutionTreeNodesStopCondition.MAXIMAL_NUMBER_OF_SET_NODES_TO_EXECUTE_PER_GOAL_FOR_ONE_STEP;

    /**
     * Default stop conditions of executed SET nodes.
     */
    public static final int[] DEFAULT_MAXIMAL_SET_NODES_PER_RUN;

    /**
     * The used temporary oracle directory.
     */
    protected static final File tempNewOracleDirectory;

    /**
     * The directory which contains the KeY repository.
     */
    public static final Path testCaseDirectory = FindResources.getTestCasesDirectory();

    static {
        assertNotNull(testCaseDirectory, "Could not find test case directory");
    }

    /*
     * Creates the temporary oracle directory if required.
     */
    static {
        // Define fast mode
        if (FAST_MODE) {
            DEFAULT_MAXIMAL_SET_NODES_PER_RUN = new int[] { ALL_IN_ONE_RUN };
        } else {
            DEFAULT_MAXIMAL_SET_NODES_PER_RUN = new int[] { ALL_IN_ONE_RUN, SINGLE_SET_NODE_RUN };
        }
        // Create temporary director for oracle files if required.
        File directory = null;
        try {
            if (CREATE_NEW_ORACLE_FILES_IN_TEMP_DIRECTORY) {
                directory = File.createTempFile("SYMBOLIC_EXECUTION", "ORACLE_DIRECTORY");
                if (System.getProperty("ORACLE_DIRECTORY") != null
                        && !System.getProperty("ORACLE_DIRECTORY").isBlank()) {
                    directory = new File(System.getProperty("ORACLE_DIRECTORY"));
                }
                LOGGER.warn("Create oracle files in {}", directory);
                directory.delete();
                directory.mkdirs();
            }
        } catch (IOException e) {
        }
        tempNewOracleDirectory = directory;
    }

    /**
     * Creates a new oracle file.
     *
     * @param node The node to save as oracle file.
     * @param oraclePathInBaseDirFile The path in example directory.
     * @param saveConstraints Save constraints?
     * @param saveVariables Save variables?
     * @param saveCallStack Save call stack?
     * @param saveReturnValues Save method return values?
     * @throws IOException Occurred Exception
     * @throws ProofInputException Occurred Exception
     */
    protected static void createOracleFile(IExecutionNode<?> node, String oraclePathInBaseDirFile,
            boolean saveConstraints, boolean saveVariables, boolean saveCallStack,
            boolean saveReturnValues) throws IOException, ProofInputException {
        if (tempNewOracleDirectory != null && tempNewOracleDirectory.isDirectory()) {
            // Create sub folder structure
            File oracleFile = new File(tempNewOracleDirectory, oraclePathInBaseDirFile);
            oracleFile.getParentFile().mkdirs();
            // Create oracle file
            ExecutionNodeWriter writer = new ExecutionNodeWriter();
            writer.write(node, ExecutionNodeWriter.DEFAULT_ENCODING, oracleFile, saveVariables,
                saveCallStack, saveReturnValues, saveConstraints);
            // Print message to the user.
            printOracleDirectory();
        }
    }

    /**
     * Prints {@link #tempNewOracleDirectory} to the user via {@link System#out}.
     */
    protected static void printOracleDirectory() {
        if (tempNewOracleDirectory != null) {
            final String HEADER_LINE = "Oracle Directory is:";
            final String PREFIX = "### ";
            final String SUFFIX = " ###";
            String path = tempNewOracleDirectory.toString();
            int length = Math.max(path.length(), HEADER_LINE.length());
            String borderLines = StringUtil.repeat("#", PREFIX.length() + length + SUFFIX.length());
            LOGGER.info(borderLines);
            LOGGER.info(PREFIX + HEADER_LINE + StringUtil.repeat(" ", length - HEADER_LINE.length())
                    + SUFFIX);
            LOGGER.info(PREFIX + path + StringUtil.repeat(" ", length - path.length()) + SUFFIX);
            LOGGER.info(borderLines);
        }
    }

    /**
     * Makes sure that the given nodes and their subtrees contains the same content.
     *
     * @param expected The expected {@link IExecutionNode}.
     * @param current The current {@link IExecutionNode}.
     * @param compareVariables Compare variables?
     * @param compareCallStack Compare call stack?
     * @param compareChildOrder Is the order of children relevant?
     * @param compareReturnValues Compare return values?
     * @param compareConstraints Compare constraints?
     * @throws ProofInputException Occurred Exception.
     */
    public static void assertExecutionNodes(IExecutionNode<?> expected, IExecutionNode<?> current,
            boolean compareVariables, boolean compareCallStack, boolean compareChildOrder,
            boolean compareReturnValues, boolean compareConstraints) throws ProofInputException {
        if (compareChildOrder) {
            // Order of children must be the same.
            ExecutionNodePreorderIterator expectedExecutionTreeNodeIterator =
                new ExecutionNodePreorderIterator(expected);
            ExecutionNodePreorderIterator actualExecutionTreeNodeIterator =
                new ExecutionNodePreorderIterator(current);
            while (expectedExecutionTreeNodeIterator.hasNext()
                    && actualExecutionTreeNodeIterator.hasNext()) {
                IExecutionNode<?> expectedNext = expectedExecutionTreeNodeIterator.next();
                IExecutionNode<?> currentNext = actualExecutionTreeNodeIterator.next();
                assertExecutionNode(expectedNext, currentNext, true, compareVariables,
                    compareCallStack, compareReturnValues, compareConstraints);
            }
            assertFalse(expectedExecutionTreeNodeIterator.hasNext());
            assertFalse(actualExecutionTreeNodeIterator.hasNext());
        } else {
            // Order of children is not relevant.
            ExecutionNodePreorderIterator expectedExecutionTreeNodeIterator =
                new ExecutionNodePreorderIterator(expected);
            Set<IExecutionNode<?>> currentVisitedNodes = new LinkedHashSet<>();
            while (expectedExecutionTreeNodeIterator.hasNext()) {
                IExecutionNode<?> expectedNext = expectedExecutionTreeNodeIterator.next();
                IExecutionNode<?> currentNext = searchExecutionNode(current, expectedNext);
                if (!currentVisitedNodes.add(currentNext)) {
                    fail("Node " + currentNext + " visited twice.");
                }
                assertExecutionNode(expectedNext, currentNext, true, compareVariables,
                    compareCallStack, compareReturnValues, compareConstraints);
            }
            // Make sure that each current node was visited
            ExecutionNodePreorderIterator actualExecutionTreeNodeIterator =
                new ExecutionNodePreorderIterator(current);
            while (actualExecutionTreeNodeIterator.hasNext()) {
                IExecutionNode<?> currentNext = actualExecutionTreeNodeIterator.next();
                if (!currentVisitedNodes.remove(currentNext)) {
                    fail("Node " + currentNext + " is not in expected model.");
                }
            }
            assertTrue(currentVisitedNodes.isEmpty());
        }
    }

    /**
     * Searches the direct or indirect child in subtree of the node to search in.
     *
     * @param toSearchIn The node to search in.
     * @param childToSearch The node to search.
     * @return The found node.
     * @throws ProofInputException Occurred Exception.
     */
    protected static IExecutionNode<?> searchExecutionNode(IExecutionNode<?> toSearchIn,
            IExecutionNode<?> childToSearch) throws ProofInputException {
        // Make sure that parameters are valid
        assertNotNull(toSearchIn);
        assertNotNull(childToSearch);
        // Collect parents
        Deque<IExecutionNode<?>> parents = new LinkedList<>();
        IExecutionNode<?> parent = childToSearch;
        while (parent != null) {
            parents.addFirst(parent);
            parent = parent.getParent();
        }
        // Search children in parent order
        boolean afterFirst = false;
        for (IExecutionNode<?> currentParent : parents) {
            if (afterFirst) {
                toSearchIn = searchDirectChildNode(toSearchIn, currentParent);
            } else {
                afterFirst = true;
            }
        }
        assertNotNull(toSearchIn, "Direct or indirect Child " + childToSearch
            + " is not contained in " + toSearchIn + ".");
        return toSearchIn;
    }

    /**
     * Searches the direct child. Nodes are equal if the name and the element type is equal.
     *
     * @param parentToSearchIn The parent to search in its children.
     * @param directChildToSearch The child to search.
     * @return The found child.
     * @throws ProofInputException Occurred Exception.
     */
    protected static IExecutionNode<?> searchDirectChildNode(IExecutionNode<?> parentToSearchIn,
            IExecutionNode<?> directChildToSearch) throws ProofInputException {
        // Make sure that parameters are valid
        assertNotNull(parentToSearchIn);
        assertNotNull(directChildToSearch);
        // Search child
        IExecutionNode<?> result = null;
        int i = 0;
        IExecutionNode<?>[] children = parentToSearchIn.getChildren();
        while (result == null && i < children.length) {
            if (children[i] instanceof IExecutionBranchCondition
                    && directChildToSearch instanceof IExecutionBranchCondition) {
                if (StringUtil
                        .equalIgnoreWhiteSpace(children[i].getName(), directChildToSearch.getName())
                        && StringUtil.equalIgnoreWhiteSpace(
                            ((IExecutionBranchCondition) children[i]).getAdditionalBranchLabel(),
                            ((IExecutionBranchCondition) directChildToSearch)
                                    .getAdditionalBranchLabel())
                        && children[i].getElementType()
                                .equals(directChildToSearch.getElementType())) {
                    result = children[i];
                }
            } else {
                if (StringUtil
                        .equalIgnoreWhiteSpace(children[i].getName(), directChildToSearch.getName())
                        && children[i].getElementType()
                                .equals(directChildToSearch.getElementType())) {
                    result = children[i];
                }
            }
            i++;
        }
        assertNotNull(result,
            "Child " + directChildToSearch + " is not contained in " + parentToSearchIn + ".");
        return result;
    }

    /**
     * Makes sure that the given nodes contains the same content. Children are not compared.
     *
     * @param expected The expected {@link IExecutionNode}.
     * @param current The current {@link IExecutionNode}.
     * @param compareParent Compare also the parent node?
     * @param compareVariables Compare variables?
     * @param compareCallStack Compare call stack?
     * @param compareReturnValues Compare return values?
     * @param compareConstraints Compare constraints?
     * @throws ProofInputException Occurred Exception.
     */
    protected static void assertExecutionNode(IExecutionNode<?> expected, IExecutionNode<?> current,
            boolean compareParent, boolean compareVariables, boolean compareCallStack,
            boolean compareReturnValues, boolean compareConstraints) throws ProofInputException {
        // Compare nodes
        assertNotNull(expected);
        assertNotNull(current);
        assertTrue(StringUtil.equalIgnoreWhiteSpace(expected.getName(), current.getName()),
            "Expected \"" + expected.getName() + "\" but is \"" + current.getName() + "\".");
        assertEquals(expected.isPathConditionChanged(), current.isPathConditionChanged());
        if (!StringUtil.equalIgnoreWhiteSpace(expected.getFormatedPathCondition(),
            current.getFormatedPathCondition())) {
            assertEquals(expected.getFormatedPathCondition(), current.getFormatedPathCondition());
        }
        if (compareParent) {
            if (expected instanceof IExecutionBlockStartNode<?>) {
                assertInstanceOf(IExecutionBlockStartNode.class, current);
                assertEquals(((IExecutionBlockStartNode<?>) expected).isBlockOpened(),
                    ((IExecutionBlockStartNode<?>) current).isBlockOpened());
                assertBlockCompletions((IExecutionBlockStartNode<?>) expected,
                    (IExecutionBlockStartNode<?>) current);
            }
            assertCompletedBlocks(expected, current);
            assertOutgoingLinks(expected, current);
            assertIncomingLinks(expected, current);
        }
        if (expected instanceof IExecutionBaseMethodReturn<?>) {
            assertInstanceOf(IExecutionBaseMethodReturn.class, current);
            assertCallStateVariables((IExecutionBaseMethodReturn<?>) expected,
                (IExecutionBaseMethodReturn<?>) current, compareVariables, compareConstraints);
        }
        if (expected instanceof IExecutionBranchCondition) {
            assertInstanceOf(IExecutionBranchCondition.class, current,
                "Expected IExecutionBranchCondition but is " + current.getClass() + ".");
            assertTrue(
                StringUtil.equalIgnoreWhiteSpace(
                    ((IExecutionBranchCondition) expected).getFormatedBranchCondition(),
                    ((IExecutionBranchCondition) current).getFormatedBranchCondition()),
                "Expected \"" + ((IExecutionBranchCondition) expected).getFormatedBranchCondition()
                    + "\" but is \""
                    + ((IExecutionBranchCondition) current).getFormatedBranchCondition() + "\".");
            assertEquals(((IExecutionBranchCondition) expected).isMergedBranchCondition(),
                ((IExecutionBranchCondition) current).isMergedBranchCondition());
            assertEquals(((IExecutionBranchCondition) expected).isBranchConditionComputed(),
                ((IExecutionBranchCondition) current).isBranchConditionComputed());
            assertTrue(
                StringUtil.equalIgnoreWhiteSpace(
                    ((IExecutionBranchCondition) expected).getAdditionalBranchLabel(),
                    ((IExecutionBranchCondition) current).getAdditionalBranchLabel()),
                "Expected \"" + ((IExecutionBranchCondition) expected).getAdditionalBranchLabel()
                    + "\" but is \""
                    + ((IExecutionBranchCondition) current).getAdditionalBranchLabel() + "\".");
            assertVariables(expected, current, compareVariables, compareConstraints);
            assertConstraints(expected, current, compareConstraints);
        } else if (expected instanceof IExecutionStart) {
            assertInstanceOf(IExecutionStart.class, current, "Expected IExecutionStartNode but is "
                + current.getClass() + ".");
            assertTerminations((IExecutionStart) expected, (IExecutionStart) current);
            assertVariables(expected, current, compareVariables, compareConstraints);
            assertConstraints(expected, current, compareConstraints);
        } else if (expected instanceof IExecutionTermination) {
            assertInstanceOf(IExecutionTermination.class, current,
                "Expected IExecutionTermination but is "
                    + current.getClass() + ".");
            assertEquals(((IExecutionTermination) expected).getTerminationKind(),
                ((IExecutionTermination) current).getTerminationKind());
            assertEquals(((IExecutionTermination) expected).isBranchVerified(),
                ((IExecutionTermination) current).isBranchVerified());
            assertVariables(expected, current, compareVariables, compareConstraints);
            assertConstraints(expected, current, compareConstraints);
        } else if (expected instanceof IExecutionBranchStatement) {
            assertInstanceOf(IExecutionBranchStatement.class, current,
                "Expected IExecutionBranchStatement but is "
                    + current.getClass() + ".");
            assertVariables(expected, current, compareVariables, compareConstraints);
            assertConstraints(expected, current, compareConstraints);
        } else if (expected instanceof IExecutionLoopCondition) {
            assertInstanceOf(IExecutionLoopCondition.class, current,
                "Expected IExecutionLoopCondition but is "
                    + current.getClass() + ".");
            assertVariables(expected, current, compareVariables, compareConstraints);
            assertConstraints(expected, current, compareConstraints);
        } else if (expected instanceof IExecutionLoopStatement) {
            assertInstanceOf(IExecutionLoopStatement.class, current,
                "Expected IExecutionLoopStatement but is "
                    + current.getClass() + ".");
            assertVariables(expected, current, compareVariables, compareConstraints);
            assertConstraints(expected, current, compareConstraints);
        } else if (expected instanceof IExecutionMethodCall) {
            assertInstanceOf(IExecutionMethodCall.class, current,
                "Expected IExecutionMethodCall but is "
                    + current.getClass() + ".");
            assertVariables(expected, current, compareVariables, compareConstraints);
            assertConstraints(expected, current, compareConstraints);
            assertMethodReturns((IExecutionMethodCall) expected, (IExecutionMethodCall) current);
        } else if (expected instanceof IExecutionMethodReturn) {
            assertInstanceOf(IExecutionMethodReturn.class, current,
                "Expected IExecutionMethodReturn but is "
                    + current.getClass() + ".");
            assertTrue(
                StringUtil.equalIgnoreWhiteSpace(((IExecutionMethodReturn) expected).getSignature(),
                    ((IExecutionMethodReturn) current).getSignature()),
                ((IExecutionMethodReturn) expected).getSignature() + " does not match "
                    + ((IExecutionMethodReturn) current).getSignature());
            if (compareReturnValues) {
                assertTrue(
                    StringUtil.equalIgnoreWhiteSpace(
                        ((IExecutionMethodReturn) expected).getNameIncludingReturnValue(),
                        ((IExecutionMethodReturn) current).getNameIncludingReturnValue()),
                    ((IExecutionMethodReturn) expected).getNameIncludingReturnValue()
                        + " does not match "
                        + ((IExecutionMethodReturn) current).getNameIncludingReturnValue());
                assertTrue(
                    StringUtil.equalIgnoreWhiteSpace(
                        ((IExecutionMethodReturn) expected).getSignatureIncludingReturnValue(),
                        ((IExecutionMethodReturn) current).getSignatureIncludingReturnValue()),
                    ((IExecutionMethodReturn) expected).getSignatureIncludingReturnValue()
                        + " does not match "
                        + ((IExecutionMethodReturn) current).getSignatureIncludingReturnValue());
                assertEquals(((IExecutionMethodReturn) expected).isReturnValuesComputed(),
                    ((IExecutionMethodReturn) current).isReturnValuesComputed());
            }
            assertTrue(
                StringUtil.equalIgnoreWhiteSpace(
                    ((IExecutionMethodReturn) expected).getFormattedMethodReturnCondition(),
                    ((IExecutionMethodReturn) current).getFormattedMethodReturnCondition()),
                ((IExecutionMethodReturn) expected).getFormattedMethodReturnCondition()
                    + " does not match "
                    + ((IExecutionMethodReturn) current).getFormattedMethodReturnCondition());
            assertVariables(expected, current, compareVariables, compareConstraints);
            assertConstraints(expected, current, compareConstraints);
            if (compareReturnValues) {
                assertReturnValues(((IExecutionMethodReturn) expected).getReturnValues(),
                    ((IExecutionMethodReturn) current).getReturnValues());
            }
        } else if (expected instanceof IExecutionExceptionalMethodReturn) {
            assertInstanceOf(IExecutionExceptionalMethodReturn.class, current,
                "Expected IExecutionExceptionalMethodReturn but is "
                    + current.getClass() + ".");
            assertTrue(
                StringUtil.equalIgnoreWhiteSpace(
                    ((IExecutionExceptionalMethodReturn) expected).getSignature(),
                    ((IExecutionExceptionalMethodReturn) current).getSignature()),
                ((IExecutionExceptionalMethodReturn) expected).getSignature() + " does not match "
                    + ((IExecutionExceptionalMethodReturn) current).getSignature());
            assertTrue(StringUtil.equalIgnoreWhiteSpace(
                ((IExecutionExceptionalMethodReturn) expected).getFormattedMethodReturnCondition(),
                ((IExecutionExceptionalMethodReturn) current).getFormattedMethodReturnCondition()),
                ((IExecutionExceptionalMethodReturn) expected).getFormattedMethodReturnCondition()
                    + " does not match " + ((IExecutionExceptionalMethodReturn) current)
                            .getFormattedMethodReturnCondition());
            assertVariables(expected, current, compareVariables, compareConstraints);
            assertConstraints(expected, current, compareConstraints);
        } else if (expected instanceof IExecutionStatement) {
            assertInstanceOf(IExecutionStatement.class, current,
                "Expected IExecutionStatement but is "
                    + current.getClass() + ".");
            assertVariables(expected, current, compareVariables, compareConstraints);
            assertConstraints(expected, current, compareConstraints);
        } else if (expected instanceof IExecutionOperationContract) {
            assertInstanceOf(IExecutionOperationContract.class, current,
                "Expected IExecutionOperationContract but is "
                    + current.getClass() + ".");
            assertEquals(((IExecutionOperationContract) expected).isPreconditionComplied(),
                ((IExecutionOperationContract) current).isPreconditionComplied());
            assertEquals(((IExecutionOperationContract) expected).hasNotNullCheck(),
                ((IExecutionOperationContract) current).hasNotNullCheck());
            assertEquals(((IExecutionOperationContract) expected).isNotNullCheckComplied(),
                ((IExecutionOperationContract) current).isNotNullCheckComplied());
            assertEquals(((IExecutionOperationContract) expected).getFormatedResultTerm(),
                ((IExecutionOperationContract) current).getFormatedResultTerm());
            assertEquals(((IExecutionOperationContract) expected).getFormatedExceptionTerm(),
                ((IExecutionOperationContract) current).getFormatedExceptionTerm());
            assertEquals(((IExecutionOperationContract) expected).getFormatedSelfTerm(),
                ((IExecutionOperationContract) current).getFormatedSelfTerm());
            assertEquals(((IExecutionOperationContract) expected).getFormatedContractParams(),
                ((IExecutionOperationContract) current).getFormatedContractParams());
            assertVariables(expected, current, compareVariables, compareConstraints);
            assertConstraints(expected, current, compareConstraints);
        } else if (expected instanceof IExecutionLoopInvariant) {
            assertInstanceOf(IExecutionLoopInvariant.class, current,
                "Expected IExecutionLoopInvariant but is "
                    + current.getClass() + ".");
            assertEquals(((IExecutionLoopInvariant) expected).isInitiallyValid(),
                ((IExecutionLoopInvariant) current).isInitiallyValid());
            assertVariables(expected, current, compareVariables, compareConstraints);
            assertConstraints(expected, current, compareConstraints);
        } else if (expected instanceof IExecutionAuxiliaryContract) {
            assertInstanceOf(IExecutionAuxiliaryContract.class, current,
                "Expected IExecutionBlockContract but is "
                    + current.getClass() + ".");
            assertEquals(((IExecutionAuxiliaryContract) expected).isPreconditionComplied(),
                ((IExecutionAuxiliaryContract) current).isPreconditionComplied());
            assertVariables(expected, current, compareVariables, compareConstraints);
            assertConstraints(expected, current, compareConstraints);
        } else if (expected instanceof IExecutionJoin) {
            assertInstanceOf(IExecutionJoin.class, current, "Expected IExecutionJoin but is "
                + current.getClass() + ".");
            assertEquals(((IExecutionJoin) expected).isWeakeningVerified(),
                ((IExecutionJoin) current).isWeakeningVerified());
            assertVariables(expected, current, compareVariables, compareConstraints);
            assertConstraints(expected, current, compareConstraints);
        } else {
            fail("Unknown execution node \"" + expected + "\".");
        }
        // Optionally compare call stack
        if (compareCallStack) {
            IExecutionNode<?>[] expectedStack = expected.getCallStack();
            IExecutionNode<?>[] currentStack = current.getCallStack();
            if (expectedStack != null) {
                assertNotNull(currentStack,
                    "Call stack of \"" + current + "\" should not be null.");
                assertEquals(expectedStack.length, currentStack.length, "Node: " + expected);
                for (int i = 0; i < expectedStack.length; i++) {
                    assertExecutionNode(expectedStack[i], currentStack[i], false, false, false,
                        false, false);
                }
            } else {
                assertTrue(currentStack == null || currentStack.length == 0,
                    "Call stack of \"" + current + "\" is \"" + Arrays.toString(currentStack)
                        + "\" but should be null or empty.");
            }
        }
        // Optionally compare parent
        if (compareParent) {
            assertExecutionNode(expected, current, false, compareVariables, compareCallStack,
                compareReturnValues, compareConstraints);
        }
    }

    /**
     * Compares the outgoing links.
     *
     * @param expected The expected {@link IExecutionNode}.
     * @param current The current {@link IExecutionNode}.
     * @throws ProofInputException Occurred Exception.
     */
    protected static void assertOutgoingLinks(IExecutionNode<?> expected, IExecutionNode<?> current)
            throws ProofInputException {
        ImmutableList<IExecutionLink> expectedEntries = expected.getOutgoingLinks();
        ImmutableList<IExecutionLink> currentEntries = current.getOutgoingLinks();
        if (expectedEntries != null) {
            assertNotNull(currentEntries,
                "Outgoing links of \"" + current + "\" should not be null.");
            assertEquals(expectedEntries.size(), currentEntries.size(),
                "Outgoing links: " + expected);
            Iterator<IExecutionLink> expectedExecutionTreeNodeIterator = expectedEntries.iterator();
            Iterator<IExecutionLink> actualExecutionTreeNodeIterator = currentEntries.iterator();
            while (expectedExecutionTreeNodeIterator.hasNext()
                    && actualExecutionTreeNodeIterator.hasNext()) {
                IExecutionLink expectedNext = expectedExecutionTreeNodeIterator.next();
                IExecutionLink currentNext = actualExecutionTreeNodeIterator.next();
                assertExecutionNode(expectedNext.getSource(), currentNext.getSource(), false, false,
                    false, false, false);
                assertExecutionNode(expectedNext.getTarget(), currentNext.getTarget(), false, false,
                    false, false, false);
            }
            assertFalse(expectedExecutionTreeNodeIterator.hasNext());
            assertFalse(actualExecutionTreeNodeIterator.hasNext());
        } else {
            assertTrue(currentEntries == null || currentEntries.isEmpty(), "Outgoing links of \""
                + current + "\" is \"" + currentEntries + "\" but should be null or empty.");
        }
    }

    /**
     * Compares the incoming links.
     *
     * @param expected The expected {@link IExecutionNode}.
     * @param current The current {@link IExecutionNode}.
     * @throws ProofInputException Occurred Exception.
     */
    protected static void assertIncomingLinks(IExecutionNode<?> expected, IExecutionNode<?> current)
            throws ProofInputException {
        ImmutableList<IExecutionLink> expectedEntries = expected.getIncomingLinks();
        ImmutableList<IExecutionLink> currentEntries = current.getIncomingLinks();
        if (expectedEntries != null) {
            assertNotNull(currentEntries,
                "Incoming links of \"" + current + "\" should not be null.");
            assertEquals(expectedEntries.size(), currentEntries.size(),
                "Incoming links: " + expected);
            Iterator<IExecutionLink> expectedExecutionTreeNodeIterator = expectedEntries.iterator();
            Iterator<IExecutionLink> actualExecutionTreeNodeIterator = currentEntries.iterator();
            while (expectedExecutionTreeNodeIterator.hasNext()
                    && actualExecutionTreeNodeIterator.hasNext()) {
                IExecutionLink expectedNext = expectedExecutionTreeNodeIterator.next();
                IExecutionLink currentNext = actualExecutionTreeNodeIterator.next();
                assertExecutionNode(expectedNext.getSource(), currentNext.getSource(), false, false,
                    false, false, false);
                assertExecutionNode(expectedNext.getTarget(), currentNext.getTarget(), false, false,
                    false, false, false);
            }
            assertFalse(expectedExecutionTreeNodeIterator.hasNext());
            assertFalse(actualExecutionTreeNodeIterator.hasNext());
        } else {
            assertTrue(currentEntries == null || currentEntries.isEmpty(), "Incoming links of \""
                + current + "\" is \"" + currentEntries + "\" but should be null or empty.");
        }
    }

    /**
     * Compares the completed blocks.
     *
     * @param expected The expected {@link IExecutionNode}.
     * @param current The current {@link IExecutionNode}.
     * @throws ProofInputException Occurred Exception.
     */
    protected static void assertCompletedBlocks(IExecutionNode<?> expected,
            IExecutionNode<?> current) throws ProofInputException {
        ImmutableList<IExecutionBlockStartNode<?>> expectedEntries = expected.getCompletedBlocks();
        ImmutableList<IExecutionBlockStartNode<?>> currentEntries = current.getCompletedBlocks();
        if (expectedEntries != null) {
            assertNotNull(currentEntries,
                "Completed blocks of \"" + current + "\" should not be null.");
            assertEquals(expectedEntries.size(), currentEntries.size(), "Node: " + expected);
            Iterator<IExecutionBlockStartNode<?>> expectedExecutionTreeNodeIterator =
                expectedEntries.iterator();
            Iterator<IExecutionBlockStartNode<?>> actualExecutionTreeNodeIterator =
                currentEntries.iterator();
            while (expectedExecutionTreeNodeIterator.hasNext()
                    && actualExecutionTreeNodeIterator.hasNext()) {
                IExecutionBlockStartNode<?> expectedNext = expectedExecutionTreeNodeIterator.next();
                IExecutionBlockStartNode<?> currentNext = actualExecutionTreeNodeIterator.next();
                assertExecutionNode(expectedNext, currentNext, false, false,
                    false, false, false);
                String expectedCondition =
                    expected.getFormatedBlockCompletionCondition(expectedNext);
                String currentCondition = current.getFormatedBlockCompletionCondition(currentNext);
                if (!StringUtil.equalIgnoreWhiteSpace(expectedCondition, currentCondition)) {
                    assertEquals(expectedCondition, currentCondition);
                }
            }
            assertFalse(expectedExecutionTreeNodeIterator.hasNext());
            assertFalse(actualExecutionTreeNodeIterator.hasNext());
        } else {
            assertTrue(currentEntries == null || currentEntries.isEmpty(),
                "Completed block entries of \"" + current + "\" is \"" + currentEntries
                    + "\" but should be null or empty.");
        }
    }

    /**
     * Compares the block completions.
     *
     * @param expected The expected {@link IExecutionBlockStartNode}.
     * @param current The current {@link IExecutionBlockStartNode}.
     * @throws ProofInputException Occurred Exception.
     */
    protected static void assertBlockCompletions(IExecutionBlockStartNode<?> expected,
            IExecutionBlockStartNode<?> current) throws ProofInputException {
        ImmutableList<IExecutionNode<?>> expectedEntries = expected.getBlockCompletions();
        ImmutableList<IExecutionNode<?>> currentEntries = current.getBlockCompletions();
        if (expectedEntries != null) {
            assertNotNull(currentEntries,
                "Block completions of \"" + current + "\" should not be null.");
            assertEquals(expectedEntries.size(), currentEntries.size(), "Node: " + expected);
            Iterator<IExecutionNode<?>> expectedExecutionTreeNodeIterator =
                expectedEntries.iterator();
            Iterator<IExecutionNode<?>> actualExecutionTreeNodeIterator = currentEntries.iterator();
            while (expectedExecutionTreeNodeIterator.hasNext()
                    && actualExecutionTreeNodeIterator.hasNext()) {
                assertExecutionNode(expectedExecutionTreeNodeIterator.next(),
                    actualExecutionTreeNodeIterator.next(),
                    false, false, false,
                    false, false);
            }
            assertFalse(expectedExecutionTreeNodeIterator.hasNext());
            assertFalse(actualExecutionTreeNodeIterator.hasNext());
        } else {
            assertTrue(currentEntries == null || currentEntries.isEmpty(),
                "Block completion entries of \"" + current + "\" is \"" + currentEntries
                    + "\" but should be null or empty.");
        }
    }

    /**
     * Compares the method returns.
     *
     * @param expected The expected {@link IExecutionMethodCall}.
     * @param current The current {@link IExecutionMethodCall}.
     * @throws ProofInputException Occurred Exception.
     */
    protected static void assertMethodReturns(IExecutionMethodCall expected,
            IExecutionMethodCall current) throws ProofInputException {
        ImmutableList<IExecutionBaseMethodReturn<?>> expectedEntries = expected.getMethodReturns();
        ImmutableList<IExecutionBaseMethodReturn<?>> currentEntries = current.getMethodReturns();
        if (expectedEntries != null) {
            assertNotNull(currentEntries,
                "Method return of \"" + current + "\" should not be null.");
            assertEquals(expectedEntries.size(), currentEntries.size(), "Node: " + expected);
            Iterator<IExecutionBaseMethodReturn<?>> expectedExecutionTreeNodeIterator =
                expectedEntries.iterator();
            Iterator<IExecutionBaseMethodReturn<?>> actualExecutionTreeNodeIterator =
                currentEntries.iterator();
            while (expectedExecutionTreeNodeIterator.hasNext()
                    && actualExecutionTreeNodeIterator.hasNext()) {
                assertExecutionNode(expectedExecutionTreeNodeIterator.next(),
                    actualExecutionTreeNodeIterator.next(),
                    false, false, false,
                    false, false);
            }
            assertFalse(expectedExecutionTreeNodeIterator.hasNext());
            assertFalse(actualExecutionTreeNodeIterator.hasNext());
        } else {
            assertTrue(currentEntries == null || currentEntries.isEmpty(),
                "Method return entries of \"" + current + "\" is \"" + currentEntries
                    + "\" but should be null or empty.");
        }
    }

    /**
     * Compares the terminations.
     *
     * @param expected The expected {@link IExecutionStart}.
     * @param current The current {@link IExecutionStart}.
     * @throws ProofInputException Occurred Exception.
     */
    protected static void assertTerminations(IExecutionStart expected, IExecutionStart current)
            throws ProofInputException {
        ImmutableList<IExecutionTermination> expectedEntries = expected.getTerminations();
        ImmutableList<IExecutionTermination> currentEntries = current.getTerminations();
        if (expectedEntries != null) {
            assertNotNull(currentEntries, "Termination of \"" + current + "\" should not be null.");
            assertEquals(expectedEntries.size(), currentEntries.size(), "Node: " + expected);
            Iterator<IExecutionTermination> expectedExecutionTreeNodeIterator =
                expectedEntries.iterator();
            Iterator<IExecutionTermination> actualExecutionTreeNodeIterator =
                currentEntries.iterator();
            while (expectedExecutionTreeNodeIterator.hasNext()
                    && actualExecutionTreeNodeIterator.hasNext()) {
                assertExecutionNode(expectedExecutionTreeNodeIterator.next(),
                    actualExecutionTreeNodeIterator.next(),
                    false, false, false,
                    false, false);
            }
            assertFalse(expectedExecutionTreeNodeIterator.hasNext());
            assertFalse(actualExecutionTreeNodeIterator.hasNext());
        } else {
            assertTrue(currentEntries == null || currentEntries.isEmpty(),
                "Termination entries of \"" + current + "\" is \"" + currentEntries
                    + "\" but should be null or empty.");
        }
    }

    /**
     * Makes sure that the given nodes contains the same {@link IExecutionMethodReturnValue}s.
     *
     * @param expected The expected {@link IExecutionMethodReturnValue}s.
     * @param current The current {@link IExecutionMethodReturnValue}s.
     * @throws ProofInputException Occurred Exception.
     */
    protected static void assertReturnValues(IExecutionMethodReturnValue[] expected,
            IExecutionMethodReturnValue[] current) throws ProofInputException {
        assertNotNull(expected);
        assertNotNull(current);
        assertEquals(expected.length, current.length);
        for (int i = 0; i < expected.length; i++) {
            assertReturnValue(expected[i], current[i]);
        }
    }

    /**
     * Makes sure that the given {@link IExecutionMethodReturnValue}s are the same.
     *
     * @param expected The expected {@link IExecutionMethodReturnValue}.
     * @param current The current {@link IExecutionMethodReturnValue}.
     * @throws ProofInputException Occurred Exception.
     */
    protected static void assertReturnValue(IExecutionMethodReturnValue expected,
            IExecutionMethodReturnValue current) throws ProofInputException {
        assertNotNull(expected);
        assertNotNull(current);
        assertTrue(StringUtil.equalIgnoreWhiteSpace(expected.getName(), current.getName()),
            expected.getName() + " does not match " + current.getName());
        assertTrue(
            StringUtil.equalIgnoreWhiteSpace(expected.getReturnValueString(),
                current.getReturnValueString()),
            expected.getReturnValueString() + " does not match " + current.getReturnValueString());
        assertEquals(expected.hasCondition(), current.hasCondition());
        assertTrue(
            StringUtil.equalIgnoreWhiteSpace(expected.getConditionString(),
                current.getConditionString()),
            expected.getConditionString() + " does not match " + current.getConditionString());
    }

    /**
     * Makes sure that the given nodes contains the same {@link IExecutionNode}s.
     *
     * @param expected The expected node.
     * @param current The current node.
     * @param compareConstraints Compare constraints?
     * @throws ProofInputException Occurred Exception.
     */
    protected static void assertConstraints(IExecutionNode<?> expected, IExecutionNode<?> current,
            boolean compareConstraints) throws ProofInputException {
        if (compareConstraints) {
            assertNotNull(expected);
            assertNotNull(current);
            IExecutionConstraint[] expectedVariables = expected.getConstraints();
            IExecutionConstraint[] currentVariables = current.getConstraints();
            assertConstraints(expectedVariables, currentVariables);
        }
    }

    /**
     * Makes sure that the given constraints are the same.
     *
     * @param expected The expected constraints.
     * @param current The current constraints.
     * @throws ProofInputException Occurred Exception.
     */
    protected static void assertConstraints(IExecutionConstraint[] expected,
            IExecutionConstraint[] current) throws ProofInputException {
        assertEquals(expected.length, current.length);
        // Compare ignore order
        List<IExecutionConstraint> availableCurrentVariables =
            new ArrayList<>(Arrays.asList(current));
        for (final IExecutionConstraint expectedVariable : expected) {
            // Find current variable with same name
            IExecutionConstraint currentVariable = CollectionUtil.searchAndRemove(
                availableCurrentVariables, element -> {
                    try {
                        return StringUtil.equalIgnoreWhiteSpace(expectedVariable.getName(),
                            element.getName());
                    } catch (ProofInputException e) {
                        throw new RuntimeException(e);
                    }
                });
            assertNotNull(currentVariable);
            // Compare variables
            assertConstraint(expectedVariable, currentVariable);
        }
        assertTrue(availableCurrentVariables.isEmpty());
    }

    /**
     * Makes sure that the given constraints are the same.
     *
     * @param expected The expected constraint.
     * @param current The current constraint.
     * @throws ProofInputException Occurred Exception.
     */
    protected static void assertConstraint(IExecutionConstraint expected,
            IExecutionConstraint current) throws ProofInputException {
        if (expected != null) {
            assertNotNull(current);
            if (!StringUtil.equalIgnoreWhiteSpace(expected.getName(), current.getName())) {
                assertEquals(expected.getName(), current.getName());
            }
        } else {
            assertNull(current);
        }
    }

    /**
     * Makes sure that the given nodes contains the same {@link IExecutionVariable}s of the call
     * state.
     *
     * @param expected The expected node.
     * @param current The current node.
     * @param compareVariables Compare variables?
     * @param compareConstraints Compare constraints?
     * @throws ProofInputException Occurred Exception.
     */
    protected static void assertCallStateVariables(IExecutionBaseMethodReturn<?> expected,
            IExecutionBaseMethodReturn<?> current, boolean compareVariables,
            boolean compareConstraints) throws ProofInputException {
        if (compareVariables) {
            assertNotNull(expected);
            assertNotNull(current);
            IExecutionVariable[] expectedVariables = expected.getCallStateVariables();
            IExecutionVariable[] currentVariables = current.getCallStateVariables();
            assertVariables(expectedVariables, currentVariables, true, true, compareConstraints);
        }
    }

    /**
     * Makes sure that the given nodes contains the same {@link IExecutionVariable}s.
     *
     * @param expected The expected node.
     * @param current The current node.
     * @param compareVariables Compare variables?
     * @param compareConstraints Compare constraints?
     * @throws ProofInputException Occurred Exception.
     */
    protected static void assertVariables(IExecutionNode<?> expected, IExecutionNode<?> current,
            boolean compareVariables, boolean compareConstraints) throws ProofInputException {
        if (compareVariables) {
            assertNotNull(expected);
            assertNotNull(current);
            IExecutionVariable[] expectedVariables = expected.getVariables();
            IExecutionVariable[] currentVariables = current.getVariables();
            assertVariables(expectedVariables, currentVariables, true, true, compareConstraints);
        }
    }

    /**
     * Makes sure that the given variables are the same.
     *
     * @param expected The expected variables.
     * @param current The current variables.
     * @param compareParent Compare parent?
     * @param compareChildren Compare children?
     * @param compareConstraints Compare constraints?
     * @throws ProofInputException Occurred Exception.
     */
    protected static void assertVariables(IExecutionVariable[] expected,
            IExecutionVariable[] current, boolean compareParent, boolean compareChildren,
            boolean compareConstraints) throws ProofInputException {
        assertEquals(expected.length, current.length);
        // Compare ignore order
        List<IExecutionVariable> availableCurrentVariables =
            new ArrayList<>(Arrays.asList(current));
        for (final IExecutionVariable expectedVariable : expected) {
            // Find current variable with same name
            IExecutionVariable currentVariable = CollectionUtil
                    .searchAndRemove(availableCurrentVariables, element -> {
                        try {
                            return StringUtil.equalIgnoreWhiteSpace(expectedVariable.getName(),
                                element.getName());
                        } catch (ProofInputException e) {
                            throw new RuntimeException(e);
                        }
                    });
            assertNotNull(currentVariable);
            // Compare variables
            assertVariable(expectedVariable, currentVariable, compareParent, compareChildren,
                compareConstraints);
        }
        assertTrue(availableCurrentVariables.isEmpty());
    }

    /**
     * Makes sure that the given variables are the same.
     *
     * @param expected The expected variable.
     * @param current The current variable.
     * @param compareParent Compare parent?
     * @param compareChildren Compare children?
     * @param compareConstraints Compare constraints?
     * @throws ProofInputException Occurred Exception.
     */
    protected static void assertVariable(IExecutionVariable expected, IExecutionVariable current,
            boolean compareParent, boolean compareChildren, boolean compareConstraints)
            throws ProofInputException {
        if (expected != null) {
            assertNotNull(current);
            // Compare variable
            assertEquals(expected.isArrayIndex(), current.isArrayIndex());
            assertEquals(expected.getArrayIndexString(), current.getArrayIndexString());
            assertEquals(expected.getName(), current.getName());
            // Compare parent
            if (compareParent) {
                assertValue(expected.getParentValue(), current.getParentValue(), false, false,
                    false);
            }
            // Compare children
            if (compareChildren) {
                IExecutionValue[] expectedValues = expected.getValues();
                IExecutionValue[] currentValues = current.getValues();
                assertValues(expectedValues, currentValues, true, true, compareConstraints);
            }
        } else {
            assertNull(current);
        }
    }

    /**
     * Makes sure that the given values are the same.
     *
     * @param expected The expected values.
     * @param current The current values.
     * @param compareParent Compare parent?
     * @param compareChildren Compare children?
     * @param compareConstraints Compare constraints?
     * @throws ProofInputException Occurred Exception.
     */
    protected static void assertValues(IExecutionValue[] expected, IExecutionValue[] current,
            boolean compareParent, boolean compareChildren, boolean compareConstraints)
            throws ProofInputException {
        assertEquals(expected.length, current.length);
        // Compare ignore order
        List<IExecutionValue> availableCurrentVariables = new ArrayList<>(Arrays.asList(current));
        for (final IExecutionValue expectedVariable : expected) {
            // Find current variable with same name
            IExecutionValue currentVariable = CollectionUtil
                    .searchAndRemove(availableCurrentVariables, element -> {
                        try {
                            return StringUtil.equalIgnoreWhiteSpace(expectedVariable.getName(),
                                element.getName())
                                    && StringUtil.equalIgnoreWhiteSpace(
                                        expectedVariable.getConditionString(),
                                        element.getConditionString());
                        } catch (ProofInputException e) {
                            throw new RuntimeException(e);
                        }
                    });
            assertNotNull(currentVariable);
            // Compare variables
            assertValue(expectedVariable, currentVariable, compareParent, compareChildren,
                compareConstraints);
        }
        assertTrue(availableCurrentVariables.isEmpty());
    }

    /**
     * Makes sure that the given values are the same.
     *
     * @param expected The expected variable.
     * @param current The current variable.
     * @param compareParent Compare parent?
     * @param compareChildren Compare children?
     * @param compareConstraints Compare constraints?
     * @throws ProofInputException Occurred Exception.
     */
    protected static void assertValue(IExecutionValue expected, IExecutionValue current,
            boolean compareParent, boolean compareChildren, boolean compareConstraints)
            throws ProofInputException {
        if (expected != null) {
            assertNotNull(current);
            // Compare variable
            assertTrue(StringUtil.equalIgnoreWhiteSpace(expected.getName(), current.getName()),
                expected.getName() + " does not match " + current.getName());
            assertEquals(expected.getTypeString(), current.getTypeString());
            assertTrue(
                StringUtil.equalIgnoreWhiteSpace(expected.getValueString(),
                    current.getValueString()),
                expected.getValueString() + " does not match " + current.getValueString());
            assertEquals(expected.isValueAnObject(), current.isValueAnObject());
            assertEquals(expected.isValueUnknown(), current.isValueUnknown());
            assertTrue(
                StringUtil.equalIgnoreWhiteSpace(expected.getConditionString(),
                    current.getConditionString()),
                expected.getConditionString() + " does not match " + current.getConditionString());
            // Compare parent
            if (compareParent) {
                assertVariable(expected.getVariable(), current.getVariable(), false, false,
                    compareConstraints);
            }
            // Compare children
            if (compareChildren) {
                IExecutionVariable[] expectedChildVariables = expected.getChildVariables();
                IExecutionVariable[] currentChildVariables = current.getChildVariables();
                assertVariables(expectedChildVariables, currentChildVariables, compareParent,
                    compareChildren, compareConstraints);
            }
            // Compare constraints
            if (compareConstraints) {
                IExecutionConstraint[] expectedConstraints = expected.getConstraints();
                IExecutionConstraint[] currentConstraints = current.getConstraints();
                assertConstraints(expectedConstraints, currentConstraints);
            }
        } else {
            assertNull(current);
        }
    }

    /**
     * Executes a "step return" global on all goals on the given
     * {@link SymbolicExecutionTreeBuilder}.
     *
     * @param ui The {@link DefaultUserInterfaceControl} to use.
     * @param builder The {@link SymbolicExecutionGoalChooser} to do step on.
     * @param oraclePathInBaseDirFile The oracle path.
     * @param oracleIndex The index of the current step.
     * @param oracleFileExtension The oracle file extension
     * @param baseDir The base directory for oracles.
     * @throws IOException Occurred Exception
     * @throws ProofInputException Occurred Exception
     * @throws ParserConfigurationException Occurred Exception
     * @throws SAXException Occurred Exception
     */
    protected static void stepReturn(DefaultUserInterfaceControl ui,
            SymbolicExecutionTreeBuilder builder, String oraclePathInBaseDirFile, int oracleIndex,
            String oracleFileExtension, Path baseDir)
            throws IOException, ProofInputException, ParserConfigurationException, SAXException {
        // Set stop condition to stop after a number of detected symbolic execution tree nodes
        // instead of applied rules
        Proof proof = builder.getProof();
        CompoundStopCondition stopCondition = new CompoundStopCondition();
        stopCondition.addChildren(new ExecutedSymbolicExecutionTreeNodesStopCondition(
            ExecutedSymbolicExecutionTreeNodesStopCondition.MAXIMAL_NUMBER_OF_SET_NODES_TO_EXECUTE_PER_GOAL_IN_COMPLETE_RUN));
        stopCondition.addChildren(new StepReturnSymbolicExecutionTreeNodesStopCondition());
        proof.getSettings().getStrategySettings()
                .setCustomApplyStrategyStopCondition(stopCondition);
        // Run proof
        ui.getProofControl().startAndWaitForAutoMode(proof);
        // Update symbolic execution tree
        builder.analyse();
        // Test result
        assertSetTreeAfterStep(builder, oraclePathInBaseDirFile, oracleIndex, oracleFileExtension,
            baseDir);
    }


    /**
     * Executes a "step return" global on all goals on the given
     * {@link SymbolicExecutionTreeBuilder}.
     *
     * @param ui The {@link DefaultUserInterfaceControl} to use.
     * @param builder The {@link SymbolicExecutionGoalChooser} to do step on.
     * @param oraclePathInBaseDirFile The oracle path.
     * @param oracleIndex The index of the current step.
     * @param oracleFileExtension The oracle file extension
     * @param baseDir The base directory for oracles.
     * @throws IOException Occurred Exception
     * @throws ProofInputException Occurred Exception
     * @throws ParserConfigurationException Occurred Exception
     * @throws SAXException Occurred Exception
     */
    protected static void stepReturnWithBreakpoints(DefaultUserInterfaceControl ui,
            SymbolicExecutionTreeBuilder builder, String oraclePathInBaseDirFile, int oracleIndex,
            String oracleFileExtension, Path baseDir, CompoundStopCondition lineBreakpoints)
            throws IOException, ProofInputException, ParserConfigurationException, SAXException {
        // Set stop condition to stop after a number of detected symbolic execution tree nodes
        // instead of applied rules
        Proof proof = builder.getProof();
        CompoundStopCondition stopCondition = new CompoundStopCondition();
        stopCondition.addChildren(new ExecutedSymbolicExecutionTreeNodesStopCondition(
            ExecutedSymbolicExecutionTreeNodesStopCondition.MAXIMAL_NUMBER_OF_SET_NODES_TO_EXECUTE_PER_GOAL_IN_COMPLETE_RUN));
        stopCondition.addChildren(new StepReturnSymbolicExecutionTreeNodesStopCondition());
        stopCondition.addChildren(lineBreakpoints);
        proof.getSettings().getStrategySettings()
                .setCustomApplyStrategyStopCondition(stopCondition);
        // Run proof
        ui.getProofControl().startAndWaitForAutoMode(proof);
        // Update symbolic execution tree
        builder.analyse();
        // Test result
        assertSetTreeAfterStep(builder, oraclePathInBaseDirFile, oracleIndex, oracleFileExtension,
            baseDir);
    }

    /**
     * Executes a "step over" global on all goals on the given
     * {@link SymbolicExecutionTreeBuilder}.
     *
     * @param ui The {@link DefaultUserInterfaceControl} to use.
     * @param builder The {@link SymbolicExecutionGoalChooser} to do step on.
     * @param oraclePathInBaseDirFile The oracle path.
     * @param oracleIndex The index of the current step.
     * @param oracleFileExtension The oracle file extension
     * @param baseDir The base directory for oracles.
     * @throws IOException Occurred Exception
     * @throws ProofInputException Occurred Exception
     * @throws ParserConfigurationException Occurred Exception
     * @throws SAXException Occurred Exception
     */
    protected static void stepOver(DefaultUserInterfaceControl ui,
            SymbolicExecutionTreeBuilder builder, String oraclePathInBaseDirFile, int oracleIndex,
            String oracleFileExtension, Path baseDir)
            throws IOException, ProofInputException, ParserConfigurationException, SAXException {
        // Set stop condition to stop after a number of detected symbolic execution tree nodes
        // instead of applied rules
        Proof proof = builder.getProof();
        CompoundStopCondition stopCondition = new CompoundStopCondition();
        stopCondition.addChildren(new ExecutedSymbolicExecutionTreeNodesStopCondition(
            ExecutedSymbolicExecutionTreeNodesStopCondition.MAXIMAL_NUMBER_OF_SET_NODES_TO_EXECUTE_PER_GOAL_IN_COMPLETE_RUN));
        stopCondition.addChildren(new StepOverSymbolicExecutionTreeNodesStopCondition());
        proof.getSettings().getStrategySettings()
                .setCustomApplyStrategyStopCondition(stopCondition);
        // Run proof
        ui.getProofControl().startAndWaitForAutoMode(proof);
        // Update symbolic execution tree
        builder.analyse();
        // Test result
        assertSetTreeAfterStep(builder, oraclePathInBaseDirFile, oracleIndex, oracleFileExtension,
            baseDir);
    }

    /**
     * Executes a "step into" global on all goals on the given
     * {@link SymbolicExecutionTreeBuilder}.
     *
     * @param ui The {@link DefaultUserInterfaceControl} to use.
     * @param builder The {@link SymbolicExecutionGoalChooser} to do step on.
     * @param oraclePathInBaseDirFile The oracle path.
     * @param oracleIndex The index of the current step.
     * @param oracleFileExtension The oracle file extension
     * @param baseDir The base directory for oracles.
     * @return The found {@link SymbolicExecutionCompletions}.
     * @throws IOException Occurred Exception
     * @throws ProofInputException Occurred Exception
     * @throws ParserConfigurationException Occurred Exception
     * @throws SAXException Occurred Exception
     */
    protected static SymbolicExecutionCompletions stepInto(DefaultUserInterfaceControl ui,
            SymbolicExecutionTreeBuilder builder, String oraclePathInBaseDirFile, int oracleIndex,
            String oracleFileExtension, Path baseDir)
            throws IOException, ProofInputException, ParserConfigurationException, SAXException {
        // Set stop condition to stop after a number of detected symbolic execution tree nodes
        // instead of applied rules
        Proof proof = builder.getProof();
        ExecutedSymbolicExecutionTreeNodesStopCondition stopCondition =
            new ExecutedSymbolicExecutionTreeNodesStopCondition(
                ExecutedSymbolicExecutionTreeNodesStopCondition.MAXIMAL_NUMBER_OF_SET_NODES_TO_EXECUTE_PER_GOAL_FOR_ONE_STEP);
        proof.getSettings().getStrategySettings()
                .setCustomApplyStrategyStopCondition(stopCondition);
        // Run proof
        ui.getProofControl().startAndWaitForAutoMode(proof);
        // Update symbolic execution tree
        SymbolicExecutionCompletions completions = builder.analyse();
        // Test result
        assertSetTreeAfterStep(builder, oraclePathInBaseDirFile, oracleIndex, oracleFileExtension,
            baseDir);
        return completions;
    }

    /**
     * Executes a "step into" global on all goals on the given
     * {@link SymbolicExecutionTreeBuilder}.
     *
     * @param ui The {@link DefaultUserInterfaceControl} to use.
     * @param builder The {@link SymbolicExecutionGoalChooser} to do step on.
     * @param oraclePathInBaseDirFile The oracle path.
     * @param baseDir The base directory for oracles.
     * @throws IOException Occurred Exception
     * @throws ProofInputException Occurred Exception
     * @throws ParserConfigurationException Occurred Exception
     * @throws SAXException Occurred Exception
     */
    protected static void resume(DefaultUserInterfaceControl ui,
            SymbolicExecutionTreeBuilder builder, String oraclePathInBaseDirFile, Path baseDir)
            throws IOException, ProofInputException, ParserConfigurationException, SAXException {
        // Set stop condition to stop after a number of detected symbolic execution tree nodes
        // instead of applied rules
        Proof proof = builder.getProof();
        ExecutedSymbolicExecutionTreeNodesStopCondition stopCondition =
            new ExecutedSymbolicExecutionTreeNodesStopCondition(
                ExecutedSymbolicExecutionTreeNodesStopCondition.MAXIMAL_NUMBER_OF_SET_NODES_TO_EXECUTE_PER_GOAL_IN_COMPLETE_RUN);
        proof.getSettings().getStrategySettings()
                .setCustomApplyStrategyStopCondition(stopCondition);
        // Run proof
        ui.getProofControl().startAndWaitForAutoMode(proof);
        // Update symbolic execution tree
        builder.analyse();
        // Test result
        assertSetTreeAfterStep(builder, oraclePathInBaseDirFile, baseDir);
    }

    /**
     * Makes sure that after a step the correct set tree is created.
     *
     * @param builder The {@link SymbolicExecutionTreeBuilder} to test.
     * @param oraclePathInBaseDirFile The oracle path.
     * @param baseDir The base directory for oracles.
     * @throws IOException Occurred Exception
     * @throws ProofInputException Occurred Exception
     * @throws ParserConfigurationException Occurred Exception
     * @throws SAXException Occurred Exception
     */
    protected static void assertSetTreeAfterStep(SymbolicExecutionTreeBuilder builder,
            String oraclePathInBaseDirFile, Path baseDir)
            throws IOException, ProofInputException, ParserConfigurationException, SAXException {
        if (CREATE_NEW_ORACLE_FILES_IN_TEMP_DIRECTORY) {
            createOracleFile(builder.getStartNode(), oraclePathInBaseDirFile, false, false, false,
                false);
        } else {
            // Read oracle file
            var oracleFile = baseDir.resolve(oraclePathInBaseDirFile);
            ExecutionNodeReader reader = new ExecutionNodeReader();
            IExecutionNode<?> oracleRoot = reader.read(oracleFile.toFile());
            assertNotNull(oracleRoot);
            // Make sure that the created symbolic execution tree matches the expected one.
            assertExecutionNodes(oracleRoot, builder.getStartNode(), false, false, false, false,
                false);
        }
    }

    /**
     * Makes sure that after a step the correct set tree is created.
     *
     * @param builder The {@link SymbolicExecutionTreeBuilder} to test.
     * @param oraclePathInBaseDirFile The oracle path.
     * @param oracleIndex The index of the current step.
     * @param oracleFileExtension The oracle file extension
     * @param baseDir The base directory for oracles.
     * @throws IOException Occurred Exception
     * @throws ProofInputException Occurred Exception
     * @throws ParserConfigurationException Occurred Exception
     * @throws SAXException Occurred Exception
     */
    protected static void assertSetTreeAfterStep(SymbolicExecutionTreeBuilder builder,
            String oraclePathInBaseDirFile, int oracleIndex, String oracleFileExtension,
            Path baseDir)
            throws IOException, ProofInputException, ParserConfigurationException, SAXException {
        assertSetTreeAfterStep(builder,
            oraclePathInBaseDirFile + "_" + oracleIndex + oracleFileExtension, baseDir);
    }

    /**
     * Searches a {@link IProgramMethod} in the given {@link Services}.
     *
     * @param services The {@link Services} to search in.
     * @param containerTypeName The name of the type which contains the method.
     * @param methodFullName The method name to search.
     * @return The first found {@link IProgramMethod} in the type.
     */
    public static IProgramMethod searchProgramMethod(Services services, String containerTypeName,
            final String methodFullName) {
        return HelperClassForTests.searchProgramMethod(services, containerTypeName, methodFullName);
    }

    /**
     * Creates a {@link SymbolicExecutionEnvironment} which consists of loading a file to load,
     * finding the method to proof, instantiation of proof and creation with configuration of
     * {@link SymbolicExecutionTreeBuilder}.
     *
     * @param baseDir The base directory which contains test and oracle file.
     * @param baseContractName The name of the contract.
     * @param mergeBranchConditions Merge branch conditions?
     * @param useOperationContracts Use operation contracts?
     * @param useLoopInvariants Use loop invariants?
     * @param blockTreatmentContract Block contracts or expand otherwise?
     * @param nonExecutionBranchHidingSideProofs {@code true} hide non execution branch labels by
     *        side proofs, {@code false} do not hide execution branch labels.
     * @param aliasChecks Do alias checks?
     * @param useUnicode {@code true} use unicode characters, {@code false} do not use unicode
     *        characters.
     * @param usePrettyPrinting {@code true} use pretty printing, {@code false} do not use pretty
     *        printing.
     * @param variablesAreOnlyComputedFromUpdates {@code true} {@link IExecutionVariable} are only
     *        computed from updates, {@code false} {@link IExecutionVariable}s are computed
     *        according to the type structure of the visible memory.
     * @param truthValueEvaluationEnabled {@code true} truth value evaluation is enabled,
     *        {@code false} truth value evaluation is disabled.
     * @param simplifyConditions {@code true} simplify conditions, {@code false} do not simplify
     *        conditions.
     * @return The created {@link SymbolicExecutionEnvironment}.
     * @throws ProblemLoaderException Occurred Exception.
     * @throws ProofInputException Occurred Exception.
     */
    protected static SymbolicExecutionEnvironment<DefaultUserInterfaceControl> createSymbolicExecutionEnvironment(
            Path baseDir, String javaPathInBaseDir, String baseContractName,
            boolean mergeBranchConditions, boolean useOperationContracts, boolean useLoopInvariants,
            boolean blockTreatmentContract, boolean nonExecutionBranchHidingSideProofs,
            boolean aliasChecks, boolean useUnicode, boolean usePrettyPrinting,
            boolean variablesAreOnlyComputedFromUpdates, boolean truthValueEvaluationEnabled,
            boolean simplifyConditions) throws ProblemLoaderException, ProofInputException {
        // Make sure that required files exists
        Path javaFile = baseDir.resolve(javaPathInBaseDir);
        assertTrue(Files.exists(javaFile));
        // Load java file
        KeYEnvironment<DefaultUserInterfaceControl> environment = KeYEnvironment.load(
            SymbolicExecutionJavaProfile.getDefaultInstance(truthValueEvaluationEnabled), javaFile,
            null, null, null, true);
        setupTacletOptions(environment);
        // Start proof
        final Contract contract = environment.getServices().getSpecificationRepository()
                .getContractByName(baseContractName);
        assertInstanceOf(FunctionalOperationContract.class, contract);
        ProofOblInput input = new FunctionalOperationContractPO(environment.getInitConfig(),
            (FunctionalOperationContract) contract, true, true);
        Proof proof = environment.createProof(input);
        assertNotNull(proof);
        // Set strategy and goal chooser to use for auto mode
        SymbolicExecutionEnvironment.configureProofForSymbolicExecution(proof,
            ExecutedSymbolicExecutionTreeNodesStopCondition.MAXIMAL_NUMBER_OF_SET_NODES_TO_EXECUTE_PER_GOAL_IN_COMPLETE_RUN,
            useOperationContracts, useLoopInvariants, blockTreatmentContract,
            nonExecutionBranchHidingSideProofs, aliasChecks);
        // Create symbolic execution tree which contains only the start node at beginning
        SymbolicExecutionTreeBuilder builder =
            new SymbolicExecutionTreeBuilder(proof, mergeBranchConditions, useUnicode,
                usePrettyPrinting, variablesAreOnlyComputedFromUpdates, simplifyConditions);
        SymbolicExecutionUtil.initializeStrategy(builder);
        builder.analyse();
        assertNotNull(builder.getStartNode());
        return new SymbolicExecutionEnvironment<>(environment, builder);
    }

    /**
     * Creates a {@link SymbolicExecutionEnvironment} which consists of loading a file to load,
     * finding the method to proof, instantiation of proof and creation with configuration of
     * {@link SymbolicExecutionTreeBuilder}.
     *
     * @param baseDir The base directory which contains test and oracle file.
     * @param javaPathInBaseDir The path to the java file inside the base directory.
     * @param containerTypeName The name of the type which contains the method.
     * @param methodFullName The method name to search.
     * @param precondition An optional precondition to use.
     * @param mergeBranchConditions Merge branch conditions?
     * @param useOperationContracts Use operation contracts?
     * @param useLoopInvariants Use loop invariants?
     * @param blockTreatmentContract Block contracts or expand otherwise?
     * @param nonExecutionBranchHidingSideProofs {@code true} hide non execution branch labels by
     *        side proofs, {@code false} do not hide execution branch labels.
     * @param aliasChecks Do alias checks?
     * @param useUnicode {@code true} use unicode characters, {@code false} do not use unicode
     *        characters.
     * @param usePrettyPrinting {@code true} use pretty printing, {@code false} do not use pretty
     *        printing.
     * @param variablesAreOnlyComputedFromUpdates {@code true} {@link IExecutionVariable} are only
     *        computed from updates, {@code false} {@link IExecutionVariable}s are computed
     *        according to the type structure of the visible memory.
     * @param simplifyConditions {@code true} simplify conditions, {@code false} do not simplify
     *        conditions.
     * @return The created {@link SymbolicExecutionEnvironment}.
     * @throws ProblemLoaderException Occurred Exception.
     * @throws ProofInputException Occurred Exception.
     */
    protected static SymbolicExecutionEnvironment<DefaultUserInterfaceControl> createSymbolicExecutionEnvironment(
            Path baseDir, String javaPathInBaseDir, String containerTypeName, String methodFullName,
            String precondition, boolean mergeBranchConditions, boolean useOperationContracts,
            boolean useLoopInvariants, boolean blockTreatmentContract,
            boolean nonExecutionBranchHidingSideProofs, boolean aliasChecks, boolean useUnicode,
            boolean usePrettyPrinting, boolean variablesAreOnlyComputedFromUpdates,
            boolean simplifyConditions) throws ProblemLoaderException, ProofInputException {
        // Make sure that required files exists
        Path javaFile = baseDir.resolve(javaPathInBaseDir);
        assertTrue(Files.exists(javaFile));
        // Load java file
        KeYEnvironment<DefaultUserInterfaceControl> environment = KeYEnvironment.load(
            SymbolicExecutionJavaProfile.getDefaultInstance(), javaFile, null, null, null, true);
        setupTacletOptions(environment);
        // Search method to proof
        IProgramMethod pm =
            searchProgramMethod(environment.getServices(), containerTypeName, methodFullName);
        // Start proof
        ProofOblInput input = new ProgramMethodPO(environment.getInitConfig(), pm.getFullName(), pm,
            precondition, true, true);
        Proof proof = environment.createProof(input);
        assertNotNull(proof);
        // Set strategy and goal chooser to use for auto mode
        SymbolicExecutionEnvironment.configureProofForSymbolicExecution(proof,
            ExecutedSymbolicExecutionTreeNodesStopCondition.MAXIMAL_NUMBER_OF_SET_NODES_TO_EXECUTE_PER_GOAL_IN_COMPLETE_RUN,
            useOperationContracts, useLoopInvariants, blockTreatmentContract,
            nonExecutionBranchHidingSideProofs, aliasChecks);
        // Create symbolic execution tree which contains only the start node at beginning
        SymbolicExecutionTreeBuilder builder =
            new SymbolicExecutionTreeBuilder(proof, mergeBranchConditions, useUnicode,
                usePrettyPrinting, variablesAreOnlyComputedFromUpdates, simplifyConditions);
        SymbolicExecutionUtil.initializeStrategy(builder);
        builder.analyse();
        assertNotNull(builder.getStartNode());
        return new SymbolicExecutionEnvironment<>(environment, builder);
    }

    private static void setupTacletOptions(KeYEnvironment<?> env) {
        // Set Taclet options
        ImmutableSet<Choice> choices = env.getInitConfig().getActivatedChoices();
        choices = choices.add(new Choice("methodExpansion", "noRestriction"));

        ProofSettings settings = env.getInitConfig().getSettings();
        if (settings == null) {
            settings = ProofSettings.DEFAULT_SETTINGS;
        }
        settings.getChoiceSettings().updateWith(choices);
    }

    /**
     * Creates a {@link SymbolicExecutionEnvironment} which consists of loading a proof file to load
     * and creation with configuration of {@link SymbolicExecutionTreeBuilder}.
     *
     * @param baseDir The base directory which contains test and oracle file.
     * @param proofPathInBaseDir The path to the proof file inside the base directory.
     * @param mergeBranchConditions Merge branch conditions?
     * @param useOperationContracts Use operation contracts?
     * @param useLoopInvariants Use loop invariants?
     * @param blockTreatmentContract Block contracts or expand otherwise?
     * @param nonExecutionBranchHidingSideProofs {@code true} hide non execution branch labels by
     *        side proofs, {@code false} do not hide execution branch labels.
     * @param aliasChecks Do alias checks?
     * @param useUnicode {@code true} use unicode characters, {@code false} do not use unicode
     *        characters.
     * @param usePrettyPrinting {@code true} use pretty printing, {@code false} do not use pretty
     *        printing.
     * @param variablesAreOnlyComputedFromUpdates {@code true} {@link IExecutionVariable} are only
     *        computed from updates, {@code false} {@link IExecutionVariable}s are computed
     *        according to the type structure of the visible memory.
     * @param truthValueEvaluationEnabled {@code true} truth value evaluation is enabled,
     *        {@code false} truth value evaluation is disabled.
     * @param simplifyConditions {@code true} simplify conditions, {@code false} do not simplify
     *        conditions.
     * @return The created {@link SymbolicExecutionEnvironment}.
     * @throws ProblemLoaderException Occurred Exception.
     */
    protected static SymbolicExecutionEnvironment<DefaultUserInterfaceControl> createSymbolicExecutionEnvironment(
            Path baseDir, String proofPathInBaseDir, boolean mergeBranchConditions,
            boolean useOperationContracts, boolean useLoopInvariants,
            boolean blockTreatmentContract,
            boolean nonExecutionBranchHidingSideProofs, boolean aliasChecks, boolean useUnicode,
            boolean usePrettyPrinting, boolean variablesAreOnlyComputedFromUpdates,
            boolean truthValueEvaluationEnabled, boolean simplifyConditions)
            throws ProblemLoaderException {
        // Make sure that required files exists
        Path proofFile = baseDir.resolve(proofPathInBaseDir);
        assertTrue(Files.exists(proofFile));
        // Load java file
        KeYEnvironment<DefaultUserInterfaceControl> environment = KeYEnvironment.load(
            SymbolicExecutionJavaProfile.getDefaultInstance(truthValueEvaluationEnabled), proofFile,
            null, null, null, SymbolicExecutionTreeBuilder.createPoPropertiesToForce(), null, true);
        setupTacletOptions(environment);
        Proof proof = environment.getLoadedProof();
        assertNotNull(proof);
        // Set strategy and goal chooser to use for auto mode
        SymbolicExecutionEnvironment.configureProofForSymbolicExecution(proof,
            ExecutedSymbolicExecutionTreeNodesStopCondition.MAXIMAL_NUMBER_OF_SET_NODES_TO_EXECUTE_PER_GOAL_IN_COMPLETE_RUN,
            useOperationContracts, useLoopInvariants, blockTreatmentContract,
            nonExecutionBranchHidingSideProofs, aliasChecks);
        // Create symbolic execution tree which contains only the start node at beginning
        SymbolicExecutionTreeBuilder builder =
            new SymbolicExecutionTreeBuilder(proof, mergeBranchConditions, useUnicode,
                usePrettyPrinting, variablesAreOnlyComputedFromUpdates, simplifyConditions);
        SymbolicExecutionUtil.initializeStrategy(builder);
        builder.analyse();
        assertNotNull(builder.getStartNode());
        return new SymbolicExecutionEnvironment<>(environment, builder);
    }

    /**
     * Creates a {@link SymbolicExecutionEnvironment} which consists of loading a file to load,
     * finding the method to proof, instantiation of proof and creation with configuration of
     * {@link SymbolicExecutionTreeBuilder}.
     *
     * @param baseDir The base directory which contains test and oracle file.
     * @param javaPathInBaseDir The path to the java file inside the base directory.
     * @param containerTypeName The name of the type which contains the method.
     * @param methodFullName The method name to search.
     * @param precondition An optional precondition to use.
     * @param startPosition The start position.
     * @param endPosition The end position.
     * @param mergeBranchConditions Merge branch conditions?
     * @param useOperationContracts Use operation contracts?
     * @param useLoopInvariants Use loop invariants?
     * @param blockTreatmentContract Block contracts or expand otherwise?
     * @param nonExecutionBranchHidingSideProofs {@code true} hide non execution branch labels by
     *        side proofs, {@code false} do not hide execution branch labels.
     * @param aliasChecks Do alias checks?
     * @param useUnicode {@code true} use unicode characters, {@code false} do not use unicode
     *        characters.
     * @param usePrettyPrinting {@code true} use pretty printing, {@code false} do not use pretty
     *        printing.
     * @param variablesAreOnlyComputedFromUpdates {@code true} {@link IExecutionVariable} are only
     *        computed from updates, {@code false} {@link IExecutionVariable}s are computed
     *        according to the type structure of the visible memory.
     * @param simplifyConditions {@code true} simplify conditions, {@code false} do not simplify
     *        conditions.
     * @return The created {@link SymbolicExecutionEnvironment}.
     * @throws ProblemLoaderException Occurred Exception.
     * @throws ProofInputException Occurred Exception.
     */
    protected static SymbolicExecutionEnvironment<DefaultUserInterfaceControl> createSymbolicExecutionEnvironment(
            Path baseDir, String javaPathInBaseDir, String containerTypeName, String methodFullName,
            String precondition, Position startPosition, Position endPosition,
            boolean mergeBranchConditions, boolean useOperationContracts, boolean useLoopInvariants,
            boolean blockTreatmentContract, boolean nonExecutionBranchHidingSideProofs,
            boolean aliasChecks, boolean useUnicode, boolean usePrettyPrinting,
            boolean variablesAreOnlyComputedFromUpdates, boolean simplifyConditions)
            throws ProblemLoaderException, ProofInputException {
        // Make sure that required files exists
        Path javaFile = baseDir.resolve(javaPathInBaseDir);
        assertTrue(Files.exists(javaFile));
        // Load java file
        KeYEnvironment<DefaultUserInterfaceControl> environment = KeYEnvironment.load(
            SymbolicExecutionJavaProfile.getDefaultInstance(), javaFile, null, null, null, true);
        setupTacletOptions(environment);
        // Search method to proof
        IProgramMethod pm =
            searchProgramMethod(environment.getServices(), containerTypeName, methodFullName);
        // Start proof
        ProofOblInput input = new ProgramMethodSubsetPO(environment.getInitConfig(), methodFullName,
            pm, precondition, startPosition, endPosition, true, true);
        Proof proof = environment.createProof(input);
        assertNotNull(proof);
        // Set strategy and goal chooser to use for auto mode
        SymbolicExecutionEnvironment.configureProofForSymbolicExecution(proof,
            ExecutedSymbolicExecutionTreeNodesStopCondition.MAXIMAL_NUMBER_OF_SET_NODES_TO_EXECUTE_PER_GOAL_IN_COMPLETE_RUN,
            useOperationContracts, useLoopInvariants, blockTreatmentContract,
            nonExecutionBranchHidingSideProofs, aliasChecks);
        // Create symbolic execution tree which contains only the start node at beginning
        SymbolicExecutionTreeBuilder builder =
            new SymbolicExecutionTreeBuilder(proof, mergeBranchConditions, useUnicode,
                usePrettyPrinting, variablesAreOnlyComputedFromUpdates, simplifyConditions);
        SymbolicExecutionUtil.initializeStrategy(builder);
        builder.analyse();
        assertNotNull(builder.getStartNode());
        return new SymbolicExecutionEnvironment<>(environment, builder);
    }

    /**
     * Extracts the content of the try block from the initial {@link Sequent}.
     *
     * @param proof The {@link Proof} which contains the initial {@link Sequent}:
     * @return The try content.
     */
    protected String getTryContent(Proof proof) {
        assertNotNull(proof);
        Node node = proof.root();
        Sequent sequent = node.sequent();
        assertEquals(1, sequent.succedent().size());
        JTerm succedent = (JTerm) sequent.succedent().get(0).formula();
        assertEquals(2, succedent.arity());
        JTerm updateApplication = succedent.subs().get(1);
        assertEquals(2, updateApplication.arity());
        JavaProgramElement updateContent = updateApplication.subs().get(1).javaBlock().program();
        assertInstanceOf(StatementBlock.class, updateContent);
        ImmutableArray<? extends Statement> updateContentBody =
            ((StatementBlock) updateContent).getBody();
        assertEquals(2, updateContentBody.size());
        assertInstanceOf(Try.class, updateContentBody.get(1));
        Try tryStatement = (Try) updateContentBody.get(1);
        assertEquals(1, tryStatement.getBranchCount());
        return ProofSaver.printAnything(tryStatement.getBody(), proof.getServices());
    }

    /**
     * Makes sure that the save and loading process works.
     *
     * @param baseDir The base directory which contains test and oracle file.
     * @param javaPathInBaseDir The path to the java file inside the base directory.
     * @param oraclePathInBaseDirFile The oracle path.
     * @param env The already executed {@link SymbolicExecutionEnvironment} which contains the proof
     *        to save/load.
     * @throws IOException Occurred Exception
     * @throws ProofInputException Occurred Exception
     * @throws ParserConfigurationException Occurred Exception
     * @throws SAXException Occurred Exception
     * @throws ProblemLoaderException Occurred Exception
     */
    protected void assertSaveAndReload(Path baseDir, String javaPathInBaseDir,
            String oraclePathInBaseDirFile, SymbolicExecutionEnvironment<?> env)
            throws IOException, ProofInputException, ParserConfigurationException, SAXException,
            ProblemLoaderException {
        Path javaFile = baseDir.resolve(javaPathInBaseDir);
        assertTrue(Files.exists(javaFile));
        Path tempFile =
            Files.createTempFile(javaFile.getParent(), "TestProgramMethodSubsetPO", ".proof");
        KeYEnvironment<DefaultUserInterfaceControl> reloadedEnv = null;
        SymbolicExecutionTreeBuilder reloadedBuilder = null;
        try {
            ProofSaver saver = new ProofSaver(env.getProof(), tempFile.toAbsolutePath(),
                KeYConstants.INTERNAL_VERSION);
            assertNull(saver.save());
            // Load proof from saved *.proof file
            reloadedEnv = KeYEnvironment.load(SymbolicExecutionJavaProfile.getDefaultInstance(),
                tempFile, null, null, null, true);
            Proof reloadedProof = reloadedEnv.getLoadedProof();
            assertNotSame(env.getProof(), reloadedProof);
            // Recreate symbolic execution tree
            reloadedBuilder =
                new SymbolicExecutionTreeBuilder(reloadedProof, false, false, false, false, true);
            SymbolicExecutionUtil.initializeStrategy(reloadedBuilder);
            reloadedBuilder.analyse();
            assertSetTreeAfterStep(reloadedBuilder, oraclePathInBaseDirFile, baseDir);
        } finally {
            if (reloadedBuilder != null) {
                reloadedBuilder.dispose();
            }
            if (reloadedEnv != null) {
                reloadedEnv.dispose();
            }
            Files.deleteIfExists(tempFile);
        }
    }

    /**
     * Executes a test with the following steps:
     * <ol>
     * <li>Load java file</li>
     * <li>Instantiate proof for method in container type</li>
     * <li>Try to close proof in auto mode</li>
     * <li>Create symbolic execution tree</li>
     * <li>Create new oracle file in temporary directory {@link #tempNewOracleDirectory} if it is
     * defined</li>
     * <li>Load oracle file</li>
     * <li>Compare created symbolic execution tree with oracle model</li>
     * </ol>
     *
     * @param baseDir The base directory which contains test and oracle file.
     * @param javaPathInBaseDir The path to the java file inside the base directory.
     * @param containerTypeName The java class to test.
     * @param methodFullName The method to test.
     * @param precondition An optional precondition.
     * @param oraclePathInBaseDirFile The path to the oracle file inside the base directory.
     * @param includeConstraints Include constraints?
     * @param includeVariables Include variables?
     * @param includeCallStack Include call stack?
     * @param includeReturnValues Include method return values?
     * @param maximalNumberOfExecutedSetNodesPerRun The number of executed set nodes per auto mode
     *        run. The whole test is executed for each defined value.
     * @param mergeBranchConditions Merge branch conditions?
     * @param useOperationContracts Use operation contracts?
     * @param useLoopInvariants Use loop invariants?
     * @param blockTreatmentContract Block contracts or expand otherwise?
     * @param nonExecutionBranchHidingSideProofs {@code true} hide non execution branch labels by
     *        side proofs, {@code false} do not hide execution branch labels.
     * @param aliasChecks Do alias checks?
     * @param useUnicode {@code true} use unicode characters, {@code false} do not use unicode
     *        characters.
     * @param usePrettyPrinting {@code true} use pretty printing, {@code false} do not use pretty
     *        printing.
     * @param variablesAreOnlyComputedFromUpdates {@code true} {@link IExecutionVariable} are only
     *        computed from updates, {@code false} {@link IExecutionVariable}s are computed
     *        according to the type structure of the visible memory.
     * @param simplifyConditions {@code true} simplify conditions, {@code false} do not simplify
     *        conditions.
     * @throws ProofInputException Occurred Exception
     * @throws IOException Occurred Exception
     * @throws ParserConfigurationException Occurred Exception
     * @throws SAXException Occurred Exception
     * @throws ProblemLoaderException Occurred Exception
     */
    protected void doSETTest(Path baseDir, String javaPathInBaseDir, String containerTypeName,
            String methodFullName, String precondition, String oraclePathInBaseDirFile,
            boolean includeConstraints, boolean includeVariables, boolean includeCallStack,
            boolean includeReturnValues, int[] maximalNumberOfExecutedSetNodesPerRun,
            boolean mergeBranchConditions, boolean useOperationContracts, boolean useLoopInvariants,
            boolean blockTreatmentContract, boolean nonExecutionBranchHidingSideProofs,
            boolean aliasChecks, boolean useUnicode, boolean usePrettyPrinting,
            boolean variablesAreOnlyComputedFromUpdates, boolean simplifyConditions)
            throws ProofInputException, IOException, ParserConfigurationException, SAXException,
            ProblemLoaderException {
        assertNotNull(maximalNumberOfExecutedSetNodesPerRun);
        for (int j : maximalNumberOfExecutedSetNodesPerRun) {
            SymbolicExecutionEnvironment<DefaultUserInterfaceControl> env = doSETTest(baseDir,
                javaPathInBaseDir, containerTypeName, methodFullName, precondition,
                oraclePathInBaseDirFile, includeConstraints, includeVariables, includeCallStack,
                includeReturnValues, j,
                mergeBranchConditions, useOperationContracts, useLoopInvariants,
                blockTreatmentContract, nonExecutionBranchHidingSideProofs, aliasChecks, useUnicode,
                usePrettyPrinting, variablesAreOnlyComputedFromUpdates, simplifyConditions);
            env.dispose();
        }
    }

    /**
     * Executes method <code>doTest</code>
     * and disposes the created {@link SymbolicExecutionEnvironment}.
     *
     * @param baseDir The base directory which contains test and oracle file.
     * @param javaPathInBaseDir The path to the java file inside the base directory.
     * @param containerTypeName The java class to test.
     * @param methodFullName The method to test.
     * @param precondition An optional precondition.
     * @param oraclePathInBaseDirFile The path to the oracle file inside the base directory.
     * @param includeConstraints Include constraints?
     * @param includeVariables Include variables?
     * @param includeCallStack Include call stack?
     * @param includeReturnValues Include method return values?
     * @param maximalNumberOfExecutedSetNodes The number of executed set nodes per auto mode run.
     * @param mergeBranchConditions Merge branch conditions?
     * @param useOperationContracts Use operation contracts?
     * @param useLoopInvariants Use loop invariants?
     * @param blockTreatmentContract Block contracts or expand otherwise?
     * @param nonExecutionBranchHidingSideProofs {@code true} hide non execution branch labels by
     *        side proofs, {@code false} do not hide execution branch labels.
     * @param aliasChecks Do alias checks?
     * @param useUnicode {@code true} use unicode characters, {@code false} do not use unicode
     *        characters.
     * @param usePrettyPrinting {@code true} use pretty printing, {@code false} do not use pretty
     *        printing.
     * @param variablesAreOnlyComputedFromUpdates {@code true} {@link IExecutionVariable} are only
     *        computed from updates, {@code false} {@link IExecutionVariable}s are computed
     *        according to the type structure of the visible memory.
     * @param simplifyConditions {@code true} simplify conditions, {@code false} do not simplify
     *        conditions.
     * @throws ProofInputException Occurred Exception
     * @throws IOException Occurred Exception
     * @throws ParserConfigurationException Occurred Exception
     * @throws SAXException Occurred Exception
     * @throws ProblemLoaderException Occurred Exception
     */
    protected void doSETTestAndDispose(Path baseDir, String javaPathInBaseDir,
            String containerTypeName, String methodFullName, String precondition,
            String oraclePathInBaseDirFile, boolean includeConstraints, boolean includeVariables,
            boolean includeCallStack, boolean includeReturnValues,
            int maximalNumberOfExecutedSetNodes, boolean mergeBranchConditions,
            boolean useOperationContracts, boolean useLoopInvariants,
            boolean blockTreatmentContract, boolean nonExecutionBranchHidingSideProofs,
            boolean aliasChecks, boolean useUnicode, boolean usePrettyPrinting,
            boolean variablesAreOnlyComputedFromUpdates, boolean simplifyConditions)
            throws ProofInputException, IOException, ParserConfigurationException, SAXException,
            ProblemLoaderException {
        SymbolicExecutionEnvironment<DefaultUserInterfaceControl> env =
            doSETTest(baseDir, javaPathInBaseDir, containerTypeName, methodFullName, precondition,
                oraclePathInBaseDirFile, includeConstraints, includeVariables, includeCallStack,
                includeReturnValues, maximalNumberOfExecutedSetNodes, mergeBranchConditions,
                useOperationContracts, useLoopInvariants, blockTreatmentContract,
                nonExecutionBranchHidingSideProofs, aliasChecks, useUnicode, usePrettyPrinting,
                variablesAreOnlyComputedFromUpdates, simplifyConditions);
        env.dispose();
    }

    /**
     * Executes a test with the following steps:
     * <ol>
     * <li>Load java file</li>
     * <li>Instantiate proof for method in container type</li>
     * <li>Try to close proof in auto mode</li>
     * <li>Create symbolic execution tree</li>
     * <li>Create new oracle file in temporary directory {@link #tempNewOracleDirectory} if it is
     * defined</li>
     * <li>Load oracle file</li>
     * <li>Compare created symbolic execution tree with oracle model</li>
     * </ol>
     *
     * @param baseDir The base directory which contains test and oracle file.
     * @param proofFilePathInBaseDir The path to the proof file inside the base directory.
     * @param oraclePathInBaseDirFile The path to the oracle file inside the base directory.
     * @param includeConstraints Include constraints?
     * @param includeVariables Include variables?
     * @param includeCallStack Include call stack?
     * @param includeReturnValues Include method return values?
     * @param mergeBranchConditions Merge branch conditions?
     * @param useOperationContracts Use operation contracts?
     * @param useLoopInvariants Use loop invariants?
     * @param blockTreatmentContract Block contracts or expand otherwise?
     * @param nonExecutionBranchHidingSideProofs {@code true} hide non execution branch labels by
     *        side proofs, {@code false} do not hide execution branch labels.
     * @param aliasChecks Do alias checks?
     * @param useUnicode {@code true} use unicode characters, {@code false} do not use unicode
     *        characters.
     * @param usePrettyPrinting {@code true} use pretty printing, {@code false} do not use pretty
     *        printing.
     * @param variablesAreOnlyComputedFromUpdates {@code true} {@link IExecutionVariable} are only
     *        computed from updates, {@code false} {@link IExecutionVariable}s are computed
     *        according to the type structure of the visible memory.
     * @throws ProofInputException Occurred Exception
     * @throws IOException Occurred Exception
     * @throws ParserConfigurationException Occurred Exception
     * @throws SAXException Occurred Exception
     * @throws ProblemLoaderException Occurred Exception
     */
    protected void doSETTestAndDispose(Path baseDir, String proofFilePathInBaseDir,
            String oraclePathInBaseDirFile, boolean includeConstraints, boolean includeVariables,
            boolean includeCallStack, boolean includeReturnValues, boolean mergeBranchConditions,
            boolean useOperationContracts, boolean useLoopInvariants,
            boolean blockTreatmentContract, boolean nonExecutionBranchHidingSideProofs,
            boolean aliasChecks, boolean useUnicode, boolean usePrettyPrinting,
            boolean variablesAreOnlyComputedFromUpdates) throws ProofInputException, IOException,
            ParserConfigurationException, SAXException, ProblemLoaderException {
        SymbolicExecutionEnvironment<DefaultUserInterfaceControl> env =
            doSETTest(baseDir, proofFilePathInBaseDir, oraclePathInBaseDirFile, includeConstraints,
                includeVariables, includeCallStack, includeReturnValues, mergeBranchConditions,
                useOperationContracts, useLoopInvariants, blockTreatmentContract,
                nonExecutionBranchHidingSideProofs, aliasChecks, useUnicode, usePrettyPrinting,
                variablesAreOnlyComputedFromUpdates, false, true);
        if (env != null) {
            env.dispose();
        }
    }

    /**
     * Executes a test with the following steps:
     * <ol>
     * <li>Load java file</li>
     * <li>Instantiate proof for method in container type</li>
     * <li>Try to close proof in auto mode</li>
     * <li>Create symbolic execution tree</li>
     * <li>Create new oracle file in temporary directory {@link #tempNewOracleDirectory} if it is
     * defined</li>
     * <li>Load oracle file</li>
     * <li>Compare created symbolic execution tree with oracle model</li>
     * </ol>
     *
     * @param baseDir The base directory which contains test and oracle file.
     * @param proofFilePathInBaseDir The path to the proof file inside the base directory.
     * @param oraclePathInBaseDirFile The path to the oracle file inside the base directory.
     * @param includeConstraints Include constraints?
     * @param includeVariables Include variables?
     * @param includeCallStack Include call stack?
     * @param includeReturnValues Include method return values?
     * @param mergeBranchConditions Merge branch conditions?
     * @param useOperationContracts Use operation contracts?
     * @param useLoopInvariants Use loop invariants?
     * @param blockTreatmentContract Block contracts or expand otherwise?
     * @param nonExecutionBranchHidingSideProofs {@code true} hide non execution branch labels by
     *        side proofs, {@code false} do not hide execution branch labels.
     * @param aliasChecks Do alias checks?
     * @param useUnicode {@code true} use unicode characters, {@code false} do not use unicode
     *        characters.
     * @param usePrettyPrinting {@code true} use pretty printing, {@code false} do not use pretty
     *        printing.
     * @param variablesAreOnlyComputedFromUpdates {@code true} {@link IExecutionVariable} are only
     *        computed from updates, {@code false} {@link IExecutionVariable}s are computed
     *        according to the type structure of the visible memory.
     * @param truthValueEvaluationEnabled {@code true} truth value evaluation is enabled,
     *        {@code false} truth value evaluation is disabled.
     * @param simplifyConditions {@code true} simplify conditions, {@code false} do not simplify
     *        conditions.
     * @return The tested {@link SymbolicExecutionEnvironment}.
     * @throws ProofInputException Occurred Exception
     * @throws IOException Occurred Exception
     * @throws ParserConfigurationException Occurred Exception
     * @throws SAXException Occurred Exception
     * @throws ProblemLoaderException Occurred Exception
     */
    protected SymbolicExecutionEnvironment<DefaultUserInterfaceControl> doSETTest(Path baseDir,
            String proofFilePathInBaseDir, String oraclePathInBaseDirFile,
            boolean includeConstraints, boolean includeVariables, boolean includeCallStack,
            boolean includeReturnValues, boolean mergeBranchConditions,
            boolean useOperationContracts, boolean useLoopInvariants,
            boolean blockTreatmentContract, boolean nonExecutionBranchHidingSideProofs,
            boolean aliasChecks, boolean useUnicode, boolean usePrettyPrinting,
            boolean variablesAreOnlyComputedFromUpdates, boolean truthValueEvaluationEnabled,
            boolean simplifyConditions) throws ProofInputException, IOException,
            ParserConfigurationException, SAXException, ProblemLoaderException {
        boolean originalOneStepSimplification = isOneStepSimplificationEnabled(null);
        SymbolicExecutionEnvironment<DefaultUserInterfaceControl> env;
        try {
            // Make sure that the parameters are valid.
            assertNotNull(proofFilePathInBaseDir);
            assertNotNull(oraclePathInBaseDirFile);
            Path oracleFile = baseDir.resolve(oraclePathInBaseDirFile);
            if (!CREATE_NEW_ORACLE_FILES_IN_TEMP_DIRECTORY) {
                assertTrue(Files.exists(oracleFile),
                    "Oracle file does not exist. Set \"CREATE_NEW_ORACLE_FILES_IN_TEMP_DIRECTORY\" to true to create an oracle file.");
            }
            // Make sure that the correct taclet options are defined.
            setOneStepSimplificationEnabled(null, true);
            // Create proof environment for symbolic execution
            env = createSymbolicExecutionEnvironment(baseDir, proofFilePathInBaseDir,
                mergeBranchConditions, useOperationContracts, useLoopInvariants,
                blockTreatmentContract, nonExecutionBranchHidingSideProofs, aliasChecks, useUnicode,
                usePrettyPrinting, variablesAreOnlyComputedFromUpdates, truthValueEvaluationEnabled,
                simplifyConditions);
            // Create new oracle file if required in a temporary directory
            createOracleFile(env.getBuilder().getStartNode(), oraclePathInBaseDirFile,
                includeConstraints, includeVariables, includeCallStack, includeReturnValues);
            // Read oracle file
            ExecutionNodeReader reader = new ExecutionNodeReader();
            IExecutionNode<?> oracleRoot = reader.read(oracleFile.toFile());
            assertNotNull(oracleRoot);
            // Make sure that the created symbolic execution tree matches the expected one.
            assertExecutionNodes(oracleRoot, env.getBuilder().getStartNode(), includeVariables,
                includeCallStack, false, includeReturnValues, includeConstraints);
            return env;
        } finally {
            // Restore original options
            setOneStepSimplificationEnabled(null, originalOneStepSimplification);
        }
    }

    /**
     * Executes a test with the following steps:
     * <ol>
     * <li>Load java file</li>
     * <li>Instantiate proof for method in container type</li>
     * <li>Try to close proof in auto mode</li>
     * <li>Create symbolic execution tree</li>
     * <li>Create new oracle file in temporary directory {@link #tempNewOracleDirectory} if it is
     * defined</li>
     * <li>Load oracle file</li>
     * <li>Compare created symbolic execution tree with oracle model</li>
     * </ol>
     *
     * @param baseDir The base directory which contains test and oracle file.
     * @param javaPathInBaseDir The path to the java file inside the base directory.
     * @param containerTypeName The java class to test.
     * @param methodFullName The method to test.
     * @param precondition An optional precondition.
     * @param oraclePathInBaseDirFile The path to the oracle file inside the base directory.
     * @param includeConstraints Include constraints?
     * @param includeVariables Include variables?
     * @param includeCallStack Include call stack?
     * @param includeReturnValues Include method return values?
     * @param maximalNumberOfExecutedSetNodes The number of executed set nodes per auto mode run.
     * @param mergeBranchConditions Merge branch conditions?
     * @param useOperationContracts Use operation contracts?
     * @param useLoopInvariants Use loop invariants?
     * @param blockTreatmentContract Block contracts or expand otherwise?
     * @param nonExecutionBranchHidingSideProofs {@code true} hide non execution branch labels by
     *        side proofs, {@code false} do not hide execution branch labels.
     * @param aliasChecks Do alias checks?
     * @param useUnicode {@code true} use unicode characters, {@code false} do not use unicode
     *        characters.
     * @param usePrettyPrinting {@code true} use pretty printing, {@code false} do not use pretty
     *        printing.
     * @param variablesAreOnlyComputedFromUpdates {@code true} {@link IExecutionVariable} are only
     *        computed from updates, {@code false} {@link IExecutionVariable}s are computed
     *        according to the type structure of the visible memory.
     * @param simplifyConditions {@code true} simplify conditions, {@code false} do not simplify
     *        conditions.
     * @return The tested {@link SymbolicExecutionEnvironment}.
     * @throws ProofInputException Occurred Exception
     * @throws IOException Occurred Exception
     * @throws ParserConfigurationException Occurred Exception
     * @throws SAXException Occurred Exception
     * @throws ProblemLoaderException Occurred Exception
     */
    protected SymbolicExecutionEnvironment<DefaultUserInterfaceControl> doSETTest(Path baseDir,
            String javaPathInBaseDir, String containerTypeName, final String methodFullName,
            String precondition, String oraclePathInBaseDirFile, boolean includeConstraints,
            boolean includeVariables, boolean includeCallStack, boolean includeReturnValues,
            int maximalNumberOfExecutedSetNodes, boolean mergeBranchConditions,
            boolean useOperationContracts, boolean useLoopInvariants,
            boolean blockTreatmentContract, boolean nonExecutionBranchHidingSideProofs,
            boolean aliasChecks, boolean useUnicode, boolean usePrettyPrinting,
            boolean variablesAreOnlyComputedFromUpdates, boolean simplifyConditions)
            throws ProofInputException, IOException, ParserConfigurationException, SAXException,
            ProblemLoaderException {
        Map<String, String> originalTacletOptions = null;
        boolean originalOneStepSimplification = isOneStepSimplificationEnabled(null);
        try {
            // Make sure that the parameters are valid.
            assertNotNull(javaPathInBaseDir);
            assertNotNull(containerTypeName);
            assertNotNull(methodFullName);
            assertNotNull(oraclePathInBaseDirFile);
            var oracleFile = baseDir.resolve(oraclePathInBaseDirFile);
            if (!CREATE_NEW_ORACLE_FILES_IN_TEMP_DIRECTORY) {
                assertTrue(Files.exists(oracleFile),
                    "Oracle file does not exist. Set \"CREATE_NEW_ORACLE_FILES_IN_TEMP_DIRECTORY\" to true to create an oracle file.");
            }
            assertTrue(maximalNumberOfExecutedSetNodes >= 1);
            // Make sure that the correct taclet options are defined.
            originalTacletOptions = setDefaultTacletOptions(baseDir, javaPathInBaseDir,
                containerTypeName, methodFullName);
            setOneStepSimplificationEnabled(null, true);
            // Create proof environment for symbolic execution
            SymbolicExecutionEnvironment<DefaultUserInterfaceControl> env =
                createSymbolicExecutionEnvironment(baseDir, javaPathInBaseDir, containerTypeName,
                    methodFullName, precondition, mergeBranchConditions, useOperationContracts,
                    useLoopInvariants, blockTreatmentContract, nonExecutionBranchHidingSideProofs,
                    aliasChecks, useUnicode, usePrettyPrinting, variablesAreOnlyComputedFromUpdates,
                    simplifyConditions);
            internalDoSETTest(oracleFile, env, oraclePathInBaseDirFile,
                maximalNumberOfExecutedSetNodes, includeConstraints, includeVariables,
                includeCallStack, includeReturnValues);
            return env;
        } finally {
            // Restore original options
            setOneStepSimplificationEnabled(null, originalOneStepSimplification);
            restoreTacletOptions(originalTacletOptions);
        }
    }

    /**
     * Executes a test with the following steps:
     * <ol>
     * <li>Load java file</li>
     * <li>Instantiate proof for method in container type</li>
     * <li>Try to close proof in auto mode</li>
     * <li>Create symbolic execution tree</li>
     * <li>Create new oracle file in temporary directory {@link #tempNewOracleDirectory} if it is
     * defined</li>
     * <li>Load oracle file</li>
     * <li>Compare created symbolic execution tree with oracle model</li>
     * </ol>
     *
     * @param baseDir The base directory which contains test and oracle file.
     * @param javaPathInBaseDir The path to the java file inside the base directory.
     * @param baseContractName The name of the contract.
     * @param oraclePathInBaseDirFile The path to the oracle file inside the base directory.
     * @param includeConstraints Include constraints?
     * @param includeVariables Include variables?
     * @param includeCallStack Include call stack?
     * @param includeReturnValues Include method return values?
     * @param maximalNumberOfExecutedSetNodes The number of executed set nodes per auto mode run.
     * @param mergeBranchConditions Merge branch conditions?
     * @param useOperationContracts Use operation contracts?
     * @param useLoopInvariants Use loop invariants?
     * @param blockTreatmentContract Block contracts or expand otherwise?
     * @param nonExecutionBranchHidingSideProofs {@code true} hide non execution branch labels by
     *        side proofs, {@code false} do not hide execution branch labels.
     * @param aliasChecks Do alias checks?
     * @param useUnicode {@code true} use unicode characters, {@code false} do not use unicode
     *        characters.
     * @param usePrettyPrinting {@code true} use pretty printing, {@code false} do not use pretty
     *        printing.
     * @param variablesAreOnlyComputedFromUpdates {@code true} {@link IExecutionVariable} are only
     *        computed from updates, {@code false} {@link IExecutionVariable}s are computed
     *        according to the type structure of the visible memory.
     * @param truthValueEvaluationEnabled {@code true} truth value evaluation is enabled,
     *        {@code false} truth value evaluation is disabled.
     * @param simplifyConditions {@code true} simplify conditions, {@code false} do not simplify
     *        conditions.
     * @return The tested {@link SymbolicExecutionEnvironment}.
     * @throws ProofInputException Occurred Exception
     * @throws IOException Occurred Exception
     * @throws ParserConfigurationException Occurred Exception
     * @throws SAXException Occurred Exception
     * @throws ProblemLoaderException Occurred Exception
     */
    protected SymbolicExecutionEnvironment<DefaultUserInterfaceControl> doSETTest(Path baseDir,
            String javaPathInBaseDir, String baseContractName, String oraclePathInBaseDirFile,
            boolean includeConstraints, boolean includeVariables, boolean includeCallStack,
            boolean includeReturnValues, int maximalNumberOfExecutedSetNodes,
            boolean mergeBranchConditions, boolean useOperationContracts, boolean useLoopInvariants,
            boolean blockTreatmentContract, boolean nonExecutionBranchHidingSideProofs,
            boolean aliasChecks, boolean useUnicode, boolean usePrettyPrinting,
            boolean variablesAreOnlyComputedFromUpdates, boolean truthValueEvaluationEnabled,
            boolean simplifyConditions) throws ProofInputException, IOException,
            ParserConfigurationException, SAXException, ProblemLoaderException {
        Map<String, String> originalTacletOptions = null;
        try {
            // Make sure that the parameters are valid.
            assertNotNull(javaPathInBaseDir);
            assertNotNull(baseContractName);
            assertNotNull(oraclePathInBaseDirFile);
            var oracleFile = baseDir.resolve(oraclePathInBaseDirFile);
            if (!CREATE_NEW_ORACLE_FILES_IN_TEMP_DIRECTORY) {
                assertTrue(Files.exists(oracleFile),
                    "Oracle file does not exist. Set \"CREATE_NEW_ORACLE_FILES_IN_TEMP_DIRECTORY\" to true to create an oracle file.");
            }
            assertTrue(maximalNumberOfExecutedSetNodes >= 1);
            // Make sure that the correct taclet options are defined.
            originalTacletOptions =
                setDefaultTacletOptions(javaPathInBaseDir, baseContractName);
            // Create proof environment for symbolic execution
            SymbolicExecutionEnvironment<DefaultUserInterfaceControl> env =
                createSymbolicExecutionEnvironment(baseDir, javaPathInBaseDir, baseContractName,
                    mergeBranchConditions, useOperationContracts, useLoopInvariants,
                    blockTreatmentContract, nonExecutionBranchHidingSideProofs, aliasChecks,
                    useUnicode, usePrettyPrinting, variablesAreOnlyComputedFromUpdates,
                    truthValueEvaluationEnabled, simplifyConditions);
            internalDoSETTest(oracleFile, env, oraclePathInBaseDirFile,
                maximalNumberOfExecutedSetNodes, includeConstraints, includeVariables,
                includeCallStack, includeReturnValues);
            return env;
        } finally {
            // Restore taclet options
            restoreTacletOptions(originalTacletOptions);
        }
    }

    /**
     * Internal test method
     */
    private void internalDoSETTest(Path oracleFile,
            SymbolicExecutionEnvironment<DefaultUserInterfaceControl> env,
            String oraclePathInBaseDirFile, int maximalNumberOfExecutedSetNodes,
            boolean includeConstraints, boolean includeVariables, boolean includeCallStack,
            boolean includeReturnValues)
            throws IOException, ProofInputException, ParserConfigurationException, SAXException {
        // Set stop condition to stop after a number of detected symbolic execution tree nodes
        // instead of applied rules
        ExecutedSymbolicExecutionTreeNodesStopCondition stopCondition =
            new ExecutedSymbolicExecutionTreeNodesStopCondition(maximalNumberOfExecutedSetNodes);
        env.getProof().getSettings().getStrategySettings()
                .setCustomApplyStrategyStopCondition(stopCondition);
        int nodeCount;
        // Execute auto mode until no more symbolic execution tree nodes are found or no new rules
        // are applied.
        do {
            // Store the number of nodes before start of the auto mode
            nodeCount = env.getProof().countNodes();
            // Run proof
            env.getProofControl().startAndWaitForAutoMode(env.getProof());
            // Update symbolic execution tree
            env.getBuilder().analyse();
            // Make sure that not to many set nodes are executed
            Map<Goal, Integer> executedSetNodesPerGoal = stopCondition.getExectuedSetNodesPerGoal();
            for (Integer value : executedSetNodesPerGoal.values()) {
                assertNotNull(value);
                assertTrue(value <= maximalNumberOfExecutedSetNodes,
                    value + " is not less equal to " + maximalNumberOfExecutedSetNodes);
            }
        } while (stopCondition.wasSetNodeExecuted() && nodeCount != env.getProof().countNodes());
        // Create new oracle file if required in a temporary directory
        createOracleFile(env.getBuilder().getStartNode(), oraclePathInBaseDirFile,
            includeConstraints, includeVariables, includeCallStack, includeReturnValues);
        // Read oracle file
        ExecutionNodeReader reader = new ExecutionNodeReader();
        IExecutionNode<?> oracleRoot = reader.read(oracleFile.toFile());
        assertNotNull(oracleRoot);
        // Make sure that the created symbolic execution tree matches the expected one.
        assertExecutionNodes(oracleRoot, env.getBuilder().getStartNode(), includeVariables,
            includeCallStack, false, includeReturnValues, includeConstraints);
    }

    /**
     * Ensures that the default taclet options are defined.
     *
     * @param javaPathInBaseDir The path in the base directory to the java file.
     * @param baseContractName The name of the contract to prove.
     * @return The original settings which are overwritten.
     * @throws ProblemLoaderException Occurred Exception.
     * @throws ProofInputException Occurred Exception.
     */
    public static Map<String, String> setDefaultTacletOptions(String javaPathInBaseDir,
            String baseContractName)
            throws ProblemLoaderException, ProofInputException {
        if (!SymbolicExecutionUtil.isChoiceSettingInitialised()) {
            SymbolicExecutionEnvironment<DefaultUserInterfaceControl> env =
                createSymbolicExecutionEnvironment(testCaseDirectory, javaPathInBaseDir,
                    baseContractName,
                    false, false, false,
                    false, false, false,
                    false, false, false,
                    false, false);
            env.dispose();
        }
        return setDefaultTacletOptions();
    }

    /**
     * Ensures that the default taclet options are defined.
     *
     * @param baseDir The base directory which contains the java file.
     * @param javaPathInBaseDir The path in the base directory to the java file.
     * @param containerTypeName name of the type where the method is implemented/declared
     * @param methodFullName The method to prove.
     * @return The original settings which are overwritten.
     * @throws ProblemLoaderException Occurred Exception.
     * @throws ProofInputException Occurred Exception.
     */
    public static Map<String, String> setDefaultTacletOptions(Path baseDir,
            String javaPathInBaseDir,
            String containerTypeName,
            String methodFullName)
            throws ProblemLoaderException, ProofInputException {
        if (!SymbolicExecutionUtil.isChoiceSettingInitialised()) {
            SymbolicExecutionEnvironment<DefaultUserInterfaceControl> env =
                createSymbolicExecutionEnvironment(baseDir, javaPathInBaseDir, containerTypeName,
                    methodFullName,
                    null, false, false, false,
                    false, false, false, false,
                    false, false, false);
            env.dispose();
        }
        return setDefaultTacletOptions();
    }

    /**
     * Ensures that the default taclet options are defined.
     *
     * @param javaFile The java file to load.
     * @param containerTypeName The type name which provides the target.
     * @param targetName The target to proof.
     * @return The original settings which are overwritten.
     * @throws ProblemLoaderException Occurred Exception.
     * @throws ProofInputException Occurred Exception.
     */
    @SuppressWarnings("unused")
    public static Map<String, String> setDefaultTacletOptionsForTarget(Path javaFile,
            String containerTypeName, final String targetName)
            throws ProblemLoaderException, ProofInputException {
        return HelperClassForTests.setDefaultTacletOptionsForTarget(javaFile, containerTypeName,
            targetName);
    }

    /**
     * Ensures that the default taclet options are defined.
     *
     * @return The original settings which are overwritten.
     */
    public static Map<String, String> setDefaultTacletOptions() {
        Map<String, String> original = HelperClassForTests.setDefaultTacletOptions();
        ChoiceSettings choiceSettings = ProofSettings.DEFAULT_SETTINGS.getChoiceSettings();
        ImmutableSet<Choice> cs = DefaultImmutableSet.nil();
        cs = cs.add(new Choice("noRestriction", "methodExpansion"));
        choiceSettings.updateWith(cs);
        return original;
    }

    /**
     * Restores the given taclet options.
     *
     * @param options The taclet options to restore.
     */
    public static void restoreTacletOptions(Map<String, String> options) {
        HelperClassForTests.restoreTacletOptions(options);
    }

    /**
     * creates a new factory that should be used by others afterward
     *
     * @return collector factory for program variables
     */
    protected ITermProgramVariableCollectorFactory createNewProgramVariableCollectorFactory(
            final SymbolicExecutionBreakpointStopCondition breakpointParentStopCondition) {
        return services -> new TermProgramVariableCollectorKeepUpdatesForBreakpointconditions(
            services, breakpointParentStopCondition);
    }

    /**
     * Makes sure that two {@link JTerm}s are equal.
     *
     * @param expected The expected {@link JTerm}.
     * @param actual The actual {@link JTerm}.
     */
    protected void assertTerm(JTerm expected, JTerm actual) {
        if (expected != null) {
            assertEquals(expected.op(), actual.op());
            assertEquals(expected.javaBlock(), actual.javaBlock());
            assertEquals(expected.getLabels(), actual.getLabels());
            assertEquals(expected.arity(), actual.arity());
            for (int i = 0; i < expected.arity(); i++) {
                assertTerm(expected.sub(i), actual.sub(i));
            }
        } else {
            assertNull(actual);
        }
    }

    /**
     * Checks if one-step simplification is enabled in the given {@link Proof}.
     *
     * @param proof The {@link Proof} to read from or {@code null} to return the general settings
     *        value.
     * @return {@code true} one step simplification is enabled, {@code false} if disabled.
     */
    public static boolean isOneStepSimplificationEnabled(Proof proof) {
        return HelperClassForTests.isOneStepSimplificationEnabled(proof);
    }

    /**
     * Defines if one-step simplification is enabled in general and within the {@link Proof}.
     *
     * @param proof The optional {@link Proof}.
     * @param enabled {@code true} use one-step simplification, {@code false} do not use one-step
     *        simplification.
     */
    public static void setOneStepSimplificationEnabled(Proof proof, boolean enabled) {
        HelperClassForTests.setOneStepSimplificationEnabled(proof, enabled);
    }
}
