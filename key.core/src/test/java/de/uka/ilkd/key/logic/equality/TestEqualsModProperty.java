/* This file is part of KeY - https://key-project.org
 * KeY is licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package de.uka.ilkd.key.logic.equality;

import java.util.Arrays;

import de.uka.ilkd.key.java.Comment;
import de.uka.ilkd.key.java.NameAbstractionTable;
import de.uka.ilkd.key.java.ProgramElement;
import de.uka.ilkd.key.java.expression.literal.StringLiteral;
import de.uka.ilkd.key.logic.*;
import de.uka.ilkd.key.logic.label.*;
import de.uka.ilkd.key.logic.op.*;
import de.uka.ilkd.key.rule.TacletForTests;
import de.uka.ilkd.key.util.HelperClassForTests;

import org.key_project.util.collection.ImmutableArray;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static de.uka.ilkd.key.logic.equality.IrrelevantTermLabelsProperty.IRRELEVANT_TERM_LABELS_PROPERTY;
import static de.uka.ilkd.key.logic.equality.ProofIrrelevancyProperty.PROOF_IRRELEVANCY_PROPERTY;
import static de.uka.ilkd.key.logic.equality.RenamingSourceElementProperty.RENAMING_SOURCE_ELEMENT_PROPERTY;
import static de.uka.ilkd.key.logic.equality.RenamingTermProperty.RENAMING_TERM_PROPERTY;
import static de.uka.ilkd.key.logic.equality.TermLabelsProperty.TERM_LABELS_PROPERTY;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link EqualsModProperty}.
 *
 * @author Tobias Reinhold
 */
public class TestEqualsModProperty {
    private TermBuilder tb;

    private TermFactory tf;

    final private TermLabel relevantLabel1 = ParameterlessTermLabel.UNDEFINED_VALUE_LABEL;
    final private TermLabel relevantLabel2 = ParameterlessTermLabel.SHORTCUT_EVALUATION_LABEL;
    private static TermLabel irrelevantLabel = null;
    final private static OriginTermLabelFactory factory = new OriginTermLabelFactory();

    @BeforeAll
    public static void setIrrelevantLabel() {
        try {
            irrelevantLabel = factory.parseInstance(Arrays.stream(new String[] {
                "User_Interaction @ node 0 (Test Test)", "[]" }).toList(),
                HelperClassForTests.createServices());
        } catch (TermLabelException e) {
            fail(e);
        }
    }

    @BeforeEach
    public void setUp() {
        tb = TacletForTests.services().getTermBuilder();
        tf = TacletForTests.services().getTermFactory();
    }

