/* This file is part of KeY - https://key-project.org
 * KeY is licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package de.uka.ilkd.key.speclang;

import java.util.function.UnaryOperator;

import de.uka.ilkd.key.java.Services;
import de.uka.ilkd.key.java.abstraction.KeYJavaType;
import de.uka.ilkd.key.logic.JTerm;
import de.uka.ilkd.key.logic.TermServices;
import de.uka.ilkd.key.logic.op.LocationVariable;
import de.uka.ilkd.key.speclang.njml.LabeledParserRuleContext;


public interface InitiallyClause extends SpecificationElement {

    @Override
    InitiallyClause map(UnaryOperator<JTerm> op, Services services);

    /**
     * Returns the formula without implicit all-quantification over the receiver object.
     */
    JTerm getClause(LocationVariable selfVar, TermServices services);

    LabeledParserRuleContext getOriginalSpec();

    InitiallyClause setKJT(KeYJavaType newKjt);


}
