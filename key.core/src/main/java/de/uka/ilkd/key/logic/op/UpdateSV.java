/* This file is part of KeY - https://key-project.org
 * KeY is licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package de.uka.ilkd.key.logic.op;

import de.uka.ilkd.key.ldt.JavaDLTheory;

import org.key_project.logic.Name;
import org.key_project.logic.TerminalSyntaxElement;


/**
 * A schema variable that is used as placeholder for updates.
 */
public final class UpdateSV extends JOperatorSV implements TerminalSyntaxElement {


    UpdateSV(Name name) {
        super(name, JavaDLTheory.UPDATE, false, true);
    }


    @Override
    public String toString() {
        return toString("update");
    }

}