    // equalsModProperty(...) with RENAMING_TERM_PROPERTY
    @Test
    public void renaming() {
        // ------------ differing terms to begin with
        JTerm term1 =
            tf.createTerm(Junctor.AND, tf.createTerm(Junctor.TRUE), tf.createTerm(Junctor.FALSE));
        JTerm term2 =
            tf.createTerm(Junctor.AND, tf.createTerm(Junctor.TRUE), tf.createTerm(Junctor.TRUE));
        assertFalse(RENAMING_TERM_PROPERTY.equalsModThisProperty(term1, term2),
            "Terms are different to begin with, so they shouldn't be equal");
        assertFalse(RENAMING_TERM_PROPERTY.equalsModThisProperty(term2, term1),
            "Terms are different to begin with, so they shouldn't be equal");
        // other tests for equality already in TestTerm.java

        // ------------ differing labels
        term1 =
            tf.createTerm(Junctor.AND, tf.createTerm(Junctor.TRUE), tf.createTerm(Junctor.FALSE));
        term2 =
            tf.createTerm(Junctor.AND, tf.createTerm(Junctor.TRUE), tf.createTerm(Junctor.FALSE));
        ImmutableArray<TermLabel> labels1 = new ImmutableArray<>(irrelevantLabel);
        term1 = tb.label(term1, labels1);
        assertTrue(RENAMING_TERM_PROPERTY.equalsModThisProperty(term1, term2),
            "Should be true as labels do not matter");
        assertTrue(RENAMING_TERM_PROPERTY.equalsModThisProperty(term2, term1),
            "Should be true as labels do not matter");
        assertEquals(term1.hashCodeModProperty(RENAMING_TERM_PROPERTY),
            term2.hashCodeModProperty(RENAMING_TERM_PROPERTY),
            "Hash codes should be equal as labels do not matter (0)");


        labels1 = new ImmutableArray<>(relevantLabel1);
        term1 = tb.label(term1, labels1);
        assertTrue(RENAMING_TERM_PROPERTY.equalsModThisProperty(term1, term2),
            "Should be true as labels do not matter");
        assertTrue(RENAMING_TERM_PROPERTY.equalsModThisProperty(term2, term1),
            "Should be true as labels do not matter");
        assertEquals(term1.hashCodeModProperty(RENAMING_TERM_PROPERTY),
            term2.hashCodeModProperty(RENAMING_TERM_PROPERTY),
            "Hash codes should be equal as labels do not matter (1)");

        ImmutableArray<TermLabel> labels2 = new ImmutableArray<>(relevantLabel2);
        term2 = tb.label(term2, labels2);
        assertTrue(RENAMING_TERM_PROPERTY.equalsModThisProperty(term1, term2),
            "Should be true as labels do not matter");
        assertTrue(RENAMING_TERM_PROPERTY.equalsModThisProperty(term2, term1),
            "Should be true as labels do not matter");
        assertEquals(term1.hashCodeModProperty(RENAMING_TERM_PROPERTY),
            term2.hashCodeModProperty(RENAMING_TERM_PROPERTY),
            "Hash codes should be equal as labels do not matter (2)");
    }

    // equalsModProperty(...) with IRRELEVANT_TERM_LABELS_PROPERTY
    @Test
    public void irrelevantTermLabels() {
        // ------------ different terms to begin with
        JTerm term1 =
            tf.createTerm(Junctor.AND, tf.createTerm(Junctor.TRUE), tf.createTerm(Junctor.FALSE));
        JTerm term2 =
            tf.createTerm(Junctor.AND, tf.createTerm(Junctor.TRUE), tf.createTerm(Junctor.TRUE));
        assertFalse(term1.equalsModProperty(term2, IRRELEVANT_TERM_LABELS_PROPERTY),
            "Terms are different to begin with, so they shouldn't be equal");
        assertFalse(term2.equalsModProperty(term1, IRRELEVANT_TERM_LABELS_PROPERTY),
            "Terms are different to begin with, so they shouldn't be equal");

        // ------------ comparison with something that is not a term
        assertFalse(term1.equalsModProperty(1, IRRELEVANT_TERM_LABELS_PROPERTY),
            "Should be false as other object is not a term");

        // base terms stay the same for the rest of the tests
        term1 =
            tf.createTerm(Junctor.AND, tf.createTerm(Junctor.TRUE), tf.createTerm(Junctor.FALSE));
        term2 =
            tf.createTerm(Junctor.AND, tf.createTerm(Junctor.TRUE), tf.createTerm(Junctor.FALSE));

        // ------------ only one term has labels
        ImmutableArray<TermLabel> labels1 =
            new ImmutableArray<>(relevantLabel1, irrelevantLabel);
        term1 = tb.label(term1, labels1);
        assertFalse(term1.equalsModProperty(term2, IRRELEVANT_TERM_LABELS_PROPERTY),
            "Should be false as term1 has a proof relevant term label, but term2 does not have any labels");
        assertFalse(term2.equalsModProperty(term1, IRRELEVANT_TERM_LABELS_PROPERTY),
            "Should be false as term1 has a proof relevant term label, but term2 does not have any labels");

        labels1 = new ImmutableArray<>(irrelevantLabel);
        term1 = tb.label(term1, labels1);
        assertTrue(term1.equalsModProperty(term2, IRRELEVANT_TERM_LABELS_PROPERTY),
            "Should be true as term1 has no relevant term labels and term2 does not have any labels");
        assertTrue(term2.equalsModProperty(term1, IRRELEVANT_TERM_LABELS_PROPERTY),
            "Should be true as term1 has no relevant term labels and term2 does not have any labels");
        assertEquals(term1.hashCodeModProperty(IRRELEVANT_TERM_LABELS_PROPERTY),
            term2.hashCodeModProperty(IRRELEVANT_TERM_LABELS_PROPERTY),
            "Hash codes should be equal as term1 has no relevant term labels and term2 does not have any labels (0)");

        // ------------ same relevant labels
        labels1 = new ImmutableArray<>(relevantLabel1, relevantLabel2);
        ImmutableArray<TermLabel> labels2 =
            new ImmutableArray<>(relevantLabel1, relevantLabel2, irrelevantLabel);
        term1 = tb.label(term1, labels1);
        term2 = tb.label(term2, labels2);
        assertTrue(term1.equalsModProperty(term2, IRRELEVANT_TERM_LABELS_PROPERTY),
            "Should be true as both terms have the same relevant term labels");
        assertTrue(term2.equalsModProperty(term1, IRRELEVANT_TERM_LABELS_PROPERTY),
            "Should be true as both terms have the same relevant term labels");
        assertEquals(term1.hashCodeModProperty(IRRELEVANT_TERM_LABELS_PROPERTY),
            term2.hashCodeModProperty(IRRELEVANT_TERM_LABELS_PROPERTY),
            "Hash codes should be equal as both terms have the same relevant term labels (1)");

        // ------------ not the same relevant labels
        labels1 = new ImmutableArray<>(relevantLabel1, irrelevantLabel);
        labels2 = new ImmutableArray<>(relevantLabel1, relevantLabel2);
        term1 = tb.label(term1, labels1);
        term2 = tb.label(term2, labels2);
        assertFalse(term1.equalsModProperty(term2, IRRELEVANT_TERM_LABELS_PROPERTY),
            "Should be false as terms do not have the same relevant term labels");
        assertFalse(term2.equalsModProperty(term1, IRRELEVANT_TERM_LABELS_PROPERTY),
            "Should be false as terms do not have the same relevant term labels");
    }

