/* This file is part of KeY - https://key-project.org
 * KeY is licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package de.uka.ilkd.key.util.mergerule;

import de.uka.ilkd.key.java.Services;
import de.uka.ilkd.key.logic.JTerm;
import de.uka.ilkd.key.pp.LogicPrinter;
import de.uka.ilkd.key.proof.Node;

import org.key_project.util.collection.Pair;

/**
 * A symbolic execution state is a pair of a symbolic state in form of a parallel update, and a path
 * condition in form of a JavaDL formula.
 *
 * @author Dominic Scheurer
 */
public class SymbolicExecutionState extends Pair<JTerm, JTerm> {

    private Node correspondingNode = null;

    /**
     * @param symbolicState The symbolic state (parallel update).
     * @param pathCondition The path condition (formula).
     */
    public SymbolicExecutionState(JTerm symbolicState, JTerm pathCondition) {
        super(symbolicState, pathCondition);
    }

    /**
     * @param symbolicState The symbolic state (parallel update).
     * @param pathCondition The path condition (formula).
     * @param correspondingNode The node corresponding to this SE state.
     */
    public SymbolicExecutionState(JTerm symbolicState, JTerm pathCondition,
            Node correspondingNode) {
        this(symbolicState, pathCondition);
        this.correspondingNode = correspondingNode;
    }

    /**
     * @return The symbolic state.
     */
    public JTerm getSymbolicState() {
        return first;
    }

    /**
     * @return The path condition.
     */
    public JTerm getPathCondition() {
        return second;
    }

    /**
     * @return The node corresponding to this SE state.
     */
    public Node getCorrespondingNode() {
        return correspondingNode;
    }

    /**
     * @param correspondingNode The node corresponding to this SE state.
     */
    public void setCorrespondingNode(Node correspondingNode) {
        this.correspondingNode = correspondingNode;
    }

    @Override
    public String toString() {
        final Services services = getCorrespondingNode().proof().getServices();

        return "SymbolicExecutionStateWithProgCnt [Symbolic State=("
            + rmN(LogicPrinter.quickPrintTerm(getSymbolicState(), services)) + "), Path Condition=("
            + rmN(LogicPrinter.quickPrintTerm(getPathCondition(), services)) + ")]";
    }

    /**
     * Removes a trailing newline (\n) char from the given string.
     *
     * @param str The string to remove the newline char from.
     * @return The given string with the removed trailing \n char, or the original string if it does
     *         not end with an \n.
     */
    private String rmN(String str) {
        if (str.endsWith("\n") && str.length() > 1) {
            return str.substring(0, str.length() - 1);
        }

        return str;
    }

}
