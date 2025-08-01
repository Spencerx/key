/* This file is part of KeY - https://key-project.org
 * KeY is licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package de.uka.ilkd.key.util.mergerule;

import de.uka.ilkd.key.java.Services;
import de.uka.ilkd.key.logic.JTerm;
import de.uka.ilkd.key.pp.LogicPrinter;
import de.uka.ilkd.key.proof.Node;

import org.jspecify.annotations.Nullable;

/**
 * A symbolic execution state with program counter is a triple of a symbolic state in form of a
 * parallel update, a path condition in form of a JavaDL formula, and a program counter in form of a
 * JavaDL formula with non-empty Java Block (and a possible post condition as first, and only, sub
 * term).
 *
 * @param symbolicState The symbolic state (parallel update).
 * @param pathCondition The path condition (formula).
 * @param programCounter The program counter: Formula with non-empty Java block and post
 *        condition as only sub term.
 * @param correspondingNode The node corresponding to this SE state.
 * @author Dominic Scheurer
 */
public record SymbolicExecutionStateWithProgCnt(JTerm symbolicState, JTerm pathCondition,
        JTerm programCounter, @Nullable Node correspondingNode) {
    /**
     * @return The symbolic state.
     */
    public JTerm getSymbolicState() { return symbolicState; }

    /**
     * @return The path condition.
     */
    public JTerm getPathCondition() { return pathCondition; }

    /**
     * @return The program counter (and post condition).
     */
    public JTerm getProgramCounter() { return programCounter; }

    /**
     * @return The node corresponding to this SE state.
     */
    public Node getCorrespondingNode() { return correspondingNode; }

    /**
     * @return The corresponding SE state (without the program counter).
     */
    public SymbolicExecutionState toSymbolicExecutionState() {
        return new SymbolicExecutionState(symbolicState, pathCondition);
    }

    @Override
    public String toString() {
        final Services services = getCorrespondingNode().proof().getServices();

        return "SymbolicExecutionStateWithProgCnt [Symbolic State=("
            + rmN(LogicPrinter.quickPrintTerm(getSymbolicState(), services)) + "), Path Condition=("
            + rmN(LogicPrinter.quickPrintTerm(getPathCondition(), services))
            + "), Program Counter=("
            + rmN(LogicPrinter.quickPrintTerm(getProgramCounter(), services)) + ")]";
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