    // equalsModProperty(...) with TERM_LABELS_PROPERTY
    @Test
    public void allTermLabels() {
        // ------------ different terms to begin with
        JTerm term1 =
            tf.createTerm(Junctor.AND, tf.createTerm(Junctor.TRUE), tf.createTerm(Junctor.FALSE));
        JTerm term2 =
            tf.createTerm(Junctor.AND, tf.createTerm(Junctor.TRUE), tf.createTerm(Junctor.TRUE));
        assertFalse(TERM_LABELS_PROPERTY.equalsModThisProperty(term1, term2),
            "Terms are different to begin with, so they shouldn't be equal");
        assertFalse(TERM_LABELS_PROPERTY.equalsModThisProperty(term2, term1),
            "Terms are different to begin with, so they shouldn't be equal");

        // base terms stay the same for the rest of the tests
        term1 =
            tf.createTerm(Junctor.AND, tf.createTerm(Junctor.TRUE), tf.createTerm(Junctor.FALSE));
        term2 =
            tf.createTerm(Junctor.AND, tf.createTerm(Junctor.TRUE), tf.createTerm(Junctor.FALSE));

        // ------------ only one term has labels
        ImmutableArray<TermLabel> labels1 =
            new ImmutableArray<>(relevantLabel1, irrelevantLabel);
        term1 = tb.label(term1, labels1);
        assertTrue(TERM_LABELS_PROPERTY.equalsModThisProperty(term1, term2),
            "Should be true as underlying terms are equal");
        assertTrue(TERM_LABELS_PROPERTY.equalsModThisProperty(term2, term1),
            "Should be true as underlying terms are equal");
        assertEquals(term1.hashCodeModProperty(TERM_LABELS_PROPERTY),
            term2.hashCodeModProperty(TERM_LABELS_PROPERTY),
            "Hash codes should be equal as all term labels are ignored (0)");

        // ------------ same relevant labels
        labels1 = new ImmutableArray<>(relevantLabel1, relevantLabel2);
        ImmutableArray<TermLabel> labels2 =
            new ImmutableArray<>(relevantLabel1, relevantLabel2, irrelevantLabel);
        term1 = tb.label(term1, labels1);
        term2 = tb.label(term2, labels2);
        assertTrue(TERM_LABELS_PROPERTY.equalsModThisProperty(term1, term2),
            "Should be true as underlying terms are equal");
        assertTrue(TERM_LABELS_PROPERTY.equalsModThisProperty(term2, term1),
            "Should be true as underlying terms are equal");
        assertEquals(term1.hashCodeModProperty(TERM_LABELS_PROPERTY),
            term2.hashCodeModProperty(TERM_LABELS_PROPERTY),
            "Hash codes should be equal as all term labels are ignored (1)");

        // ------------ not the same relevant labels
        labels1 = new ImmutableArray<>(relevantLabel1, irrelevantLabel);
        labels2 = new ImmutableArray<>(relevantLabel1, relevantLabel2);
        term1 = tb.label(term1, labels1);
        term2 = tb.label(term2, labels2);
        assertTrue(TERM_LABELS_PROPERTY.equalsModThisProperty(term1, term2),
            "Should be true as underlying terms are equal");
        assertTrue(TERM_LABELS_PROPERTY.equalsModThisProperty(term2, term1),
            "Should be true as underlying terms are equal");
        assertEquals(term1.hashCodeModProperty(TERM_LABELS_PROPERTY),
            term2.hashCodeModProperty(TERM_LABELS_PROPERTY),
            "Hash codes should be equal as all term labels are ignored (2)");
    }

