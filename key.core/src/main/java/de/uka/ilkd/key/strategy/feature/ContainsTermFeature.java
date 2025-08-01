/* This file is part of KeY - https://key-project.org
 * KeY is licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package de.uka.ilkd.key.strategy.feature;

import de.uka.ilkd.key.proof.Goal;

import org.key_project.logic.Term;
import org.key_project.logic.Visitor;
import org.key_project.prover.proof.ProofGoal;
import org.key_project.prover.rules.RuleApp;
import org.key_project.prover.sequent.PosInOccurrence;
import org.key_project.prover.strategy.costbased.MutableState;
import org.key_project.prover.strategy.costbased.NumberRuleAppCost;
import org.key_project.prover.strategy.costbased.RuleAppCost;
import org.key_project.prover.strategy.costbased.TopRuleAppCost;
import org.key_project.prover.strategy.costbased.feature.Feature;
import org.key_project.prover.strategy.costbased.termProjection.ProjectionToTerm;

import org.jspecify.annotations.NonNull;

import static de.uka.ilkd.key.logic.equality.RenamingTermProperty.RENAMING_TERM_PROPERTY;


/**
 * Feature for checking if the term of the first projection contains the term of the second
 * projection.
 */
public class ContainsTermFeature implements Feature {

    /** Constant that represents the boolean value true */
    public static final RuleAppCost ZERO_COST = NumberRuleAppCost.getZeroCost();

    /** Constant that represents the boolean value false */
    public static final RuleAppCost TOP_COST = TopRuleAppCost.INSTANCE;

    private final ProjectionToTerm<Goal> proj1;

    private final ProjectionToTerm<Goal> proj2;


    /**
     * checks whether the second term is a subterm of the first one
     *
     * @param proj1 the ProjectionToTerm resolving to the term in which to search for the second
     *        term
     * @param proj2 the ProjectionToTerm resolving to the term to be checked whether it is a subterm
     *        of the first one
     */
    private ContainsTermFeature(ProjectionToTerm<Goal> proj1, ProjectionToTerm<Goal> proj2) {
        this.proj1 = proj1;
        this.proj2 = proj2;
    }


    public static Feature create(ProjectionToTerm<Goal> proj1, ProjectionToTerm<Goal> proj2) {
        return new ContainsTermFeature(proj1, proj2);
    }


    @Override
    public <G extends ProofGoal<@NonNull G>> RuleAppCost computeCost(RuleApp app,
            PosInOccurrence pos, G goal,
            MutableState mState) {
        final Term t1 = proj1.toTerm(app, pos, (Goal) goal, mState);
        final Term t2 = proj2.toTerm(app, pos, (Goal) goal, mState);
        ContainsTermVisitor visitor = new ContainsTermVisitor(t2);
        t1.execPreOrder(visitor);
        if (visitor.found) {
            return ZERO_COST;
        } else {
            return TOP_COST;
        }
    }


    private static class ContainsTermVisitor implements Visitor<@NonNull Term> {
        boolean found = false;
        final Term term;


        public ContainsTermVisitor(Term term) {
            this.term = term;
        }

        @Override
        public boolean visitSubtree(Term visited) {
            return true;
        }

        @Override
        public void visit(Term visited) {
            found = found || RENAMING_TERM_PROPERTY.equalsModThisProperty(visited, term);
        }

        @Override
        public void subtreeEntered(Term subtreeRoot) {
            // nothing to do
        }

        @Override
        public void subtreeLeft(Term subtreeRoot) {
            // nothing to do
        }
    }
}
