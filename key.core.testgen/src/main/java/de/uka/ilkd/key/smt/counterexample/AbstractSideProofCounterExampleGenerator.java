/* This file is part of KeY - https://key-project.org
 * KeY is licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package de.uka.ilkd.key.smt.counterexample;

import de.uka.ilkd.key.control.UserInterfaceControl;
import de.uka.ilkd.key.proof.Proof;
import de.uka.ilkd.key.proof.init.ProofInputException;
import de.uka.ilkd.key.proof.mgt.ProofEnvironment;
import de.uka.ilkd.key.rule.OneStepSimplifier;
import de.uka.ilkd.key.util.ProofStarter;
import de.uka.ilkd.key.util.SideProofUtil;

import org.key_project.logic.Choice;
import org.key_project.prover.sequent.Sequent;

/**
 * Implementation of {@link AbstractCounterExampleGenerator} which instantiates the new
 * {@link Proof} as side proof.
 */
public abstract class AbstractSideProofCounterExampleGenerator
        extends AbstractCounterExampleGenerator {
    /**
     * {@inheritDoc}
     */
    @Override
    protected Proof createProof(UserInterfaceControl ui, Proof oldProof, Sequent oldSequent,
            String proofName) throws ProofInputException {
        Sequent newSequent = createNewSequent(oldSequent);
        ProofEnvironment env = SideProofUtil.cloneProofEnvironmentWithOwnOneStepSimplifier(oldProof,
            new Choice("ban", "runtimeExceptions"));
        ProofStarter starter = SideProofUtil.createSideProof(env, newSequent, proofName);
        Proof proof = starter.getProof();
        OneStepSimplifier.refreshOSS(proof);
        return proof;
    }
}