    // equalsModProperty(...) with PROOF_IRRELEVANCY_PROPERTY
    @Test
    public void proofIrrelevancy() {
        // ------------ different terms to begin with
        JTerm term1 =
            tf.createTerm(Junctor.AND, tf.createTerm(Junctor.TRUE), tf.createTerm(Junctor.FALSE));
        JTerm term2 =
            tf.createTerm(Junctor.AND, tf.createTerm(Junctor.TRUE), tf.createTerm(Junctor.TRUE));
        assertFalse(term1.equalsModProperty(term2, PROOF_IRRELEVANCY_PROPERTY),
            "Terms are different to begin with, so they shouldn't be equal");
        assertFalse(term2.equalsModProperty(term1, PROOF_IRRELEVANCY_PROPERTY),
            "Terms are different to begin with, so they shouldn't be equal");

        // ------------ comparison with something that is not a term
        assertFalse(term1.equalsModProperty(1, PROOF_IRRELEVANCY_PROPERTY),
            "Should be false as other object is not a term");

        // base terms stay the same for the rest of the tests
        term1 =
            tf.createTerm(Junctor.AND, tf.createTerm(Junctor.TRUE), tf.createTerm(Junctor.FALSE));
        term2 =
            tf.createTerm(Junctor.AND, tf.createTerm(Junctor.TRUE), tf.createTerm(Junctor.FALSE));

        // ------------ only one term has labels
        ImmutableArray<TermLabel> labels1 =
            new ImmutableArray<>(relevantLabel1, irrelevantLabel);
        term1 = tb.label(term1, labels1);
        assertFalse(term1.equalsModProperty(term2, PROOF_IRRELEVANCY_PROPERTY),
            "Should be false as term1 has a proof relevant term label, but term2 does not have any labels");
        assertFalse(term2.equalsModProperty(term1, PROOF_IRRELEVANCY_PROPERTY),
            "Should be false as term1 has a proof relevant term label, but term2 does not have any labels");

        labels1 = new ImmutableArray<>(irrelevantLabel);
        term1 = tb.label(term1, labels1);
        assertTrue(term1.equalsModProperty(term2, PROOF_IRRELEVANCY_PROPERTY),
            "Should be true as term1 has no relevant term labels and term2 does not have any labels");
        assertTrue(term2.equalsModProperty(term1, PROOF_IRRELEVANCY_PROPERTY),
            "Should be true as term1 has no relevant term labels and term2 does not have any labels");
        assertEquals(term1.hashCodeModProperty(PROOF_IRRELEVANCY_PROPERTY),
            term2.hashCodeModProperty(PROOF_IRRELEVANCY_PROPERTY),
            "Hash codes should be equal as proof irrelevant properties are ignored (0)");

        // ------------ same relevant labels
        labels1 = new ImmutableArray<>(relevantLabel1, relevantLabel2, irrelevantLabel);
        ImmutableArray<TermLabel> labels2 =
            new ImmutableArray<>(relevantLabel1, relevantLabel2, irrelevantLabel);
        term1 = tb.label(term1, labels1);
        term2 = tb.label(term2, labels2);
        assertTrue(term1.equalsModProperty(term2, PROOF_IRRELEVANCY_PROPERTY),
            "Should be true as both terms have the same relevant term labels");
        assertTrue(term2.equalsModProperty(term1, PROOF_IRRELEVANCY_PROPERTY),
            "Should be true as both terms have the same relevant term labels");
        assertEquals(term1.hashCodeModProperty(PROOF_IRRELEVANCY_PROPERTY),
            term2.hashCodeModProperty(PROOF_IRRELEVANCY_PROPERTY),
            "Hash codes should be equal as proof irrelevant properties are ignored (1)");

        labels1 = new ImmutableArray<>(relevantLabel1, relevantLabel2, irrelevantLabel);
        labels2 = new ImmutableArray<>(relevantLabel1, relevantLabel2);
        term1 = tb.label(term1, labels1);
        term2 = tb.label(term2, labels2);
        assertTrue(term1.equalsModProperty(term2, PROOF_IRRELEVANCY_PROPERTY),
            "Should be true as both terms have the same relevant term labels and irrelevant labels do not matter");
        assertTrue(term2.equalsModProperty(term1, PROOF_IRRELEVANCY_PROPERTY),
            "Should be true as both terms have the same relevant term labels and irrelevant labels do not matter");
        assertEquals(term1.hashCodeModProperty(PROOF_IRRELEVANCY_PROPERTY),
            term2.hashCodeModProperty(PROOF_IRRELEVANCY_PROPERTY),
            "Hash codes should be equal as proof irrelevant properties are ignored (2)");

        // ------------ not the same relevant labels
        labels1 = new ImmutableArray<>(relevantLabel1);
        labels2 = new ImmutableArray<>(relevantLabel2);
        term1 = tb.label(term1, labels1);
        term2 = tb.label(term2, labels2);
        assertFalse(term1.equalsModProperty(term2, PROOF_IRRELEVANCY_PROPERTY),
            "Should be false as terms do not have the same relevant term labels");
        assertFalse(term2.equalsModProperty(term1, PROOF_IRRELEVANCY_PROPERTY),
            "Should be false as terms do not have the same relevant term labels");
    }

    @Test
    public void renamingSourceElements() {
        ProgramElement match1 = TacletForTests.parsePrg("{ int i; int j; /*Test*/ }");
        ProgramElement match2 = TacletForTests.parsePrg("{ int i; /*Another test*/ int k; }");
        assertTrue(
            match1.equalsModProperty(match2, RENAMING_SOURCE_ELEMENT_PROPERTY,
                new NameAbstractionTable()),
            "ProgramElements should be equal modulo renaming (0).");
        assertEquals(match1.hashCodeModProperty(RENAMING_SOURCE_ELEMENT_PROPERTY),
            match2.hashCodeModProperty(RENAMING_SOURCE_ELEMENT_PROPERTY),
            "Hash codes should be equal as ProgramElements are equal modulo renaming (0).");


        Comment testComment = new Comment("test");
        StringLiteral stringLiteral = new StringLiteral("testStringLiteral");

        assertFalse(testComment.equalsModProperty(stringLiteral, RENAMING_SOURCE_ELEMENT_PROPERTY,
            new NameAbstractionTable()));
        assertFalse(stringLiteral.equalsModProperty(testComment, RENAMING_SOURCE_ELEMENT_PROPERTY,
            new NameAbstractionTable()));
    }
}
